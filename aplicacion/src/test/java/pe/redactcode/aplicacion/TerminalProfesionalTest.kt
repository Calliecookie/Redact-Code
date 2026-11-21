package pe.redactcode.aplicacion

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.redactcode.aplicacion.datos.PlantillaProyecto
import pe.redactcode.aplicacion.datos.ProveedorAnthropic
import pe.redactcode.aplicacion.datos.RepositorioGitHub
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.funcionalidades.espacio.TerminalViewModel
import pe.redactcode.aplicacion.funcionalidades.terminal.EstadoSesion
import pe.redactcode.aplicacion.funcionalidades.terminal.GestorSesionesTerminal
import pe.redactcode.aplicacion.funcionalidades.terminal.LimitesFuente
import pe.redactcode.aplicacion.funcionalidades.terminal.ModificadoresTeclas
import pe.redactcode.aplicacion.funcionalidades.terminal.PaletaAnsi
import pe.redactcode.aplicacion.funcionalidades.terminal.PanelTerminalProfesional
import pe.redactcode.aplicacion.funcionalidades.terminal.ScriptTerminal
import pe.redactcode.aplicacion.funcionalidades.terminal.ServicioPuenteTerminal
import pe.redactcode.aplicacion.funcionalidades.terminal.SesionPty
import pe.redactcode.aplicacion.funcionalidades.terminal.VistaTerminal
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.base_datos.BaseDatosRedactCode
import pe.redactcode.aplicacion.nucleo.diseno.TemaRedactCode
import pe.redactcode.aplicacion.nucleo.diseno.tokensPara
import pe.redactcode.aplicacion.nucleo.ejecucion.MotorJavaScriptCompuesto
import pe.redactcode.aplicacion.nucleo.ejecucion.MotorJavaScriptSandbox
import pe.redactcode.aplicacion.nucleo.ejecucion.PuentePython
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.aplicacion.nucleo.seguridad.AlmacenCredenciales
import pe.redactcode.aplicacion.nucleo.seguridad.CifradorKeystore
import pe.redactcode.nucleo.github.ClienteGitHub
import pe.redactcode.nucleo.javascript.MotorRhino
import pe.redactcode.nucleo.javascript.ServicioJavaScript
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TerminalProfesionalTest {
    @get:Rule
    val compose = createComposeRule()

    private fun contraste(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    @Test
    fun paletaAnsiCumpleContrasteSobreElFondo() {
        val fallos = mutableListOf<String>()
        for (oscuro in listOf(true, false)) for (alto in listOf(false, true)) {
            val paleta = PaletaAnsi.crear(tokensPara(oscuro, alto))
            assertEquals(16, paleta.ansi.size)
            if (contraste(paleta.frente, paleta.fondo) < 4.5) fallos += "frente oscuro=$oscuro alto=$alto"
            if (contraste(paleta.cursor, paleta.fondo) < 3.0) fallos += "cursor oscuro=$oscuro alto=$alto"
            paleta.ansi.forEachIndexed { i, c ->
                val minimo = if (i == 0) 3.0 else 4.5
                val v = contraste(c, paleta.fondo)
                if (v < minimo) fallos += "ansi$i oscuro=$oscuro alto=$alto ${"%.2f".format(v)}"
            }
        }
        assertTrue(fallos.joinToString("\n"), fallos.isEmpty())
    }

    @Test
    fun propiedadesContienenLosDieciseisColores() {
        val p = PaletaAnsi.aPropiedades(PaletaAnsi.crear(tokensPara(true, false)))
        assertEquals(16, (0..15).count { p.getProperty("color$it") != null })
        assertTrue(p.getProperty("background").startsWith("#"))
    }

    @Test
    fun tamanoDeFuenteSeAcota() {
        assertEquals(10, LimitesFuente.acotar(3))
        assertEquals(22, LimitesFuente.acotar(99))
        assertEquals(14, LimitesFuente.acotar(14))
    }

    @Test
    fun scriptDeTerminalNoTieneComentariosYDefineAlias() {
        val s = ScriptTerminal.generar()
        assertTrue(s.contains("alias ll="))
        assertTrue(s.lines().none { it.trimStart().startsWith("#") })
    }

    @Test
    fun entornoApuntaAlScriptYAlProyecto() {
        val base = File("/base")
        val proyecto = File("/base/p")
        val rc = File("/base/.mkshrc")
        val e = ScriptTerminal.entorno(base, proyecto, rc)
        assertTrue(e.contains("ENV=${rc.absolutePath}"))
        assertTrue(e.contains("PWD=${proyecto.absolutePath}"))
        assertTrue(e.none { it.startsWith("LD_PRELOAD") })
    }

    private fun buscarTerminalView(vista: android.view.View): com.termux.view.TerminalView? {
        if (vista is com.termux.view.TerminalView) return vista
        if (vista is android.view.ViewGroup) {
            for (i in 0 until vista.childCount) {
                buscarTerminalView(vista.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    @Test
    fun vistaTerminalInicializaSinNpeYAplicaTipografia() {
        val contexto: Context = ApplicationProvider.getApplicationContext()
        val sesion = SesionPty(1, "sh 1", null, EstadoSesion.Activa)
        val tamanoSp = androidx.compose.runtime.mutableIntStateOf(14)
        var raiz: android.view.View? = null
        compose.setContent {
            raiz = androidx.compose.ui.platform.LocalView.current
            TemaRedactCode {
                VistaTerminal(
                    sesion = sesion,
                    modificadores = ModificadoresTeclas(),
                    tamanoSp = tamanoSp.intValue,
                    alCambiarTamano = { tamanoSp.intValue = LimitesFuente.acotar(it) },
                    modifier = Modifier.size(200.dp)
                )
            }
        }
        compose.waitForIdle()
        val terminalView = buscarTerminalView(raiz!!)
        assertNotNull(terminalView)
        assertEquals("Terminal sh 1", terminalView!!.contentDescription.toString())
        assertNotNull(terminalView.mRenderer)
        val campoTipografia = terminalView.mRenderer.javaClass.getDeclaredField("mTypeface").apply { isAccessible = true }
        val esperado = androidx.core.content.res.ResourcesCompat.getFont(contexto, R.font.jetbrains_mono_regular)
        assertEquals(esperado, campoTipografia.get(terminalView.mRenderer))
        terminalView.mClient.onScale(1.25f)
        compose.waitForIdle()
        assertEquals(15, tamanoSp.intValue)
        assertEquals(esperado, campoTipografia.get(terminalView.mRenderer))
    }

    @Test
    fun falloDeCargaNativaDejaSesionFallidaYMuestraTerminalNoInteractiva() {
        val contexto: Context = ApplicationProvider.getApplicationContext()
        val bd = Room.inMemoryDatabaseBuilder(contexto, BaseDatosRedactCode::class.java).allowMainThreadQueries().build()
        val archivos = GestorArchivos(contexto).apply { raiz.deleteRecursively(); raiz.mkdirs() }
        val proyectos = RepositorioProyectos(bd.proyectoDao(), bd.archivoDao(), archivos)
        val preferencias = RepositorioPreferencias(contexto)
        val credenciales = AlmacenCredenciales(contexto, CifradorKeystore())
        val motor = MotorJavaScriptCompuesto(MotorJavaScriptSandbox(contexto), MotorRhino())
        val servicio = ServicioCodigo(PuentePython(contexto), ServicioJavaScript(motor, motor), ProveedorAnthropic(credenciales, preferencias))
        val clienteGitHub = ClienteGitHub({ null })
        val repoGitHub = RepositorioGitHub(clienteGitHub, credenciales, proyectos)
        val puente = ServicioPuenteTerminal(contexto, servicio, preferencias, clienteGitHub, repoGitHub)
        val gestor = GestorSesionesTerminal(contexto, puente).apply {
            cargadorNativo = { throw UnsatisfiedLinkError("libtermux.so no alineada a 16 KB") }
        }
        var idProyecto = 1L
        try {
            idProyecto = runBlocking { proyectos.crear("PruebaTerminal", "", PlantillaProyecto.VACIO) }
            val entidad = runBlocking { proyectos.obtener(idProyecto) }
            assertNotNull(entidad)
            val carpeta = archivos.carpetaProyecto(entidad!!.carpeta)
            val sesion = gestor.crear(idProyecto, carpeta)
            assertNotNull(sesion)
            assertNull(sesion!!.sesion)
            assertEquals(EstadoSesion.Fallida("libtermux.so no alineada a 16 KB"), sesion.estado.value)

            val vm = TerminalViewModel(SavedStateHandle(mapOf("proyectoId" to idProyecto)), proyectos, servicio, preferencias, gestor)
            compose.setContent {
                TemaRedactCode {
                    PanelTerminalProfesional(vm)
                }
            }
            compose.onNodeWithText(
                "La terminal interactiva no está disponible en este dispositivo (libtermux.so no alineada a 16 KB). Se usa la terminal no interactiva."
            ).assertIsDisplayed()
            compose.onNodeWithContentDescription("Línea de comandos").assertIsDisplayed()
        } finally {
            gestor.cerrarTodas(idProyecto)
            motor.cerrar()
            bd.close()
            archivos.raiz.deleteRecursively()
        }
    }
}
