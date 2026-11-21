import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "main", "python"))
import redact_python


class PruebasRedactPython(unittest.TestCase):
    def test_agregar_ruta_es_idempotente(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertTrue(redact_python.agregar_ruta_paquetes(d))
            self.assertTrue(redact_python.agregar_ruta_paquetes(d))
            self.assertEqual(sys.path.count(d), 1)
            redact_python.agregar_ruta_paquetes("")

    def test_cambiar_de_entorno_quita_la_ruta_anterior(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            redact_python.agregar_ruta_paquetes(a)
            redact_python.agregar_ruta_paquetes(b)
            self.assertNotIn(a, sys.path)
            self.assertIn(b, sys.path)
            self.assertFalse(redact_python.agregar_ruta_paquetes(""))
            self.assertNotIn(b, sys.path)

    def test_paquete_instalado_se_puede_importar(self):
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "paqueteprueba"))
            with open(os.path.join(d, "paqueteprueba", "__init__.py"), "w") as f:
                f.write("VALOR = 42\n")
            redact_python.agregar_ruta_paquetes(d)
            r = json.loads(redact_python.ejecutar("import paqueteprueba\nprint(paqueteprueba.VALOR)", "", 10))
            redact_python.agregar_ruta_paquetes("")
            self.assertEqual(r["estado"], "EXITO")
            self.assertEqual(r["salida"].strip(), "42")

    def test_entrada_estandar(self):
        r = json.loads(redact_python.ejecutar("print(input().upper())", "hola\n", 10))
        self.assertIn("HOLA", r["salida"])

    def test_limite_de_tiempo(self):
        r = json.loads(redact_python.ejecutar("while True:\n    pass", "", 1))
        self.assertEqual(r["estado"], "TIEMPO_AGOTADO")


if __name__ == "__main__":
    unittest.main()
