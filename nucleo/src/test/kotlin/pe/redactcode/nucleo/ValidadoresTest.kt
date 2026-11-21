package pe.redactcode.nucleo

import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.redactcode.nucleo.javascript.MotorRhino
import pe.redactcode.nucleo.javascript.ServicioJavaScript
import pe.redactcode.nucleo.modelo.Severidad
import pe.redactcode.nucleo.validacion.CatalogoExplicaciones
import pe.redactcode.nucleo.validacion.ValidadorCss
import pe.redactcode.nucleo.validacion.ValidadorHtml
import pe.redactcode.nucleo.validacion.ValidadorJson

class ValidadoresTest {
    companion object {
        val motor = MotorRhino()
        val servicioJs = ServicioJavaScript(motor)

        @JvmStatic
        @AfterClass
        fun cerrar() = motor.cerrar()
    }

    private val json = ValidadorJson()
    private val css = ValidadorCss()
    private val html = ValidadorHtml()

    @Test
    fun catalogoDeExplicacionesCompleto() {
        val todas = CatalogoExplicaciones.todas
        assertTrue(todas.size >= 60)
        todas.forEach {
            assertTrue(it.codigo, it.titulo.isNotBlank() && it.explicacion.isNotBlank() && it.recomendacion.isNotBlank() && it.causaProbable.isNotBlank())
        }
    }

    @Test
    fun jsonValidoSinDiagnosticos() {
        val r = json.validarSincrono("{\n  \"nombre\": \"Ana\",\n  \"edad\": 20,\n  \"activo\": true,\n  \"notas\": [12.5, -3, 1e3],\n  \"extra\": null\n}")
        assertTrue(r.diagnosticos.toString(), r.diagnosticos.isEmpty())
    }

    @Test
    fun jsonComaFinalConPosicion() {
        val r = json.validarSincrono("{\n  \"a\": 1,\n  \"b\": 2,\n}")
        val d = r.errores.single()
        assertEquals("JSON001", d.codigo)
        assertEquals(3, d.linea)
        assertEquals(9, d.columna)
        assertNotNull(d.explicacion)
    }

    @Test
    fun jsonErroresFrecuentes() {
        assertEquals("JSON002", json.validarSincrono("{'a': 1}").errores.single().codigo)
        assertEquals("JSON003", json.validarSincrono("{a: 1}").errores.single().codigo)
        assertEquals("JSON004", json.validarSincrono("[1, 2 3]").errores.single().codigo)
        assertEquals("JSON005", json.validarSincrono("{\"a\" 1}").errores.single().codigo)
        assertEquals("JSON006", json.validarSincrono("{\"a\": [1, 2").errores.single().codigo)
        assertEquals("JSON007", json.validarSincrono("{\"a\": [1, 2}").errores.single().codigo)
        assertEquals("JSON008", json.validarSincrono("{\"a\": undefined}").errores.single().codigo)
        assertEquals("JSON009", json.validarSincrono("{\"a\": \"hola}").errores.single().codigo)
        assertEquals("JSON010", json.validarSincrono("{} {}").errores.single().codigo)
        assertEquals("JSON011", json.validarSincrono("{\n// x\n}").errores.single().codigo)
        assertEquals("JSON012", json.validarSincrono("[007]").errores.single().codigo)
        assertEquals("JSON013", json.validarSincrono("[\"C:\\x\"]").errores.single().codigo)
        assertEquals("JSON014", json.validarSincrono("   ").errores.single().codigo)
    }

    @Test
    fun jsonClaveDuplicadaEsAdvertencia() {
        val r = json.validarSincrono("{\"id\": 1, \"id\": 2}")
        assertTrue(r.esValido)
        assertEquals("JSON015", r.advertencias.single().codigo)
    }

    @Test
    fun cssValidoIncluyendoMediaYAnidamiento() {
        val codigo = """
            @import url("base.css");
            :root { --principal: #0d0d0f; }
            body { font-family: "Inter", sans-serif; color: var(--principal); }
            .tarjeta { padding: calc(1rem + 2px); background: url(fondo.png) no-repeat; }
            @media (max-width: 600px) {
              .tarjeta { padding: 8px; }
            }
            @keyframes aparecer { from { opacity: 0; } to { opacity: 1; } }
            a { &:hover { color: red; } }
        """.trimIndent()
        val r = css.validarSincrono(codigo)
        assertTrue(r.diagnosticos.toString(), r.diagnosticos.isEmpty())
    }

    @Test
    fun cssPuntoYComaFaltante() {
        val r = css.validarSincrono("p {\n  color: red\n  margin: 0;\n}")
        val d = r.errores.single()
        assertEquals("CSS004", d.codigo)
        assertEquals(2, d.linea)
    }

