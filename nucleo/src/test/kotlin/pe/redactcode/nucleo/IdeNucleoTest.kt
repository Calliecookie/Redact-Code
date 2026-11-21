package pe.redactcode.nucleo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.redactcode.nucleo.control.ControlVersiones
import pe.redactcode.nucleo.control.ErrorControlVersiones
import pe.redactcode.nucleo.control.EstadoCambio
import pe.redactcode.nucleo.ide.BusquedaGlobal
import pe.redactcode.nucleo.ide.CoincidenciaDifusa
import pe.redactcode.nucleo.ide.Diferencias
import pe.redactcode.nucleo.ide.ExtractorSimbolos
import pe.redactcode.nucleo.ide.Formateador
import pe.redactcode.nucleo.ide.OpcionesBusqueda
import pe.redactcode.nucleo.ide.ComandoInterno
import pe.redactcode.nucleo.ide.ResultadoComando
import pe.redactcode.nucleo.ide.TerminalLocal
import pe.redactcode.nucleo.ide.TipoCambioLinea
import pe.redactcode.nucleo.ide.TipoSimbolo
import pe.redactcode.nucleo.modelo.Lenguaje
import java.io.File

class IdeNucleoTest {
    @get:Rule val temporal = TemporaryFolder()

    @Test
    fun diferenciasMinimasYBloques() {
        val a = "uno\ndos\ntres\ncuatro\ncinco\n"
        val b = "uno\nDOS\ntres\ncuatro\ncinco\nseis\n"
        val r = Diferencias.comparar(a, b, contexto = 1)
        assertEquals(2, r.agregadas)
        assertEquals(1, r.eliminadas)
        assertEquals(2, r.bloques.size)
        assertEquals(listOf("dos"), r.lineas.filter { it.tipo == TipoCambioLinea.ELIMINADA }.map { it.texto })
        assertTrue(r.unificado("a", "b").contains("@@ -1,3 +1,3 @@"))
        assertEquals(b, Diferencias.aplicarBloques(a, b, r, setOf(0, 1)))
        assertEquals(a, Diferencias.aplicarBloques(a, b, r, emptySet()))
        assertEquals("uno\nDOS\ntres\ncuatro\ncinco\n", Diferencias.aplicarBloques(a, b, r, setOf(0)))
        assertEquals("uno\ndos\ntres\ncuatro\ncinco\nseis\n", Diferencias.aplicarBloques(a, b, r, setOf(1)))
        assertTrue(Diferencias.comparar(a, a).sinCambios)
        val grande = (1..3000).joinToString("\n") { "linea $it" }
        val modificado = grande.replace("linea 1500", "cambio")
        val rg = Diferencias.comparar(grande, modificado)
        assertEquals(1, rg.agregadas)
        assertEquals(1, rg.eliminadas)
        val x = listOf("a", "b", "c", "a", "b", "b", "a")
        val y = listOf("c", "b", "a", "b", "a", "c")
        val ry = Diferencias.compararLineas(x, y)
        assertEquals(5, ry.agregadas + ry.eliminadas)
        assertEquals(y, ry.lineas.filter { it.tipo != TipoCambioLinea.ELIMINADA }.map { it.texto })
        assertEquals(x, ry.lineas.filter { it.tipo != TipoCambioLinea.AGREGADA }.map { it.texto })
    }

