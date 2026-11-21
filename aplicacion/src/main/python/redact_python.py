import ast
import builtins
import io
import json
import math
import symtable
import sys
import threading
import time
import traceback
import warnings

ARCHIVO_USUARIO = "<redact>"
LIMITE_SALIDA = 200_000
NOMBRES_INCORPORADOS = {
    "list", "dict", "str", "int", "float", "input", "print", "sum", "max", "min", "len", "type",
    "id", "set", "tuple", "range", "open", "sorted", "map", "filter", "bool", "abs", "round", "format",
}

_cerrojo_ejecucion = threading.Lock()
_cancelado = False


class TiempoAgotado(BaseException):
    pass


class EjecucionCancelada(BaseException):
    pass


class SalidaLimitada(io.TextIOBase):
    def __init__(self):
        self.partes = []
        self.longitud = 0
        self.truncada = False

    def writable(self):
        return True

    def write(self, texto):
        texto = str(texto)
        if self.longitud >= LIMITE_SALIDA:
            self.truncada = True
            return len(texto)
        restante = LIMITE_SALIDA - self.longitud
        self.partes.append(texto[:restante])
        self.longitud += min(len(texto), restante)
        if len(texto) > restante:
            self.truncada = True
        return len(texto)

    def texto(self):
        contenido = "".join(self.partes)
        if self.truncada:
            contenido += "\n[Salida recortada: se superó el límite de 200 000 caracteres]"
        return contenido


def validar(codigo):
    with warnings.catch_warnings(record=True) as avisos:
        warnings.simplefilter("always")
        try:
            compile(codigo, ARCHIVO_USUARIO, "exec", dont_inherit=True)
        except SyntaxError as error:
            return json.dumps([{
                "tipo": type(error).__name__,
                "mensaje": error.msg or "invalid syntax",
                "linea": error.lineno or 1,
                "columna": error.offset or 1,
                "columna_fin": getattr(error, "end_offset", None),
                "severidad": "ERROR",
            }], ensure_ascii=False)
        except ValueError as error:
            return json.dumps([{"tipo": "ValueError", "mensaje": str(error), "linea": 1, "columna": 1, "severidad": "ERROR"}], ensure_ascii=False)
    resultado = []
    for aviso in avisos:
        if issubclass(aviso.category, SyntaxWarning):
            resultado.append({
                "tipo": "SyntaxWarning",
                "mensaje": str(aviso.message),
                "linea": aviso.lineno or 1,
                "columna": 1,
                "severidad": "ADVERTENCIA",
            })
    return json.dumps(resultado, ensure_ascii=False)


def cancelar():
    global _cancelado
    _cancelado = True


def _linea_usuario(rastro):
    linea = None
    for marco in traceback.extract_tb(rastro):
        if marco.filename == ARCHIVO_USUARIO:
            linea = marco.lineno
    return linea


def _crear_espacio(salida, entrada):
    lineas = iter(entrada.splitlines())

    def leer(mensaje=""):
        salida.write(str(mensaje))
        try:
            valor = next(lineas)
        except StopIteration:
            raise EOFError("No hay más datos de entrada. Escríbelos en el panel Entrada antes de ejecutar.")
        salida.write(valor + "\n")
        return valor

    incorporados = dict(builtins.__dict__)
    incorporados["input"] = leer
    return {"__name__": "__main__", "__builtins__": incorporados}


def _ejecutar_con_limite(funcion, limite_segundos):
    global _cancelado
    _cancelado = False
    fin = time.monotonic() + limite_segundos

    def rastreo(marco, evento, argumento):
        if _cancelado:
            raise EjecucionCancelada()
        if time.monotonic() > fin:
            raise TiempoAgotado()
        return rastreo

    anterior_salida, anterior_error = sys.stdout, sys.stderr
    sys.settrace(rastreo)
    try:
        return funcion()
    finally:
        sys.settrace(None)
        sys.stdout, sys.stderr = anterior_salida, anterior_error


