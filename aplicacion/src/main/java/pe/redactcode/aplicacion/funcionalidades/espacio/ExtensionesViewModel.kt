package pe.redactcode.aplicacion.funcionalidades.espacio

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pe.redactcode.nucleo.extensiones.ClienteOpenVsx
import pe.redactcode.nucleo.extensiones.ExtensionOpenVsx
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.datos.ExtensionInstalada
import pe.redactcode.aplicacion.datos.RepositorioExtensiones
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import javax.inject.Inject

data class EstadoCatalogo(
    val consulta: String = "",
    val resultados: List<ExtensionOpenVsx> = emptyList(),
    val buscando: Boolean = false,
    val instalando: String? = null,
    val error: String? = null
)

@HiltViewModel
class ExtensionesViewModel @Inject constructor(
    private val repositorio: RepositorioExtensiones,
    private val preferenciasRepo: RepositorioPreferencias,
    private val catalogoRemoto: ClienteOpenVsx
) : ViewModel() {
    private val _catalogo = MutableStateFlow(EstadoCatalogo())
    val catalogo: StateFlow<EstadoCatalogo> = _catalogo.asStateFlow()

    val extensiones: StateFlow<List<ExtensionInstalada>> = repositorio.extensiones
    val preferencias: StateFlow<PreferenciasUsuario> = preferenciasRepo.preferencias.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasUsuario())
    private val _mensajes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val mensajes: SharedFlow<String> = _mensajes.asSharedFlow()

    fun instalar(uri: Uri) {
        viewModelScope.launch {
            runCatching { repositorio.instalar(uri) }
                .onSuccess { m ->
                    val avisos = if (m.omitido.isEmpty()) "" else " · ${m.omitido.size} contribución(es) no compatible(s) omitida(s)"
                    _mensajes.tryEmit("Instalada: ${m.nombre} (${m.fragmentos.size} fragmentos, ${m.temas.size} temas)$avisos")
                }
                .onFailure { _mensajes.tryEmit(it.message ?: "No se pudo instalar la extensión") }
        }
    }

    fun buscarCatalogo(consulta: String, categoria: String?) {
        if (consulta.isBlank()) { _catalogo.update { it.copy(resultados = emptyList(), error = null, consulta = "") }; return }
        viewModelScope.launch {
            _catalogo.update { it.copy(buscando = true, error = null, consulta = consulta) }
            runCatching { catalogoRemoto.buscar(consulta, categoria) }
                .onSuccess { r -> _catalogo.update { it.copy(buscando = false, resultados = r) } }
                .onFailure { e -> _catalogo.update { it.copy(buscando = false, error = e.message ?: "No se pudo consultar el catálogo") } }
        }
    }

    fun instalarDelCatalogo(extension: ExtensionOpenVsx) {
        viewModelScope.launch {
            _catalogo.update { it.copy(instalando = extension.identificador) }
            runCatching { repositorio.instalarBytes(catalogoRemoto.descargar(extension)) }
                .onSuccess { m -> _mensajes.tryEmit("Instalada: ${m.nombre} (${m.fragmentos.size} fragmentos, ${m.temas.size} temas)" + if (m.omitido.isEmpty()) "" else " · ${m.omitido.size} contribución(es) no compatible(s) omitida(s)") }
                .onFailure { _mensajes.tryEmit(it.message ?: "No se pudo instalar la extensión") }
            _catalogo.update { it.copy(instalando = null) }
        }
    }

    fun activar(id: String, activa: Boolean) = viewModelScope.launch { preferenciasRepo.extensionActiva(id, activa) }

    fun desinstalar(id: String) {
        viewModelScope.launch {
            runCatching { repositorio.desinstalar(id) }
                .onSuccess { _mensajes.tryEmit("Extensión desinstalada") }
                .onFailure { _mensajes.tryEmit(it.message ?: "No se pudo desinstalar") }
        }
    }

    fun temas() = repositorio.temas()

    fun elegirTema(clave: String) = viewModelScope.launch { preferenciasRepo.temaEditor(clave) }

    fun fragmentosActivos() = repositorio.fragmentosActivos()
}
