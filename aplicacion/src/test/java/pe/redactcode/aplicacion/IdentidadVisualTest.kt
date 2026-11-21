package pe.redactcode.aplicacion

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.redactcode.aplicacion.nucleo.diseno.EntradaMarca

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class IdentidadVisualTest {
    @Test
    fun entradaSoloSeAnimaSiTodasLasCondicionesSeCumplen() {
        val combinaciones = listOf(true, false)
        for (a in combinaciones) for (b in combinaciones) for (c in combinaciones) {
            assertEquals(a && b && c, EntradaMarca.debeAnimarEntrada(a, b, c))
        }
        assertFalse(EntradaMarca.debeAnimarEntrada(false, true, true))
    }

    @Test
    fun fondoDeVentanaClaroPorDefecto() {
        val c = ApplicationProvider.getApplicationContext<android.content.Context>().getColor(R.color.fondo_ventana)
        assertEquals(0xFFF6F8FA.toInt(), c)
    }

    @Test
    @Config(sdk = [35], application = android.app.Application::class, qualifiers = "night")
    fun fondoDeVentanaOscuroEnModoNocturno() {
        val c = ApplicationProvider.getApplicationContext<android.content.Context>().getColor(R.color.fondo_ventana)
        assertEquals(0xFF0F131A.toInt(), c)
    }

    @Test
    fun derivadosDelLogoExistenComoRecursos() {
        val r = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        assertTrue(r.getIdentifier("logo_inicio", "drawable", "pe.redactcode.aplicacion") != 0)
        assertTrue(r.getIdentifier("logo_redact_baldosa", "drawable", "pe.redactcode.aplicacion") != 0)
    }
}