def ejecutar(codigo, entrada, limite_segundos):
    with _cerrojo_ejecucion:
        salida = SalidaLimitada()
        errores = SalidaLimitada()
        inicio = time.monotonic()
        respuesta = {"estado": "EXITO", "excepcion": None, "linea": None, "mensaje": None}
        try:
            compilado = compile(codigo, ARCHIVO_USUARIO, "exec", dont_inherit=True)
        except SyntaxError as error:
            return json.dumps({
                "estado": "ERROR", "salida": "", "errores": f"{type(error).__name__}: {error.msg}",
                "excepcion": type(error).__name__, "linea": error.lineno, "mensaje": error.msg,
                "duracion_ms": 0,
            }, ensure_ascii=False)
        espacio = _crear_espacio(salida, entrada)

        def correr():
            sys.stdout = salida
            sys.stderr = errores
            exec(compilado, espacio)

        try:
            _ejecutar_con_limite(correr, limite_segundos)
        except TiempoAgotado:
            respuesta.update(estado="TIEMPO_AGOTADO", excepcion="TiempoAgotado",
                             mensaje=f"La ejecución superó {limite_segundos} segundos y fue detenida")
        except EjecucionCancelada:
            respuesta.update(estado="CANCELADO", mensaje="Ejecución cancelada por el usuario")
        except SystemExit:
            pass
        except BaseException as error:
            linea = _linea_usuario(error.__traceback__)
            errores.write(f"Traceback (línea {linea}):\n{type(error).__name__}: {error}\n" if linea else f"{type(error).__name__}: {error}\n")
            respuesta.update(estado="ERROR", excepcion=type(error).__name__, linea=linea, mensaje=str(error))
        respuesta["salida"] = salida.texto()
        respuesta["errores"] = errores.texto() if respuesta["estado"] != "TIEMPO_AGOTADO" else (errores.texto() + respuesta["mensaje"])
        respuesta["duracion_ms"] = int((time.monotonic() - inicio) * 1000)
        return json.dumps(respuesta, ensure_ascii=False)


_sesion_consola = None


def reiniciar_consola():
    global _sesion_consola
    _sesion_consola = None
    return "ok"


def consola(codigo, limite_segundos):
    global _sesion_consola
    with _cerrojo_ejecucion:
        salida = SalidaLimitada()
        errores = SalidaLimitada()
        inicio = time.monotonic()
        respuesta = {"estado": "EXITO", "excepcion": None, "linea": None, "mensaje": None}
        if _sesion_consola is None:
            _sesion_consola = _crear_espacio(salida, "")
            _sesion_consola["__name__"] = "__consola__"
        try:
            try:
                compilado = compile(codigo, "<consola>", "eval", dont_inherit=True)
                es_expresion = True
            except SyntaxError:
                compilado = compile(codigo, "<consola>", "exec", dont_inherit=True)
                es_expresion = False
        except SyntaxError as error:
            return json.dumps({
                "estado": "ERROR", "salida": "", "errores": f"{type(error).__name__}: {error.msg}",
                "excepcion": type(error).__name__, "linea": error.lineno, "mensaje": error.msg, "duracion_ms": 0,
            }, ensure_ascii=False)
        espacio = _sesion_consola

        def correr():
            sys.stdout = salida
            sys.stderr = errores
            if es_expresion:
                valor = eval(compilado, espacio)
                if valor is not None:
                    espacio["_"] = valor
                    salida.write(repr(valor) + "\n")
            else:
                exec(compilado, espacio)

        try:
            _ejecutar_con_limite(correr, limite_segundos)
        except TiempoAgotado:
            respuesta.update(estado="TIEMPO_AGOTADO", excepcion="TiempoAgotado", mensaje=f"Superó {limite_segundos} segundos")
        except EjecucionCancelada:
            respuesta.update(estado="CANCELADO", mensaje="Cancelado")
        except SystemExit:
            pass
        except BaseException as error:
            errores.write(f"{type(error).__name__}: {error}\n")
            respuesta.update(estado="ERROR", excepcion=type(error).__name__, mensaje=str(error))
        respuesta["salida"] = salida.texto()
        respuesta["errores"] = errores.texto() if respuesta["estado"] != "TIEMPO_AGOTADO" else (errores.texto() + respuesta["mensaje"])
        respuesta["duracion_ms"] = int((time.monotonic() - inicio) * 1000)
        return json.dumps(respuesta, ensure_ascii=False)


def _serializar(valor):
    def convertir(objeto):
        if isinstance(objeto, (set, frozenset)):
            return sorted(objeto, key=repr)
        if isinstance(objeto, float) and (math.isnan(objeto) or math.isinf(objeto)):
            return str(objeto)
        raise TypeError(f"El tipo {type(objeto).__name__} no se puede comparar con el resultado esperado")
    return json.dumps(valor, default=convertir, ensure_ascii=False)


