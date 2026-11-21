import bdb
import builtins
import json
import linecache
import queue
import sys
import threading
import time

ARCHIVO = "<redact>"
LIMITE_SALIDA = 200_000
MAXIMO_VARIABLES = 60
LARGO_VALOR = 200

_sesion = None
_cerrojo = threading.Lock()


class Cancelada(BaseException):
    pass


class TiempoAgotado(BaseException):
    pass


def _texto_corto(valor):
    try:
        texto = repr(valor)
    except BaseException as error:
        texto = "<error al mostrar: " + type(error).__name__ + ">"
    if len(texto) > LARGO_VALOR:
        texto = texto[:LARGO_VALOR] + "…"
    return texto


def _variables(marco):
    resultado = []
    for nombre, valor in list(marco.f_locals.items()):
        if nombre.startswith("__"):
            continue
        if isinstance(valor, type(sys)):
            continue
        resultado.append({"nombre": nombre, "valor": _texto_corto(valor), "tipo": type(valor).__name__})
        if len(resultado) >= MAXIMO_VARIABLES:
            break
    return resultado


def _pila(marco):
    marcos = []
    while marco is not None:
        if marco.f_code.co_filename == ARCHIVO:
            marcos.append({"funcion": marco.f_code.co_name, "linea": marco.f_lineno})
        marco = marco.f_back
    return marcos


class Flujo:
    def __init__(self, sesion, es_error):
        self.sesion = sesion
        self.es_error = es_error

    def write(self, texto):
        texto = str(texto)
        self.sesion.emitir_salida(texto, self.es_error)
        return len(texto)

    def flush(self):
        pass

    def isatty(self):
        return False


class Depurador(bdb.Bdb):
    def __init__(self, sesion):
        super().__init__()
        self.sesion = sesion
        self.avanzando = False

    def stop_here(self, marco):
        if not self.avanzando or marco.f_code.co_filename != ARCHIVO:
            return False
        return super().stop_here(marco)

    def trace_dispatch(self, marco, evento, argumento):
        self.sesion.comprobar()
        return super().trace_dispatch(marco, evento, argumento)

    def set_continue(self):
        self._set_stopinfo(self.botframe, None, -1)

    def user_line(self, marco):
        self.sesion.pausar(self, marco)


