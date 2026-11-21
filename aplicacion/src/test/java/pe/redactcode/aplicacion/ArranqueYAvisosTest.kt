package pe.redactcode.aplicacion

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pe.redactcode.aplicacion.datos.ProveedorAnthropic
import pe.redactcode.aplicacion.datos.RepositorioPerfil
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.funcionalidades.depuracion.DepuradorViewModel
import pe.redactcode.aplicacion.navegacion.AccesoViewModel
import pe.redactcode.aplicacion.navegacion.EstadoAcceso
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.base_datos.BaseDatosRedactCode
import pe.redactcode.aplicacion.nucleo.ejecucion.MotorJavaScriptCompuesto
import pe.redactcode.aplicacion.nucleo.ejecucion.MotorJavaScriptSandbox
import pe.redactcode.aplicacion.nucleo.ejecucion.PuentePython
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.aplicacion.nucleo.seguridad.AlmacenCredenciales
import pe.redactcode.aplicacion.nucleo.seguridad.CifradorKeystore
import pe.redactcode.nucleo.depuracion.FaseSesion
import pe.redactcode.nucleo.javascript.MotorRhino
import pe.redactcode.nucleo.javascript.ServicioJavaScript
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.NivelExperiencia
import java.io.File
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArranqueYAvisosTest {
    private lateinit var contexto: Context
    private lateinit var bd: BaseDatosRedactCode
    private lateinit var archivos: GestorArchivos
    private lateinit var preferencias: RepositorioPreferencias
    private lateinit var motor: MotorJavaScriptCompuesto

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = Room.inMemoryDatabaseBuilder(contexto, BaseDatosRedactCode::class.java).allowMainThreadQueries().build()
        archivos = GestorArchivos(contexto).apply { raiz.deleteRecursively(); raiz.mkdirs() }
        preferencias = RepositorioPreferencias(contexto)
        motor = MotorJavaScriptCompuesto(MotorJavaScriptSandbox(contexto), MotorRhino())
        runBlocking { preferencias.restablecer() }
    }

    @After
    fun cerrar() {
        motor.cerrar()
        bd.close()
        archivos.raiz.deleteRecursively()
    }

    private fun crearDepurador(): DepuradorViewModel {
        val proyectos = RepositorioProyectos(bd.proyectoDao(), bd.archivoDao(), archivos)
        val credenciales = AlmacenCredenciales(contexto, CifradorKeystore())
        val servicio = ServicioCodigo(PuentePython(contexto), ServicioJavaScript(motor, motor), ProveedorAnthropic(credenciales, preferencias))
        return DepuradorViewModel(SavedStateHandle(mapOf("proyectoId" to 1L)), proyectos, servicio, preferencias)
    }

    private fun esperarEstado(vm: AccesoViewModel, condicion: (EstadoAcceso) -> Boolean): EstadoAcceso {
        val limite = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < limite) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condicion(vm.estado.value)) return vm.estado.value
            Thread.sleep(20)
        }
        return vm.estado.value
    }

    private fun leerFuenteEspacio(): String {
        val ruta = "src/main/java/pe/redactcode/aplicacion/funcionalidades/espacio/PantallaEspacio.kt"
        val archivo = File(ruta).takeIf { it.exists() } ?: File("aplicacion/$ruta")
        return archivo.readText()
    }

    @Test
    fun depuradorEmiteElAvisoCuandoElArchivoNoEsPython() {
        val vm = crearDepurador()
        val aviso = runBlocking {
            val recibido = async(start = CoroutineStart.UNDISPATCHED) { vm.mensajes.first() }
            vm.iniciar("index.html", Lenguaje.HTML, "<p>hola</p>", true)
            withTimeout(5.seconds) { recibido.await() }
        }
        assertEquals("La ejecución interactiva y la depuración solo están disponibles para Python", aviso)
        assertFalse(vm.activa)
        assertEquals(FaseSesion.INACTIVA, vm.estado.value.fase)
    }

    @Test
    fun elEspacioEscuchaLosMensajesDelDepurador() {
        val fuente = leerFuenteEspacio()
        val coincidencia = Regex("merge\\(([^)]*explorador\\.mensajes[^)]*)\\)\\.collect").find(fuente)
        assertTrue("No se encontró el merge de mensajes en PantallaEspacio", coincidencia != null)
        val fuentes = coincidencia!!.groupValues[1].split(",").map { it.trim() }
        assertTrue("Falta depurador.mensajes en $fuentes", "depurador.mensajes" in fuentes)
        listOf("explorador", "busqueda", "control", "terminal", "agente", "extVm").forEach {
            assertTrue("Falta $it.mensajes en $fuentes", "$it.mensajes" in fuentes)
        }
    }

    @Test
    fun accesoSaleDeCargandoSinPerfilYPasaABienvenida() {
        val vm = AccesoViewModel(preferencias, RepositorioPerfil(bd.perfilDao()))
        val estado = esperarEstado(vm) { it != EstadoAcceso.Cargando }
        assertEquals(EstadoAcceso.Bienvenida, estado)
    }

    @Test
    fun accesoLibreConPerfilYBienvenidaCompletada() {
        runBlocking {
            RepositorioPerfil(bd.perfilDao()).crear("QA", NivelExperiencia.PRINCIPIANTE, listOf(Lenguaje.PYTHON))
            preferencias.bienvenidaCompletada(true)
        }
        val vm = AccesoViewModel(preferencias, RepositorioPerfil(bd.perfilDao()))
        val estado = esperarEstado(vm) { it != EstadoAcceso.Cargando }
        assertEquals(EstadoAcceso.Libre, estado)
    }

    @Test
    fun accesoBloqueadoConPinYSeLiberaAlDesbloquear() {
        runBlocking {
            RepositorioPerfil(bd.perfilDao()).crear("QA", NivelExperiencia.PRINCIPIANTE, listOf(Lenguaje.PYTHON))
            preferencias.bienvenidaCompletada(true)
            preferencias.bloqueoPin(true)
        }
        val vm = AccesoViewModel(preferencias, RepositorioPerfil(bd.perfilDao()))
        assertTrue(esperarEstado(vm) { it != EstadoAcceso.Cargando } is EstadoAcceso.Bloqueado)
        vm.desbloquear()
        assertEquals(EstadoAcceso.Libre, esperarEstado(vm) { it == EstadoAcceso.Libre })
        vm.bloquear()
        assertTrue(esperarEstado(vm) { it is EstadoAcceso.Bloqueado } is EstadoAcceso.Bloqueado)
    }

    @Test
    fun laAplicacionNoConstruyeRetosNiGitHubEnElHiloPrincipalAlArrancar() {
        assertEquals(dagger.Lazy::class.java, RedactCodeAplicacion::class.java.getDeclaredField("retos").type)
        assertEquals(dagger.Lazy::class.java, RedactCodeAplicacion::class.java.getDeclaredField("github").type)
    }
}