    @Test
    fun controlDeVersionesCompleto() {
        val raiz = temporal.newFolder("proyecto")
        File(raiz, "main.py").writeText("print('hola')\n")
        File(raiz, "src").mkdirs()
        File(raiz, "src/util.py").writeText("def f():\n    return 1\n")
        var reloj = 1_000L
        val vcs = ControlVersiones(raiz, reloj = { reloj++ })
        vcs.inicializar()
        assertEquals("main", vcs.ramaActual())
        assertEquals(2, vcs.estado().size)
        assertTrue(vcs.estado().all { it.estado == EstadoCambio.AGREGADO })
        val c1 = vcs.confirmar("Primer commit", "Ana", "ana@ejemplo.pe")
        assertTrue(vcs.estado().isEmpty())
        assertEquals(ControlVersiones.hashBlob("print('hola')\n".toByteArray()), c1.arbol["main.py"])
        assertEquals("95d09f2b10159347eece71399a7e2e907ea3df4f", ControlVersiones.hashBlob("hello world".toByteArray()))
        File(raiz, "main.py").writeText("print('adiós')\n")
        File(raiz, "src/util.py").delete()
        File(raiz, "nuevo.js").writeText("console.log(1)\n")
        val estado = vcs.estado().associate { it.ruta to it.estado }
        assertEquals(mapOf("main.py" to EstadoCambio.MODIFICADO, "nuevo.js" to EstadoCambio.AGREGADO, "src/util.py" to EstadoCambio.ELIMINADO), estado)
        val dif = vcs.diferenciaTrabajo("main.py")!!
        assertEquals(1, dif.diferencias!!.agregadas)
        vcs.descartar("nuevo.js")
        assertFalse(File(raiz, "nuevo.js").exists())
        vcs.confirmar("Solo main", "Ana", rutas = setOf("main.py"))
        assertEquals(listOf("src/util.py"), vcs.estado().map { it.ruta })
        vcs.descartar("src/util.py")
        assertEquals("def f():\n    return 1\n", File(raiz, "src/util.py").readText())
        assertEquals(2, vcs.historial().size)
        assertEquals("Solo main", vcs.historial().first().mensaje)
        assertEquals(1, vcs.diferenciasCommit(vcs.historial().first().id).size)

        vcs.crearRama("experimento")
        assertEquals("experimento", vcs.ramaActual())
        File(raiz, "extra.txt").writeText("rama\n")
        vcs.confirmar("Extra", "Ana")
        File(raiz, "main.py").writeText("cambio sin confirmar\n")
        try { vcs.cambiarRama("main"); throw AssertionError("Debió fallar") } catch (e: ErrorControlVersiones) { assertTrue(e.message!!.contains("Confirma")) }
        vcs.descartar("main.py")
        vcs.cambiarRama("main")
        assertFalse(File(raiz, "extra.txt").exists())
        vcs.cambiarRama("experimento")
        assertEquals("rama\n", File(raiz, "extra.txt").readText())
        assertEquals(listOf("experimento", "main"), vcs.ramas())
        assertNotNull(ControlVersiones.validarRama("../malo"))
        assertNull(ControlVersiones.validarRama("feature/login-2"))
        try { vcs.confirmar("vacío", "Ana"); throw AssertionError("Debió fallar") } catch (e: ErrorControlVersiones) { assertTrue(e.message!!.contains("No hay cambios")) }
        try { vcs.resolver("../fuera.txt"); throw AssertionError("Debió fallar") } catch (_: ErrorControlVersiones) { }
        File(raiz, "foto.png").writeBytes(byteArrayOf(0, 1, 2, 3))
        assertTrue(vcs.diferenciaTrabajo("foto.png")!!.esBinario)
    }

    @Test
    fun busquedaGlobalYReemplazo() {
        val archivos = mapOf<String, () -> String?>(
            "a.js" to { "const total = 1;\nlet Total = 2;\n" },
            "src/b.py" to { "total = 3\nsubtotal = 4\n" },
            "notas.txt" to { "nada" }
        )
        val simple = BusquedaGlobal.buscar(archivos, OpcionesBusqueda("total"))
        assertEquals(4, simple.total)
        assertEquals(3, BusquedaGlobal.buscar(archivos, OpcionesBusqueda("total", palabraCompleta = true)).total)
        assertEquals(2, BusquedaGlobal.buscar(archivos, OpcionesBusqueda("total", distinguirMayusculas = true, palabraCompleta = true)).total)
        assertEquals(1, BusquedaGlobal.buscar(archivos, OpcionesBusqueda("total", incluir = "*.js", distinguirMayusculas = true)).total)
        assertEquals(2, BusquedaGlobal.buscar(archivos, OpcionesBusqueda("total", excluir = "src/**")).total)
        assertEquals(4, BusquedaGlobal.buscar(archivos, OpcionesBusqueda("\\b\\w+ = \\d", expresionRegular = true)).total)
        assertNotNull(BusquedaGlobal.buscar(archivos, OpcionesBusqueda("(", expresionRegular = true)).error)
        val (nuevo, cantidad) = BusquedaGlobal.reemplazar("a1 b2", OpcionesBusqueda("([a-z])(\\d)", expresionRegular = true), "$2$1")
        assertEquals("1a 2b", nuevo)
        assertEquals(2, cantidad)
        assertEquals("x.y = 1" to 1, BusquedaGlobal.reemplazar("a.b = 1", OpcionesBusqueda("a.b"), "x.y"))
    }

    @Test
    fun coincidenciaDifusaOrdenaComoUnaPaleta() {
        val comandos = listOf("Formatear documento", "Abrir archivo", "Alternar panel inferior", "Buscar en archivos", "Ir a símbolo")
        val r = CoincidenciaDifusa.filtrar("fmt", comandos, { it })
        assertEquals("Formatear documento", r.first().elemento)
        assertEquals("Ir a símbolo", CoincidenciaDifusa.filtrar("simbolo", comandos, { it }).first().elemento)
        assertEquals("Alternar panel inferior", CoincidenciaDifusa.filtrar("api", comandos, { it }).first().elemento)
        assertTrue(CoincidenciaDifusa.filtrar("zzz", comandos, { it }).isEmpty())
    }

