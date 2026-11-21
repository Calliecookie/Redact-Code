package pe.redactcode.aplicacion

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.redactcode.aplicacion.funcionalidades.editor.IdColores
import pe.redactcode.aplicacion.funcionalidades.editor.LexerResaltado
import pe.redactcode.aplicacion.funcionalidades.editor.Pestana
import pe.redactcode.aplicacion.funcionalidades.problemas.PanelProblemas
import pe.redactcode.aplicacion.nucleo.diseno.TemaRedactCode
import pe.redactcode.nucleo.mentor.CorrectorAutomatico
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.validacion.ValidadorJson

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EditorYUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun colorEn(tramos: List<pe.redactcode.aplicacion.funcionalidades.editor.TramoColor>, linea: Int, columna: Int): Int =
        tramos.filter { it.linea < linea || (it.linea == linea && it.columna <= columna) }.last().color

    @Test
    fun resaltadoPython() {
        val codigo = "def saludar(nombre):\n    # comentario\n    return f\"Hola {nombre}\" + 42"
        val tramos = LexerResaltado(Lenguaje.PYTHON).analizar(codigo)
        assertEquals(IdColores.PALABRA_CLAVE, colorEn(tramos, 0, 0))
        assertEquals(IdColores.FUNCION, colorEn(tramos, 0, 4))
        assertEquals(IdColores.COMENTARIO, colorEn(tramos, 1, 6))
        assertEquals(IdColores.PALABRA_CLAVE, colorEn(tramos, 2, 4))
        assertEquals(IdColores.CADENA, colorEn(tramos, 2, 13))
        assertEquals(IdColores.NUMERO, colorEn(tramos, 2, 31))
    }

    @Test
    fun resaltadoMultilineaJavaScriptYHtml() {
        val js = "/* bloque\n   largo */\nconst x = `a\nb`;"
        val tramosJs = LexerResaltado(Lenguaje.JAVASCRIPT).analizar(js)
        assertEquals(IdColores.COMENTARIO, colorEn(tramosJs, 1, 3))
        assertEquals(IdColores.PALABRA_CLAVE, colorEn(tramosJs, 2, 0))
        assertEquals(IdColores.CADENA, colorEn(tramosJs, 3, 0))
        val html = "<a href=\"x\">texto</a>"
        val tramosHtml = LexerResaltado(Lenguaje.HTML).analizar(html)
        assertEquals(IdColores.ETIQUETA, colorEn(tramosHtml, 0, 1))
        assertEquals(IdColores.ATRIBUTO, colorEn(tramosHtml, 0, 3))
        assertEquals(IdColores.VALOR_ATRIBUTO, colorEn(tramosHtml, 0, 8))
        assertEquals(IdColores.NORMAL, colorEn(tramosHtml, 0, 13))
        val css = "body {\n  color: #fff;\n}"
        val tramosCss = LexerResaltado(Lenguaje.CSS).analizar(css)
        assertEquals(IdColores.ETIQUETA, colorEn(tramosCss, 0, 0))
        assertEquals(IdColores.ATRIBUTO, colorEn(tramosCss, 1, 2))
        assertEquals(IdColores.NUMERO, colorEn(tramosCss, 1, 10))
    }

    @Test
    fun lexerToleraCodigoIncompleto() {
        val json = LexerResaltado(Lenguaje.JSON).analizar("{\"a\": [-1, -2.5e3]}")
        assertEquals(IdColores.NUMERO, colorEn(json, 0, 7))
        listOf("\"sin cerrar", "/* sin cerrar", "<div class=\"x", "a { color: ", "'''triple", "x { color: #fff; margin: -2px }", "[-1, -2.5]", "#", "-", "@", "&").forEach { codigo ->
            Lenguaje.entries.forEach { l -> assertTrue(LexerResaltado(l).analizar(codigo).isNotEmpty()) }
        }
        val grande = (1..1000).joinToString("\n") { "def f$it(a):\n    return a * $it  # nota" }
        val inicio = System.nanoTime()
        LexerResaltado(Lenguaje.PYTHON).analizar(grande)
        println("RENDIMIENTO resaltado 2000 lineas Python: ${(System.nanoTime() - inicio) / 1_000_000} ms")
    }

    @Test
    fun pestanaDetectaModificacion() {
        val p = Pestana("src/main.py", "a", "a")
        assertEquals(Lenguaje.PYTHON, p.lenguaje)
        assertEquals("main.py", p.nombre)
        assertTrue(p.copy(textoActual = "b").modificada)
    }

    @Test
    fun panelDeProblemasMuestraExplicacionYCorreccion() {
        val codigo = "{\n  \"a\": 1,\n}"
        val diagnosticos = ValidadorJson().validarSincrono(codigo).diagnosticos
        var aplicada = false
        compose.setContent {
            TemaRedactCode {
                PanelProblemas(
                    diagnosticos, "Analizador JSON", 1,
                    propuestaPara = { CorrectorAutomatico.proponer(it, codigo) },
                    alIrALinea = {}, alCorregir = { aplicada = true }, alPreguntarMentor = {}
                )
            }
        }
        compose.onNodeWithText("Coma sobrante al final").assertIsDisplayed().performClick()
        compose.onNodeWithText("Causa probable").assertIsDisplayed()
        compose.onNodeWithText("Aplicar corrección").performScrollTo().performClick()
        assertTrue(aplicada)
    }

    @Test
    fun panelDeProblemasVacio() {
        compose.setContent { TemaRedactCode { PanelProblemas(emptyList(), "CPython", 3, { null }, {}, {}, {}) } }
        compose.onNodeWithText("Sin problemas").assertIsDisplayed()
    }
}