def evaluar_pruebas(codigo, nombre_funcion, casos_json, limite_segundos):
    with _cerrojo_ejecucion:
        casos = json.loads(casos_json)
        salida = SalidaLimitada()
        try:
            compilado = compile(codigo, ARCHIVO_USUARIO, "exec", dont_inherit=True)
        except SyntaxError as error:
            return json.dumps({"error": f"{type(error).__name__}: {error.msg}", "linea": error.lineno, "salida": []}, ensure_ascii=False)
        espacio = _crear_espacio(salida, "")
        resultados = []

        def correr():
            sys.stdout = salida
            sys.stderr = salida
            exec(compilado, espacio)
            funcion = espacio.get(nombre_funcion)
            if not callable(funcion):
                return {"error": f"No se encontró la función {nombre_funcion}"}
            for entrada in casos:
                try:
                    valor = funcion(*entrada)
                    resultados.append({"ok": True, "valor": _serializar(valor)})
                except (TiempoAgotado, EjecucionCancelada):
                    raise
                except Exception as error:
                    resultados.append({"ok": False, "error": f"{type(error).__name__}: {error}"})
            return None

        try:
            problema = _ejecutar_con_limite(correr, limite_segundos)
            if problema:
                problema["salida"] = salida.texto().splitlines()
                return json.dumps(problema, ensure_ascii=False)
        except TiempoAgotado:
            return json.dumps({"error": "Tiempo agotado: revisa si hay un bucle infinito", "salida": salida.texto().splitlines(), "tiempo_agotado": True}, ensure_ascii=False)
        except EjecucionCancelada:
            return json.dumps({"error": "Evaluación cancelada", "salida": []}, ensure_ascii=False)
        except BaseException as error:
            return json.dumps({"error": f"{type(error).__name__}: {error}", "linea": _linea_usuario(error.__traceback__), "salida": salida.texto().splitlines()}, ensure_ascii=False)
        return json.dumps({"resultados": resultados, "salida": salida.texto().splitlines()}, ensure_ascii=False)


def _hallazgo(regla, nodo, extra=""):
    return {"regla": regla, "linea": getattr(nodo, "lineno", 1), "columna": getattr(nodo, "col_offset", 0) + 1, "extra": extra}


def _nombre_llamada(funcion):
    if isinstance(funcion, ast.Name):
        return funcion.id
    if isinstance(funcion, ast.Attribute):
        base = _nombre_llamada(funcion.value)
        return f"{base}.{funcion.attr}" if base else funcion.attr
    return ""


def _complejidad(funcion):
    total = 1
    for nodo in ast.walk(funcion):
        if nodo is not funcion and isinstance(nodo, (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda)):
            continue
        if isinstance(nodo, (ast.If, ast.For, ast.AsyncFor, ast.While, ast.IfExp, ast.ExceptHandler, ast.With, ast.Assert)):
            total += 1
        elif isinstance(nodo, ast.BoolOp):
            total += len(nodo.values) - 1
        elif isinstance(nodo, ast.comprehension):
            total += 1 + len(nodo.ifs)
        elif hasattr(ast, "match_case") and isinstance(nodo, ast.match_case):
            total += 1
    return total


def _profundidad(nodo, nivel=0):
    maxima = nivel
    bloques = (ast.If, ast.For, ast.AsyncFor, ast.While, ast.Try, ast.With)
    for hijo in ast.iter_child_nodes(nodo):
        if isinstance(hijo, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef, ast.Lambda)):
            continue
        siguiente = nivel + 1 if isinstance(hijo, bloques) else nivel
        maxima = max(maxima, _profundidad(hijo, siguiente))
    return maxima


