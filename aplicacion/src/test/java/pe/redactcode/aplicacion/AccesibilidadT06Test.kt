package pe.redactcode.aplicacion

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.funcionalidades.explorador.PantallaProyectos
import pe.redactcode.aplicacion.funcionalidades.explorador.ProyectosViewModel
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.base_datos.ArchivoDao
import pe.redactcode.aplicacion.nucleo.base_datos.ArchivoEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.ConteoLenguaje
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoConArchivos
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoDao
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoEntidad
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.TemaRedactCode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccesibilidadT06Test {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun etiquetaSeleccionableExponeEstadoYRol() {
        var elegido by mutableStateOf("Oscuro")
        compose.setContent {
            TemaRedactCode {
                Row {
                    Etiqueta("Oscuro", seleccionada = elegido == "Oscuro", alPulsar = { elegido = "Oscuro" })
                    Etiqueta("Claro", seleccionada = elegido == "Claro", alPulsar = { elegido = "Claro" })
                    Etiqueta("Solo lectura")
                }
            }
        }
        compose.onNodeWithText("Oscuro")
            .assertIsSelectable()
            .assertIsSelected()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        compose.onNodeWithText("Claro")
            .assertIsSelectable()
            .assertIsNotSelected()
            .assertHasClickAction()
        compose.onNodeWithText("Solo lectura")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))

        compose.onNodeWithText("Claro").performClick()
        compose.onNodeWithText("Claro").assertIsSelected()
        compose.onNodeWithText("Oscuro").assertIsNotSelected()
    }

    @Test
    fun botonNuevoProyectoExponeDescripcionEnNodoPulsable() {
        val contexto = ApplicationProvider.getApplicationContext<Application>()
        val vm = ProyectosViewModel(
            contexto,
            RepositorioProyectos(ProyectoDaoFalso(), ArchivoDaoFalso(), GestorArchivos(contexto))
        )
        compose.setContent {
            TemaRedactCode {
                PantallaProyectos(alAbrirProyecto = {}, vm = vm)
            }
        }
        compose.onNode(hasContentDescription("Nuevo proyecto").and(hasClickAction()))
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Plantilla").assertIsDisplayed()
    }

    private class ProyectoDaoFalso : ProyectoDao {
        override fun observarTodos(): Flow<List<ProyectoEntidad>> = flowOf(emptyList())
        override suspend fun todos(): List<ProyectoEntidad> = emptyList()
        override fun observar(id: Long): Flow<ProyectoEntidad?> = flowOf(null)
        override suspend fun obtener(id: Long): ProyectoEntidad? = null
        override suspend fun porProyectoGuiado(guiadoId: String): ProyectoEntidad? = null
        override fun observarGuiados(): Flow<List<ProyectoEntidad>> = flowOf(emptyList())
        override suspend fun contarCarpeta(carpeta: String): Int = 0
        override suspend fun insertar(proyecto: ProyectoEntidad): Long = 1L
        override suspend fun actualizar(proyecto: ProyectoEntidad) = Unit
        override suspend fun tocar(id: Long, momento: Long) = Unit
        override suspend fun eliminar(proyecto: ProyectoEntidad) = Unit
        override suspend fun conArchivos(id: Long): ProyectoConArchivos? = null
    }

    private class ArchivoDaoFalso : ArchivoDao {
        override suspend fun obtener(proyectoId: Long, ruta: String): ArchivoEntidad? = null
        override fun observarRecientes(limite: Int): Flow<List<ArchivoEntidad>> = flowOf(emptyList())
        override suspend fun guardar(archivo: ArchivoEntidad) = Unit
        override suspend fun eliminarRuta(proyectoId: Long, ruta: String, prefijo: String) = Unit
        override suspend fun delProyecto(proyectoId: Long): List<ArchivoEntidad> = emptyList()
        override suspend fun conteoPorLenguaje(): List<ConteoLenguaje> = emptyList()
    }
}
