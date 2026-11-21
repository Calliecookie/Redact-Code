package pe.redactcode.aplicacion

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.redactcode.aplicacion.datos.GestorCopiasSeguridad
import pe.redactcode.aplicacion.datos.ProveedorAnthropic
import pe.redactcode.aplicacion.datos.RepositorioActividad
import pe.redactcode.aplicacion.datos.RepositorioExtensiones
import pe.redactcode.aplicacion.datos.RepositorioFragmentos
import pe.redactcode.aplicacion.datos.RepositorioGitHub
import pe.redactcode.aplicacion.datos.RepositorioPerfil
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.funcionalidades.ajustes.AjustesViewModel
import pe.redactcode.aplicacion.funcionalidades.ajustes.PantallaAjustes
import pe.redactcode.aplicacion.funcionalidades.bienvenida.BloqueoViewModel
import pe.redactcode.aplicacion.funcionalidades.bienvenida.PantallaBloqueo
import pe.redactcode.aplicacion.funcionalidades.terminal.ServicioPuenteTerminal
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.base_datos.BaseDatosRedactCode
import pe.redactcode.aplicacion.nucleo.diseno.TemaRedactCode
import pe.redactcode.aplicacion.nucleo.ejecucion.MotorJavaScriptCompuesto
import pe.redactcode.aplicacion.nucleo.ejecucion.MotorJavaScriptSandbox
import pe.redactcode.aplicacion.nucleo.ejecucion.PuentePython
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.aplicacion.nucleo.seguridad.AlmacenCredenciales
import pe.redactcode.aplicacion.nucleo.seguridad.AutenticadorBiometrico
import pe.redactcode.aplicacion.nucleo.seguridad.CifradorKeystore
import pe.redactcode.aplicacion.nucleo.seguridad.GestorPin
import pe.redactcode.nucleo.github.ClienteGitHub
import pe.redactcode.nucleo.javascript.MotorRhino
import pe.redactcode.nucleo.javascript.ServicioJavaScript
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private class CifradorMemoria : CifradorKeystore() {
    private val clave = SecretKeySpec(ByteArray(32) { (it + 7).toByte() }, "AES")
    private val transformacion = "AES/GCM/NoPadding"

    override fun cifrar(texto: String): String {
        val cifrador = Cipher.getInstance(transformacion)
        cifrador.init(Cipher.ENCRYPT_MODE, clave)
        val datos = cifrador.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cifrador.iv + datos, Base64.NO_WRAP)
    }

    override fun descifrar(cifrado: String): String {
        val bytes = Base64.decode(cifrado, Base64.NO_WRAP)
        val vector = bytes.copyOfRange(0, 12)
        val datos = bytes.copyOfRange(12, bytes.size)
        val cifrador = Cipher.getInstance(transformacion)
        cifrador.init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(128, vector))
        return String(cifrador.doFinal(datos), Charsets.UTF_8)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PinSeguroTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var contexto: Context
    private lateinit var bd: BaseDatosRedactCode
    private lateinit var archivos: GestorArchivos
    private lateinit var preferencias: RepositorioPreferencias
    private lateinit var almacen: AlmacenCredenciales
    private lateinit var motor: MotorJavaScriptCompuesto

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = Room.inMemoryDatabaseBuilder(contexto, BaseDatosRedactCode::class.java).allowMainThreadQueries().build()
        archivos = GestorArchivos(contexto).apply { raiz.deleteRecursively(); raiz.mkdirs() }
        preferencias = RepositorioPreferencias(contexto)
        almacen = AlmacenCredenciales(contexto, CifradorMemoria())
        motor = MotorJavaScriptCompuesto(MotorJavaScriptSandbox(contexto), MotorRhino())
        runBlocking {
            preferencias.restablecer()
            almacen.eliminarTodo()
        }
    }

    @After
    fun cerrar() {
        motor.cerrar()
        bd.close()
        archivos.raiz.deleteRecursively()
    }

    private fun crearAjustesViewModel(gestorPin: GestorPin): AjustesViewModel {
        val proyectos = RepositorioProyectos(bd.proyectoDao(), bd.archivoDao(), archivos)
        val actividad = RepositorioActividad(bd.actividadDao())
        val fragmentos = RepositorioFragmentos(bd.fragmentoDao(), actividad)
        val perfiles = RepositorioPerfil(bd.perfilDao())
        val copias = GestorCopiasSeguridad(contexto, bd.proyectoDao(), bd.fragmentoDao(), archivos, proyectos, fragmentos)
        val servicio = ServicioCodigo(PuentePython(contexto), ServicioJavaScript(motor, motor), ProveedorAnthropic(almacen, preferencias))
        val extensiones = RepositorioExtensiones(contexto, preferencias)
        val clienteGitHub = ClienteGitHub({ null })
        val repoGitHub = RepositorioGitHub(clienteGitHub, almacen, proyectos)
        val puenteTerminal = ServicioPuenteTerminal(contexto, servicio, preferencias, clienteGitHub, repoGitHub)
        return AjustesViewModel(
            contexto, preferencias, perfiles, gestorPin, AutenticadorBiometrico(contexto),
            almacen, copias, bd, archivos, servicio, extensiones, puenteTerminal
        )
    }

    @Test
    fun gestorPinPersisteBloqueoYConsultaSegundosAlReabrir() = runBlocking {
        var ahora = 1_000_000L
        val gestorInicial = GestorPin(almacen).apply { reloj = { ahora } }
        gestorInicial.establecer("1234")
        assertTrue(gestorInicial.tienePin)
        repeat(5) {
            assertFalse(gestorInicial.verificar("0000"))
        }
        assertEquals(30L, gestorInicial.segundosBloqueo())

        val gestorReabierto = GestorPin(almacen).apply { reloj = { ahora } }
        assertEquals(30L, gestorReabierto.consultarSegundosBloqueo())
        assertFalse(gestorReabierto.verificar("1234"))

        ahora += 30_000L
        assertEquals(0L, gestorReabierto.consultarSegundosBloqueo())
        assertTrue(gestorReabierto.verificar("1234"))
    }

    @Test
    fun quitarPinEnAjustesExigePinCorrectoYComparteIntentosDeBloqueo() = runBlocking {
        var ahora = 2_000_000L
        val gestorPin = GestorPin(almacen).apply { reloj = { ahora } }
        gestorPin.establecer("2468")
        preferencias.bloqueoPin(true)
        preferencias.bloqueoBiometrico(true)
        preferencias.bloquearAlSalir(true)
        val vm = crearAjustesViewModel(gestorPin)

        assertFalse(vm.quitarPin("9999"))
        assertTrue(gestorPin.tienePin)
        assertTrue(preferencias.actuales().bloqueoPin)

        repeat(4) {
            assertFalse(vm.quitarPin("9999"))
        }
        assertEquals(30L, vm.consultarSegundosBloqueo())
        assertFalse(vm.quitarPin("2468"))
        assertTrue(gestorPin.tienePin)

        ahora += 31_000L
        assertEquals(0L, vm.consultarSegundosBloqueo())
        assertTrue(vm.quitarPin("2468"))
        assertFalse(gestorPin.tienePin)
        assertFalse(preferencias.actuales().bloqueoPin)
        assertFalse(preferencias.actuales().bloqueoBiometrico)
        assertFalse(preferencias.actuales().bloquearAlSalir)
        assertEquals("Bloqueo desactivado", vm.mensaje.value)
    }

    @Test
    fun pantallaBloqueoMuestraEsperaInmediataDeshabilitaDesbloquearYPermiteDesbloqueoPosterior() {
        var ahora = 3_000_000L
        val gestorInicial = GestorPin(almacen).apply { reloj = { ahora } }
        runBlocking {
            gestorInicial.establecer("1357")
            repeat(5) { gestorInicial.verificar("0000") }
        }

        val gestorReabierto = GestorPin(almacen).apply { reloj = { ahora } }
        val vm = BloqueoViewModel(gestorReabierto, AutenticadorBiometrico(contexto), preferencias)
        var desbloqueado = false

        compose.setContent {
            TemaRedactCode {
                PantallaBloqueo(usarBiometria = false, alDesbloquear = { desbloqueado = true }, vm = vm)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Demasiados intentos. Espera 30 s.").assertIsDisplayed()
        compose.onNodeWithText("PIN").performTextInput("1357")
        compose.onNodeWithText("Desbloquear").assertIsNotEnabled()

        ahora += 31_000L
        compose.mainClock.advanceTimeBy(1_500L)
        compose.waitForIdle()
        compose.onNodeWithText("Desbloquear").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { desbloqueado }
        assertTrue(desbloqueado)
    }

    @Test
    fun dialogoDesactivarPinEnPantallaAjustesPidePinActualYCancelarConservaElPin() {
        val gestorPin = GestorPin(almacen)
        runBlocking {
            gestorPin.establecer("4321")
            preferencias.bloqueoPin(true)
        }
        val vm = crearAjustesViewModel(gestorPin)

        compose.setContent {
            TemaRedactCode {
                PantallaAjustes(alAbrirGitHub = {}, alReiniciar = {}, vm = vm)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Privacidad y seguridad").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bloqueo con PIN").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Desactivar PIN").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()
        assertTrue(runBlocking { preferencias.actuales().bloqueoPin })
        assertTrue(gestorPin.tienePin)

        compose.onNodeWithText("Bloqueo con PIN").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("PIN actual").performTextInput("0000")
        compose.onNodeWithText("Desactivar").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("PIN incorrecto")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("PIN incorrecto").assertIsDisplayed()
        assertTrue(runBlocking { preferencias.actuales().bloqueoPin })
        assertTrue(gestorPin.tienePin)

        compose.onNodeWithText("PIN actual").performTextInput("4321")
        compose.onNodeWithText("Desactivar").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Desactivar PIN")).fetchSemanticsNodes().isEmpty()
        }
        assertFalse(runBlocking { preferencias.actuales().bloqueoPin })
        assertFalse(gestorPin.tienePin)
    }

    @Test
    fun cambiarPinEnAjustesViewModelExigePinActualYActivaBloqueoCompartido() = runBlocking {
        var ahora = 4_000_000L
        val gestorPin = GestorPin(almacen).apply { reloj = { ahora } }
        val vm = crearAjustesViewModel(gestorPin)

        assertFalse(vm.tienePin)
        assertTrue(vm.establecerPin("1234"))
        assertTrue(vm.tienePin)
        assertTrue(preferencias.actuales().bloqueoPin)
        assertTrue(gestorPin.verificar("1234"))

        assertFalse(vm.establecerPin("5678", "0000"))
        assertTrue(gestorPin.verificar("1234"))
        assertFalse(gestorPin.verificar("5678"))

        repeat(4) {
            assertFalse(vm.establecerPin("5678", "0000"))
        }
        assertEquals(30L, vm.consultarSegundosBloqueo())
        assertFalse(vm.establecerPin("5678", "1234"))
        assertFalse(vm.quitarPin("1234"))

        ahora += 31_000L
        assertEquals(0L, vm.consultarSegundosBloqueo())
        assertTrue(vm.establecerPin("5678", "1234"))
        assertFalse(gestorPin.verificar("1234"))
        assertTrue(gestorPin.verificar("5678"))
    }

    @Test
    fun dialogoPinPermiteActivarPorPrimeraVezYExigePinActualAlCambiar() {
        var ahora = 5_000_000L
        val gestorPin = GestorPin(almacen).apply { reloj = { ahora } }
        val vm = crearAjustesViewModel(gestorPin)

        compose.setContent {
            TemaRedactCode {
                PantallaAjustes(alAbrirGitHub = {}, alReiniciar = {}, vm = vm)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Privacidad y seguridad").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bloqueo con PIN").performClick()
        compose.waitForIdle()

        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("PIN actual")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("PIN (4 a 8 dígitos)").performTextInput("1234")
        compose.onNodeWithText("Confirmar PIN").performTextInput("1234")
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Establecer PIN")).fetchSemanticsNodes().isEmpty()
        }
        assertTrue(gestorPin.tienePin)
        assertTrue(runBlocking { preferencias.actuales().bloqueoPin })

        compose.onNodeWithText("Cambiar PIN").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(
            androidx.compose.ui.test.hasText("Cambiar PIN") and
                androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())
        ).assertIsDisplayed()
        compose.onNodeWithText("PIN actual").assertIsDisplayed()
        compose.onNodeWithText("PIN (4 a 8 dígitos)").performTextInput("5678")
        compose.onNodeWithText("Confirmar PIN").performTextInput("5678")
        compose.onNodeWithText("Guardar").assertIsNotEnabled()

        compose.onNodeWithText("PIN actual").performTextInput("0000")
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("PIN incorrecto")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("PIN incorrecto").assertIsDisplayed()
        assertTrue(runBlocking { gestorPin.verificar("1234") })

        repeat(5) {
            compose.onNodeWithText("PIN actual").performTextInput("0000")
            compose.onNodeWithText("Guardar").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("PIN incorrecto")).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodes(androidx.compose.ui.test.hasText("Demasiados intentos. Espera 30 s.")).fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.onNodeWithText("Demasiados intentos. Espera 30 s.").assertIsDisplayed()
        compose.onNodeWithText("PIN actual").performTextInput("1234")
        compose.onNodeWithText("Guardar").assertIsNotEnabled()

        ahora += 31_000L
        compose.mainClock.advanceTimeBy(1_500L)
        compose.waitForIdle()
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText("Cambiar PIN") and
                    androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())
            ).fetchSemanticsNodes().isEmpty()
        }
        assertFalse(runBlocking { gestorPin.verificar("1234") })
        assertTrue(runBlocking { gestorPin.verificar("5678") })
    }
}