    @Test
    fun formateadorPorLenguaje() {
        val json = Formateador.formatear(Lenguaje.JSON, "{\"a\":[1,2,{\"b\":null}],\"c\":\"x\\\"y\",\"d\":{}}", 2)
        assertEquals("{\n  \"a\": [\n    1,\n    2,\n    {\n      \"b\": null\n    }\n  ],\n  \"c\": \"x\\\"y\",\n  \"d\": {}\n}\n", json.texto)
        assertTrue(json.cambiado)
        assertNotNull(Formateador.formatear(Lenguaje.JSON, "{\"a\":}", 2).error)
        val css = Formateador.formatear(Lenguaje.CSS, "body{color:red;margin:0}/* c */a:hover{background:url(\"x;y.png\")}", 2).texto
        assertEquals("body {\n  color: red;\n  margin: 0;\n}\n\n/* c */\na:hover {\n  background: url(\"x;y.png\");\n}\n", css)
        val py = Formateador.formatear(Lenguaje.PYTHON, "def f():\n\treturn 1   \n\n\n", 4).texto
        assertEquals("def f():\n    return 1\n", py)
        val html = Formateador.formatear(Lenguaje.HTML, "<ul><li>a</li><li>b</li></ul>", 2).texto
        assertTrue(html.contains("<ul>\n  <li>a</li>"))
    }

    @Test
    fun simbolosYNavegacion() {
        val py = "class Persona:\n    def saludar(self):\n        pass\n\ndef main():\n    pass\nMAXIMO = 3\n"
        val s = ExtractorSimbolos.extraer(Lenguaje.PYTHON, py)
        assertEquals(listOf("Persona", "saludar", "main", "MAXIMO"), s.map { it.nombre })
        assertEquals(TipoSimbolo.METODO, s[1].tipo)
        val js = "export class Carro {\n  arrancar() {\n    if (x) {}\n  }\n}\nconst sumar = (a, b) => a + b;\nasync function cargar() {}\n"
        assertEquals(listOf("Carro", "arrancar", "sumar", "cargar"), ExtractorSimbolos.extraer(Lenguaje.JAVASCRIPT, js).map { it.nombre })
        assertEquals(listOf("body", ".a, .b > p"), ExtractorSimbolos.extraer(Lenguaje.CSS, "/* x { */\nbody {\n}\n.a, .b > p { color: red }\n").map { it.nombre })
        assertEquals("sumar", ExtractorSimbolos.palabraEn(js, 6, 9))
        assertEquals(6, ExtractorSimbolos.definicion(Lenguaje.JAVASCRIPT, js, "sumar")!!.linea)
        assertEquals(1, ExtractorSimbolos.referencias(js, "sumar").size)
    }

    @Test
    fun terminalEjecutaComandosRealesDentroDelProyecto() = runBlocking {
        val raiz = temporal.newFolder("terminal")
        File(raiz, "carpeta").mkdirs()
        File(raiz, "carpeta/hola.txt").writeText("hola mundo\n")
        val terminal = TerminalLocal(raiz, internos = mapOf("python" to ComandoInterno { args, _ -> ResultadoComando("python ${args.joinToString(" ")}\n") }))
        assertEquals("~\n", terminal.ejecutar("pwd").salida)
        assertEquals(0, terminal.ejecutar("cd carpeta").codigo)
        assertEquals("~/carpeta", terminal.rutaVisible)
        assertEquals("hola mundo\n", terminal.ejecutar("cat hola.txt").salida)
        assertEquals("2\n", terminal.ejecutar("echo 'a b' | wc -w | tr -d ' '").salida)
        assertEquals(1, terminal.ejecutar("cd ../..").codigo)
        assertEquals("~/carpeta", terminal.rutaVisible)
        assertEquals("python app.py\n", terminal.ejecutar("python app.py").salida)
        assertTrue(terminal.ejecutar("exit 3").codigo == 3)
        assertTrue(terminal.ejecutar("sleep 5", limiteSegundos = 1).tiempoAgotado)
        assertTrue(terminal.ejecutar("clear").limpiar)
        assertEquals(listOf("hola.txt"), terminal.autocompletar("cat ho"))
        assertEquals(listOf("a b", "c"), TerminalLocal.dividir("\"a b\" c"))
        assertTrue(terminal.historial.contains("cat hola.txt"))
    }
}
