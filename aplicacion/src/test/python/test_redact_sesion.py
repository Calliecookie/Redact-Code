import json
import os
import sys
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "main", "python"))
import redact_sesion as r


def drenar(hasta, maximo=8):
    eventos = []
    limite = time.time() + maximo
    while time.time() < limite:
        texto = r.siguiente_evento(100)
        if texto:
            evento = json.loads(texto)
            eventos.append(evento)
            if hasta(evento):
                return eventos
    raise AssertionError("no llegó el evento esperado: " + str(eventos[-5:]))


def esperar_libre():
    limite = time.time() + 5
    while r.activa() and time.time() < limite:
        time.sleep(0.05)


def texto_salida(eventos):
    return "".join(e["texto"] for e in eventos if e["tipo"] == "salida")


class PruebasSesion(unittest.TestCase):
    def setUp(self):
        esperar_libre()

    def test_input_interactivo(self):
        r.iniciar("nombre = input('nombre: ')\nprint('hola', nombre)\n", False, "[]", 10)
        eventos = drenar(lambda e: e["tipo"] == "entrada")
        self.assertIn("nombre: ", texto_salida(eventos))
        r.enviar_entrada("ana")
        eventos += drenar(lambda e: e["tipo"] == "fin")
        self.assertIn("hola ana", texto_salida(eventos))
        self.assertEqual(eventos[-1]["estado"], "EXITO")

    def test_la_espera_de_entrada_no_cuenta_para_el_limite(self):
        r.iniciar("x = input()\nprint(x)\n", False, "[]", 1)
        drenar(lambda e: e["tipo"] == "entrada")
        time.sleep(1.6)
        r.enviar_entrada("ok")
        fin = drenar(lambda e: e["tipo"] == "fin")[-1]
        self.assertEqual(fin["estado"], "EXITO")

    def test_bucle_infinito_se_corta(self):
        r.iniciar("while True:\n    pass\n", False, "[]", 1)
        fin = drenar(lambda e: e["tipo"] == "fin", 10)[-1]
        self.assertEqual(fin["estado"], "TIEMPO_AGOTADO")

    def test_detener_durante_la_entrada(self):
        r.iniciar("input()\n", False, "[]", 10)
        drenar(lambda e: e["tipo"] == "entrada")
        r.detener()
        fin = drenar(lambda e: e["tipo"] == "fin")[-1]
        self.assertEqual(fin["estado"], "CANCELADO")

    def test_error_con_linea(self):
        r.iniciar("a = 1\nb = 1 / 0\n", False, "[]", 10)
        fin = drenar(lambda e: e["tipo"] == "fin")[-1]
        self.assertEqual(fin["estado"], "ERROR")
        self.assertEqual(fin["linea"], 2)

    def test_error_de_sintaxis(self):
        r.iniciar("def f(:\n", False, "[]", 10)
        fin = drenar(lambda e: e["tipo"] == "fin")[-1]
        self.assertEqual(fin["estado"], "ERROR")
        self.assertIn("SyntaxError", fin["mensaje"])

    CODIGO = "def f(x):\n    y = x * 2\n    return y\n\nz = f(3)\nw = f(z)\nprint(w)\n"

    def test_punto_de_interrupcion_variables_pila_y_evaluacion(self):
        r.iniciar(self.CODIGO, True, "[2]", 10)
        pausa = drenar(lambda e: e["tipo"] == "pausa")[-1]
        self.assertEqual(pausa["linea"], 2)
        self.assertEqual(pausa["pila"][0]["funcion"], "f")
        self.assertEqual({v["nombre"]: v["valor"] for v in pausa["variables"]}["x"], "3")
        r.comando_depurador("evaluar", "x + 1")
        self.assertEqual(drenar(lambda e: e["tipo"] == "evaluacion")[-1]["texto"], "4")
        r.comando_depurador("evaluar", "no_existe")
        self.assertTrue(drenar(lambda e: e["tipo"] == "evaluacion")[-1]["error"])
        r.comando_depurador("continuar")
        segunda = drenar(lambda e: e["tipo"] == "pausa")[-1]
        self.assertEqual({v["nombre"]: v["valor"] for v in segunda["variables"]}["x"], "6")
        r.comando_depurador("continuar")
        eventos = drenar(lambda e: e["tipo"] == "fin")
        self.assertIn("12", texto_salida(eventos))

    def test_paso_entrar_y_salir(self):
        r.iniciar(self.CODIGO, True, "[5]", 10)
        self.assertEqual(drenar(lambda e: e["tipo"] == "pausa")[-1]["linea"], 5)
        r.comando_depurador("entrar")
        self.assertEqual(drenar(lambda e: e["tipo"] == "pausa")[-1]["linea"], 2)
        r.comando_depurador("paso")
        self.assertEqual(drenar(lambda e: e["tipo"] == "pausa")[-1]["linea"], 3)
        r.comando_depurador("salir")
        self.assertEqual(drenar(lambda e: e["tipo"] == "pausa")[-1]["linea"], 6)
        r.comando_depurador("paso")
        self.assertEqual(drenar(lambda e: e["tipo"] == "pausa")[-1]["linea"], 7)
        r.comando_depurador("continuar")
        self.assertEqual(drenar(lambda e: e["tipo"] == "fin")[-1]["estado"], "EXITO")

    def test_detener_en_pausa(self):
        r.iniciar(self.CODIGO, True, "[2]", 10)
        drenar(lambda e: e["tipo"] == "pausa")
        r.comando_depurador("detener")
        self.assertEqual(drenar(lambda e: e["tipo"] == "fin")[-1]["estado"], "CANCELADO")

    def test_sin_puntos_corre_hasta_el_final(self):
        r.iniciar(self.CODIGO, True, "[]", 10)
        eventos = drenar(lambda e: e["tipo"] == "fin")
        self.assertIn("12", texto_salida(eventos))

    def test_depurando_sin_puntos_tambien_respeta_el_limite(self):
        r.iniciar("while True:\n    pass\n", True, "[]", 1)
        self.assertEqual(drenar(lambda e: e["tipo"] == "fin", 10)[-1]["estado"], "TIEMPO_AGOTADO")

    def test_limite_tras_continuar_desde_una_pausa(self):
        r.iniciar("x = 1\nwhile True:\n    pass\n", True, "[1]", 1)
        drenar(lambda e: e["tipo"] == "pausa")
        r.comando_depurador("continuar")
        self.assertEqual(drenar(lambda e: e["tipo"] == "fin", 10)[-1]["estado"], "TIEMPO_AGOTADO")

    def test_detener_un_bucle_ocupado(self):
        r.iniciar("while True:\n    pass\n", False, "[]", 30)
        time.sleep(0.3)
        r.detener()
        self.assertEqual(drenar(lambda e: e["tipo"] == "fin", 5)[-1]["estado"], "CANCELADO")

    def test_sesion_ocupada(self):
        r.iniciar("input()\n", False, "[]", 10)
        drenar(lambda e: e["tipo"] == "entrada")
        self.assertEqual(r.iniciar("print(1)", False, "[]", 10), "ocupada")
        r.detener()
        drenar(lambda e: e["tipo"] == "fin")


if __name__ == "__main__":
    unittest.main()