    @Test
    fun cssErroresEstructurales() {
        assertEquals("CSS001", css.validarSincrono("body {\n  color: #333;\n\nh1 { margin: 0; }").errores.first().codigo)
        assertEquals("CSS002", css.validarSincrono("p { color: red; } }").errores.single().codigo)
        assertEquals("CSS003", css.validarSincrono("p { color red; }").errores.single().codigo)
        assertTrue(css.validarSincrono("body { font-family: \"Inter; }").errores.any { it.codigo == "CSS005" })
        assertEquals("CSS006", css.validarSincrono("/* sin cierre\nbody {}").errores.single().codigo)
        assertEquals("CSS008", css.validarSincrono("h1 { font-size: ; }").errores.single().codigo)
        assertEquals("CSS009", css.validarSincrono("{ color: blue; }").errores.single().codigo)
        assertEquals("CSS010", css.validarSincrono("div { width: calc(100% - 2rem; }").errores.single().codigo)
        assertEquals("CSS011", css.validarSincrono("color: red;").errores.single().codigo)
    }

    @Test
    fun cssPropiedadDesconocidaEsAdvertencia() {
        val r = css.validarSincrono("p { colr: red; -webkit-appearance: none; --mi-var: 1; }")
        assertTrue(r.esValido)
        assertEquals("CSS007", r.advertencias.single().codigo)
        assertEquals(Severidad.ADVERTENCIA, r.advertencias.single().severidad)
    }

    @Test
    fun htmlValidoSinErrores() {
        val codigo = "<!DOCTYPE html>\n<html lang=\"es\">\n<head><meta charset=\"utf-8\"><title>T</title></head>\n<body>\n<ul><li>uno<li>dos</ul>\n<p>Hola<br>mundo</p>\n<img src=\"a.png\" alt=\"a\">\n</body>\n</html>"
        val r = html.validarSincrono(codigo)
        assertTrue(r.diagnosticos.toString(), r.diagnosticos.isEmpty())
    }

    @Test
    fun htmlDetectaProblemas() {
        val sinCerrar = html.validarSincrono("<div class=\"a\">\n  <h2>Título</h2>\n")
        assertTrue(sinCerrar.diagnosticos.any { it.codigo == "HTML005" && it.linea == 1 })
        val cierreSobrante = html.validarSincrono("<p>Hola</p></span>")
        assertTrue(cierreSobrante.diagnosticos.toString(), cierreSobrante.errores.any { it.codigo == "HTML001" })
        val sinDoctype = html.validarSincrono("<html><head><title>x</title></head><body></body></html>")
        assertTrue(sinDoctype.diagnosticos.any { it.codigo == "HTML006" })
        val obsoleto = html.validarSincrono("<center>x</center>")
        assertTrue(obsoleto.diagnosticos.any { it.codigo == "HTML009" })
    }

    @Test
    fun javascriptModernoValido() = runBlocking {
        val codigo = """
            class Persona {
              #edad = 0;
              constructor(nombre) { this.nombre = nombre; }
              get saludo() { return `Hola ${'$'}{this.nombre}`; }
            }
            const datos = { a: 1, ...{ b: 2 } };
            async function cargar() { for await (const x of []) {} return datos?.a ?? 0; }
            for (const n of [1, 2]) { console.log(n ** 2); }
        """.trimIndent()
        val r = servicioJs.validar(codigo)
        assertTrue(r.diagnosticos.toString(), r.diagnosticos.isEmpty())
    }

    @Test
    fun javascriptErroresConPosicion() = runBlocking {
        val r = servicioJs.validar("const a = 1;\nif (a > 0 {\n  console.log(a);\n}")
        val d = r.errores.single()
        assertEquals("JS001", d.codigo)
        assertEquals(2, d.linea)
        assertEquals(11, d.columna)
        assertEquals("JS002", servicioJs.validar("const s = \"hola;").errores.single().codigo)
        assertEquals("JS005", servicioJs.validar("let x = 1;\nlet x = 2;").errores.single().codigo)
        assertEquals("JS015", servicioJs.validar("function f() {\n  return 1;").errores.single().codigo)
        assertEquals("JS009", servicioJs.validar("return 5;").errores.single().codigo)
        assertTrue(servicioJs.validar("import x from './a.js';\nexport const y = 1;").esValido)
    }

    @Test
    fun javascriptRendimientoMilLineas() = runBlocking {
        val codigo = (1..1000).joinToString("\n") { "const valor$it = (a, b) => { return a + b * $it; };" }
        servicioJs.validar(codigo)
        val r = servicioJs.validar(codigo)
        println("RENDIMIENTO validacion JS 1000 lineas (JVM escritorio, Rhino interpretado): ${r.duracionMs} ms")
        assertTrue(r.esValido)
        val inicioJson = System.nanoTime()
        json.validarSincrono("[" + (1..1000).joinToString(",\n") { "{\"id\": $it, \"nombre\": \"elemento $it\"}" } + "]")
        println("RENDIMIENTO validacion JSON 1000 lineas: ${(System.nanoTime() - inicioJson) / 1_000_000} ms")
        val inicioCss = System.nanoTime()
        css.validarSincrono((1..250).joinToString("\n") { ".clase$it {\n  color: #fff;\n  margin: ${it}px;\n}" })
        println("RENDIMIENTO validacion CSS 1000 lineas: ${(System.nanoTime() - inicioCss) / 1_000_000} ms")
        assertFalse(r.duracionMs < 0)
    }
}