class Sesion:
    def __init__(self, codigo, depurar, puntos, limite_segundos):
        self.codigo = codigo
        self.depurar = depurar
        self.puntos = set(puntos)
        self.limite = limite_segundos
        self.eventos = queue.Queue()
        self.entrada = queue.Queue()
        self.comandos = queue.Queue()
        self.cancelada = False
        self.espera = 0.0
        self.esperando_desde = None
        self.inicio = time.monotonic()
        self.emitidos = 0
        self.hilo = None
        self.terminada = False

    def emitir(self, evento):
        self.eventos.put(json.dumps(evento, ensure_ascii=False))

    def emitir_salida(self, texto, es_error):
        if self.emitidos >= LIMITE_SALIDA:
            return
        resto = LIMITE_SALIDA - self.emitidos
        self.emitidos += min(len(texto), resto)
        self.emitir({"tipo": "salida", "texto": texto[:resto], "error": es_error})
        if len(texto) > resto:
            self.emitir({"tipo": "salida", "texto": "\n[Salida recortada: se superó el límite de 200 000 caracteres]\n", "error": True})

    def esperar(self, cola):
        inicio = time.monotonic()
        self.esperando_desde = inicio
        try:
            while True:
                try:
                    return cola.get(timeout=0.1)
                except queue.Empty:
                    if self.cancelada:
                        raise Cancelada()
        finally:
            self.espera += time.monotonic() - inicio
            self.esperando_desde = None

    def leer(self, mensaje=""):
        if mensaje:
            self.emitir_salida(str(mensaje), False)
        self.emitir({"tipo": "entrada"})
        valor = self.esperar(self.entrada)
        if valor is None:
            raise EOFError("Se cerró la entrada")
        self.emitir_salida(valor + "\n", False)
        return valor

    def pausar(self, depurador, marco):
        self.emitir({"tipo": "pausa", "linea": marco.f_lineno, "pila": _pila(marco), "variables": _variables(marco)})
        while True:
            comando, argumento = self.esperar(self.comandos)
            if comando == "continuar":
                depurador.avanzando = False
                depurador.set_continue()
                return
            if comando == "paso":
                depurador.avanzando = True
                depurador.set_next(marco)
                return
            if comando == "entrar":
                depurador.avanzando = True
                depurador.set_step()
                return
            if comando == "salir":
                depurador.avanzando = True
                depurador.set_return(marco)
                return
            if comando == "detener":
                raise Cancelada()
            if comando == "evaluar":
                try:
                    valor = eval(compile(argumento, "<evaluar>", "eval"), marco.f_globals, marco.f_locals)
                    self.emitir({"tipo": "evaluacion", "texto": _texto_corto(valor), "error": False})
                except BaseException as error:
                    self.emitir({"tipo": "evaluacion", "texto": type(error).__name__ + ": " + str(error), "error": True})

    def comprobar(self):
        if self.cancelada:
            raise Cancelada()
        ahora = time.monotonic()
        if (ahora - self.inicio) - self.espera_total(ahora) > self.limite:
            raise TiempoAgotado()

    def rastreo(self, marco, evento, argumento):
        self.comprobar()
        return self.rastreo

    def espera_total(self, ahora):
        desde = self.esperando_desde
        return self.espera + (ahora - desde if desde is not None else 0.0)

    def correr(self):
        salida, error = Flujo(self, False), Flujo(self, True)
        anterior = sys.stdout, sys.stderr
        estado, mensaje, linea = "EXITO", None, None
        try:
            try:
                compilado = compile(self.codigo, ARCHIVO, "exec", dont_inherit=True)
            except SyntaxError as e:
                self.emitir({"tipo": "fin", "estado": "ERROR", "mensaje": "SyntaxError: " + str(e.msg), "linea": e.lineno})
                return
            incorporados = dict(builtins.__dict__)
            incorporados["input"] = self.leer
            espacio = {"__name__": "__main__", "__builtins__": incorporados}
            sys.stdout, sys.stderr = salida, error
            if self.depurar:
                linecache.cache[ARCHIVO] = (len(self.codigo), None, self.codigo.splitlines(True), ARCHIVO)
                bdb.Breakpoint.clearBreakpoints()
                depurador = Depurador(self)
                for numero in self.puntos:
                    depurador.set_break(ARCHIVO, numero)
                depurador.run(compilado, espacio)
            else:
                sys.settrace(self.rastreo)
                exec(compilado, espacio)
        except Cancelada:
            estado, mensaje = "CANCELADO", "Ejecución detenida por el usuario"
        except bdb.BdbQuit:
            estado, mensaje = "CANCELADO", "Ejecución detenida por el usuario"
        except TiempoAgotado:
            estado, mensaje = "TIEMPO_AGOTADO", "La ejecución superó " + str(self.limite) + " segundos (sin contar las esperas) y fue detenida"
        except SystemExit:
            pass
        except BaseException as e:
            estado = "ERROR"
            mensaje = type(e).__name__ + ": " + str(e)
            tb = e.__traceback__
            while tb is not None:
                if tb.tb_frame.f_code.co_filename == ARCHIVO:
                    linea = tb.tb_lineno
                tb = tb.tb_next
        finally:
            sys.settrace(None)
            sys.stdout, sys.stderr = anterior
            bdb.Breakpoint.clearBreakpoints()
            self.terminada = True
        self.emitir({"tipo": "fin", "estado": estado, "mensaje": mensaje, "linea": linea, "duracion_ms": int((time.monotonic() - self.inicio - self.espera_total(time.monotonic())) * 1000)})


def iniciar(codigo, depurar, puntos_json, limite_segundos):
    global _sesion
    with _cerrojo:
        if _sesion is not None and not _sesion.terminada:
            return "ocupada"
        sesion = Sesion(codigo, bool(depurar), json.loads(puntos_json or "[]"), limite_segundos)
        _sesion = sesion
        sesion.hilo = threading.Thread(target=sesion.correr, name="redact-sesion", daemon=True)
        sesion.hilo.start()
        return "ok"


def siguiente_evento(timeout_ms):
    sesion = _sesion
    if sesion is None:
        return ""
    try:
        return sesion.eventos.get(timeout=timeout_ms / 1000.0)
    except queue.Empty:
        return ""


def enviar_entrada(texto):
    if _sesion is not None:
        _sesion.entrada.put(texto)


def comando_depurador(nombre, argumento=""):
    if _sesion is not None:
        _sesion.comandos.put((nombre, argumento))


def detener():
    if _sesion is not None:
        _sesion.cancelada = True


def activa():
    return _sesion is not None and not _sesion.terminada