def _variables_no_usadas(codigo, arbol):
    hallazgos = []
    try:
        tabla = symtable.symtable(codigo, ARCHIVO_USUARIO, "exec")
    except SyntaxError:
        return hallazgos
    funciones = {}
    for nodo in ast.walk(arbol):
        if isinstance(nodo, (ast.FunctionDef, ast.AsyncFunctionDef)):
            funciones.setdefault(nodo.name, []).append(nodo)

    def recorrer(tabla_actual):
        for hija in tabla_actual.get_children():
            if hija.get_type() == "function":
                nodos = funciones.get(hija.get_name(), [])
                for simbolo in hija.get_symbols():
                    nombre = simbolo.get_name()
                    if nombre.startswith("_") or simbolo.is_parameter() or simbolo.is_global() or simbolo.is_nonlocal():
                        continue
                    if simbolo.is_assigned() and not simbolo.is_referenced() and not simbolo.is_imported():
                        linea = None
                        for funcion in nodos:
                            for nodo in ast.walk(funcion):
                                if isinstance(nodo, ast.Name) and nodo.id == nombre and isinstance(nodo.ctx, ast.Store):
                                    linea = nodo
                                    break
                            if linea:
                                break
                        if linea is not None and not _es_variable_de_bucle(nodos, nombre):
                            hallazgos.append(_hallazgo("VARIABLE_NO_USADA", linea, nombre))
            recorrer(hija)

    recorrer(tabla)
    return hallazgos


def _es_variable_de_bucle(funciones, nombre):
    for funcion in funciones:
        for nodo in ast.walk(funcion):
            if isinstance(nodo, (ast.For, ast.comprehension)) and isinstance(nodo.target, ast.Name) and nodo.target.id == nombre:
                return True
    return False


def revisar(codigo):
    try:
        arbol = ast.parse(codigo, ARCHIVO_USUARIO)
    except (SyntaxError, ValueError):
        return json.dumps([])
    hallazgos = []
    usados = set()
    for nodo in ast.walk(arbol):
        if isinstance(nodo, ast.Name) and isinstance(nodo.ctx, ast.Load):
            usados.add(nodo.id)
        elif isinstance(nodo, ast.Attribute):
            base = nodo
            while isinstance(base, ast.Attribute):
                base = base.value
            if isinstance(base, ast.Name):
                usados.add(base.id)
    for nodo in arbol.body:
        if isinstance(nodo, (ast.Import, ast.ImportFrom)):
            for alias in nodo.names:
                nombre = (alias.asname or alias.name).split(".")[0]
                if nombre != "*" and nombre not in usados:
                    hallazgos.append(_hallazgo("PY_IMPORT_NO_USADO", nodo, nombre))
    for nodo in ast.walk(arbol):
        if isinstance(nodo, ast.ExceptHandler):
            if len(nodo.body) == 1 and isinstance(nodo.body[0], ast.Pass):
                hallazgos.append(_hallazgo("PY_EXCEPT_VACIO", nodo))
            elif nodo.type is None:
                hallazgos.append(_hallazgo("PY_EXCEPT_GENERAL", nodo))
        elif isinstance(nodo, ast.Global):
            hallazgos.append(_hallazgo("PY_GLOBAL", nodo))
        elif isinstance(nodo, ast.Compare):
            if any(isinstance(op, (ast.Eq, ast.NotEq)) for op in nodo.ops) and any(
                    isinstance(c, ast.Constant) and c.value is None for c in [nodo.left, *nodo.comparators]):
                hallazgos.append(_hallazgo("PY_COMPARACION_NONE", nodo))
        elif isinstance(nodo, (ast.Assign, ast.AnnAssign)):
            objetivos = nodo.targets if isinstance(nodo, ast.Assign) else [nodo.target]
            for objetivo in objetivos:
                if isinstance(objetivo, ast.Name) and objetivo.id in NOMBRES_INCORPORADOS:
                    hallazgos.append(_hallazgo("PY_NOMBRE_RESERVADO", objetivo, objetivo.id))
        elif isinstance(nodo, ast.Call):
            nombre = _nombre_llamada(nodo.func)
            if nombre in ("eval", "exec"):
                hallazgos.append(_hallazgo("SEG_PY_EVAL", nodo))
            elif nombre in ("os.system", "os.popen") or (nombre.startswith("subprocess.") and any(
                    k.arg == "shell" and isinstance(k.value, ast.Constant) and k.value.value is True for k in nodo.keywords)):
                hallazgos.append(_hallazgo("SEG_PY_SHELL", nodo))
            elif nombre in ("pickle.load", "pickle.loads"):
                hallazgos.append(_hallazgo("SEG_PY_PICKLE", nodo))
        if isinstance(nodo, (ast.FunctionDef, ast.AsyncFunctionDef)):
            predeterminados = list(nodo.args.defaults) + [d for d in nodo.args.kw_defaults if d is not None]
            if any(isinstance(d, (ast.List, ast.Dict, ast.Set)) for d in predeterminados):
                hallazgos.append(_hallazgo("PY_ARGUMENTO_MUTABLE", nodo))
            complejidad = _complejidad(nodo)
            if complejidad > 10:
                hallazgos.append(_hallazgo("FUNCION_COMPLEJA", nodo, f"{nodo.name}|{complejidad}"))
            lineas = (getattr(nodo, "end_lineno", nodo.lineno) or nodo.lineno) - nodo.lineno + 1
            if lineas > 50:
                hallazgos.append(_hallazgo("FUNCION_LARGA", nodo, f"{nodo.name}|{lineas}"))
            cantidad = len(nodo.args.args) + len(nodo.args.kwonlyargs) + len(nodo.args.posonlyargs)
            if cantidad > 5:
                hallazgos.append(_hallazgo("MUCHOS_PARAMETROS", nodo, f"{nodo.name}|{cantidad}"))
            if _profundidad(nodo) > 4:
                hallazgos.append(_hallazgo("ANIDAMIENTO_PROFUNDO", nodo, nodo.name))
    hallazgos.extend(_variables_no_usadas(codigo, arbol))
    return json.dumps(hallazgos, ensure_ascii=False)


def _tipo_valor(valor):
    if valor is None:
        return "sin_valor"
    if isinstance(valor, ast.Constant):
        if isinstance(valor.value, bool):
            return "booleano"
        if isinstance(valor.value, (int, float, complex)):
            return "numero"
        if isinstance(valor.value, str):
            return "cadena"
        if valor.value is None:
            return "nulo"
        return "literal"
    if isinstance(valor, ast.JoinedStr):
        return "cadena"
    tipos = {ast.List: "lista", ast.ListComp: "lista", ast.Dict: "diccionario", ast.DictComp: "diccionario",
             ast.Tuple: "tupla", ast.Set: "conjunto", ast.SetComp: "conjunto", ast.Call: "llamada",
             ast.BinOp: "operacion", ast.Lambda: "funcion", ast.Compare: "booleano", ast.BoolOp: "booleano"}
    return tipos.get(type(valor), "expresion")


def _texto_corto(nodo):
    try:
        texto = ast.unparse(nodo)
    except Exception:
        return ""
    return texto if len(texto) <= 30 else texto[:27] + "..."


def estructura(codigo):
    try:
        arbol = ast.parse(codigo, ARCHIVO_USUARIO)
    except SyntaxError as error:
        return json.dumps({"error": f"{error.msg} (línea {error.lineno})", "lineas": []}, ensure_ascii=False)
    lineas = []

    def agregar(nodo, nivel, **datos):
        datos["linea"] = nodo.lineno
        datos["nivel"] = nivel
        lineas.append(datos)

    def visitar(cuerpo, nivel, asignadas):
        for nodo in cuerpo:
            if isinstance(nodo, (ast.Assign, ast.AnnAssign)):
                objetivo = nodo.targets[0] if isinstance(nodo, ast.Assign) else nodo.target
                nombre = objetivo.id if isinstance(objetivo, ast.Name) else _texto_corto(objetivo)
                valor = _tipo_valor(nodo.value)
                if isinstance(objetivo, ast.Name) and nombre not in asignadas:
                    asignadas.add(nombre)
                    agregar(nodo, nivel, tipo="declaracion_variable", nombre=nombre, valor=valor)
                else:
                    agregar(nodo, nivel, tipo="asignacion", nombre=nombre, operador="=")
            elif isinstance(nodo, ast.AugAssign):
                simbolos = {ast.Add: "+=", ast.Sub: "-=", ast.Mult: "*=", ast.Div: "/="}
                agregar(nodo, nivel, tipo="asignacion", nombre=_texto_corto(nodo.target), operador=simbolos.get(type(nodo.op), "operador"))
            elif isinstance(nodo, (ast.FunctionDef, ast.AsyncFunctionDef)):
                agregar(nodo, nivel, tipo="funcion", nombre=nodo.name, cantidad=len(nodo.args.args),
                        extra="async" if isinstance(nodo, ast.AsyncFunctionDef) else "")
                visitar(nodo.body, nivel + 1, set(a.arg for a in nodo.args.args))
            elif isinstance(nodo, ast.ClassDef):
                base = nodo.bases[0].id if nodo.bases and isinstance(nodo.bases[0], ast.Name) else ""
                agregar(nodo, nivel, tipo="clase", nombre=nodo.name, extra=base)
                for miembro in nodo.body:
                    if isinstance(miembro, (ast.FunctionDef, ast.AsyncFunctionDef)):
                        agregar(miembro, nivel + 1, tipo="metodo", nombre=miembro.name, cantidad=max(0, len(miembro.args.args) - 1))
                        visitar(miembro.body, nivel + 2, set(a.arg for a in miembro.args.args))
                    else:
                        visitar([miembro], nivel + 1, asignadas)
            elif isinstance(nodo, ast.Return):
                agregar(nodo, nivel, tipo="retorno", valor=_tipo_valor(nodo.value))
            elif isinstance(nodo, ast.If):
                agregar(nodo, nivel, tipo="condicional")
                visitar(nodo.body, nivel + 1, asignadas)
                if len(nodo.orelse) == 1 and isinstance(nodo.orelse[0], ast.If):
                    visitar(nodo.orelse, nivel, asignadas)
                elif nodo.orelse:
                    agregar(nodo.orelse[0], nivel, tipo="alternativa")
                    visitar(nodo.orelse, nivel + 1, asignadas)
            elif isinstance(nodo, (ast.For, ast.AsyncFor)):
                nombre = nodo.target.id if isinstance(nodo.target, ast.Name) else _texto_corto(nodo.target)
                asignadas.add(nombre)
                agregar(nodo, nivel, tipo="bucle_por_cada", nombre=nombre, extra=_texto_corto(nodo.iter))
                visitar(nodo.body, nivel + 1, asignadas)
            elif isinstance(nodo, ast.While):
                agregar(nodo, nivel, tipo="bucle_while")
                visitar(nodo.body, nivel + 1, asignadas)
            elif isinstance(nodo, ast.Try):
                agregar(nodo, nivel, tipo="intento")
                visitar(nodo.body, nivel + 1, asignadas)
                for manejador in nodo.handlers:
                    agregar(manejador, nivel, tipo="captura")
                    visitar(manejador.body, nivel + 1, asignadas)
            elif isinstance(nodo, ast.Raise):
                agregar(nodo, nivel, tipo="lanzar")
            elif isinstance(nodo, ast.Break):
                agregar(nodo, nivel, tipo="romper")
            elif isinstance(nodo, ast.Continue):
                agregar(nodo, nivel, tipo="continuar")
            elif isinstance(nodo, ast.Pass):
                agregar(nodo, nivel, tipo="pasar")
            elif isinstance(nodo, (ast.Import, ast.ImportFrom)):
                modulo = nodo.module if isinstance(nodo, ast.ImportFrom) else nodo.names[0].name
                agregar(nodo, nivel, tipo="importacion", nombre=modulo or "")
            elif hasattr(ast, "Match") and isinstance(nodo, ast.Match):
                agregar(nodo, nivel, tipo="seleccion", cantidad=len(nodo.cases))
                for caso in nodo.cases:
                    visitar(caso.body, nivel + 1, asignadas)
            elif isinstance(nodo, ast.With):
                agregar(nodo, nivel, tipo="expresion")
                visitar(nodo.body, nivel + 1, asignadas)
            elif isinstance(nodo, ast.Expr) and isinstance(nodo.value, ast.Call):
                agregar(nodo, nivel, tipo="llamada", nombre=_nombre_llamada(nodo.value.func) or "función", cantidad=len(nodo.value.args))
            elif isinstance(nodo, ast.Expr) and isinstance(nodo.value, ast.Await):
                agregar(nodo, nivel, tipo="espera")
            elif isinstance(nodo, ast.Expr) and isinstance(nodo.value, ast.Constant) and isinstance(nodo.value.value, str):
                continue
            else:
                agregar(nodo, nivel, tipo="expresion")

    visitar(arbol.body, 0, set())
    return json.dumps({"error": None, "lineas": lineas}, ensure_ascii=False)


_ruta_paquetes = None


def agregar_ruta_paquetes(ruta):
    import importlib
    global _ruta_paquetes
    if _ruta_paquetes and _ruta_paquetes != ruta and _ruta_paquetes in sys.path:
        sys.path.remove(_ruta_paquetes)
    _ruta_paquetes = ruta or None
    if ruta and ruta not in sys.path:
        sys.path.insert(0, ruta)
        importlib.invalidate_caches()
    return bool(ruta) and ruta in sys.path


def version():
    return sys.version.split()[0]
