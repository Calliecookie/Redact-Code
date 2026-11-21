package pe.redactcode.aplicacion.funcionalidades.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.datos.RepositorioActividad
import pe.redactcode.aplicacion.datos.RepositorioAprendizaje
import pe.redactcode.aplicacion.datos.RepositorioFragmentos
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.funcionalidades.mentor.SolicitudMentorCompartida
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoEntidad
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.nucleo.mentor.CorrectorAutomatico
import pe.redactcode.nucleo.mentor.PropuestaCorreccion
import pe.redactcode.nucleo.mentor.TipoSolicitud
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.EstadoEjecucion
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.ResultadoEjecucion
import pe.redactcode.nucleo.revision.InformeRevision
import pe.redactcode.nucleo.validacion.ClasificadorMensajes
import javax.inject.Inject

data class Pestana(
    val ruta: String,
    val textoGuardado: String,
    val textoActual: String
) {
    val nombre: String get() = ruta.substringAfterLast('/')
    val lenguaje: Lenguaje get() = Lenguaje.desdeNombreArchivo(nombre)
    val modificada: Boolean get() = textoGuardado != textoActual
}

enum class PanelEditor { NINGUNO, PROBLEMAS, EJECUCION, REVISION, VISTA_PREVIA }

data class LineaConsola(val nivel: String, val mensaje: String, val linea: Int)

data class EstadoEditor(
    val proyecto: ProyectoEntidad? = null,
    val pestanas: List<Pestana> = emptyList(),
    val activa: String? = null,
    val diagnosticos: Map<String, List<Diagnostico>> = emptyMap(),
    val validando: Boolean = false,
    val duracionValidacionMs: Long = 0,
    val motorValidacion: String = "",
    val cursor: PosicionCursor = PosicionCursor(1, 1, ""),
    val panel: PanelEditor = PanelEditor.NINGUNO,
    val ejecutando: Boolean = false,
    val resultado: ResultadoEjecucion? = null,
    val entradaPrograma: String = "",
    val revisando: Boolean = false,
    val revision: InformeRevision? = null,
    val archivoVistaPrevia: String? = null,
    val versionVistaPrevia: Int = 0,
    val consolaWeb: List<LineaConsola> = emptyList(),
    val archivosProyecto: List<String> = emptyList(),
    val mensaje: String? = null,
    val cargando: Boolean = true,
    val error: String? = null
) {
    val pestanaActiva: Pestana? get() = pestanas.firstOrNull { it.ruta == activa }
    val diagnosticosActivos: List<Diagnostico> get() = activa?.let { diagnosticos[it] }.orEmpty()
    val hayCambiosSinGuardar: Boolean get() = pestanas.any { it.modificada }
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val proyectos: RepositorioProyectos,
    private val servicio: ServicioCodigo,
    private val actividad: RepositorioActividad,
    private val aprendizaje: RepositorioAprendizaje,
    private val fragmentos: RepositorioFragmentos,
    repositorioPreferencias: RepositorioPreferencias
) : ViewModel() {
    private val proyectoId: Long = checkNotNull(estadoGuardado.get<Long>("proyectoId"))
    private val rutaInicial: String? = estadoGuardado.get<String>("ruta")?.takeIf { it.isNotBlank() }

    private val _estado = MutableStateFlow(EstadoEditor())
    val estado: StateFlow<EstadoEditor> = _estado.asStateFlow()

    val preferencias: StateFlow<PreferenciasUsuario> = repositorioPreferencias.preferencias.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasUsuario())

    private var trabajoValidacion: Job? = null
    private var trabajoEjecucion: Job? = null
    private var ultimaEdicion = 0L

    init {
        viewModelScope.launch {
            val proyecto = proyectos.obtener(proyectoId)
            if (proyecto == null) {
                _estado.update { it.copy(cargando = false, error = "El proyecto ya no existe") }
                return@launch
            }
            val archivos = runCatching { proyectos.archivos.listarArchivos(proyecto.carpeta) }.getOrDefault(emptyList())
            _estado.update { it.copy(proyecto = proyecto, archivosProyecto = archivos, cargando = false) }
            val ruta = rutaInicial ?: archivos.firstOrNull { it == "main.py" || it == "index.html" || it == "app.js" } ?: archivos.firstOrNull()
            if (ruta != null) abrir(ruta)
        }
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                if (System.currentTimeMillis() - ultimaEdicion < 120_000) actividad.registrarTiempo(30)
            }
        }
    }

    fun abrir(ruta: String) {
        val proyecto = _estado.value.proyecto ?: return
        if (_estado.value.pestanas.any { it.ruta == ruta }) {
            _estado.update { it.copy(activa = ruta) }
            return
        }
        viewModelScope.launch {
            try {
                val texto = proyectos.leer(proyecto, ruta)
                proyectos.registrarApertura(proyecto, ruta)
                _estado.update { it.copy(pestanas = it.pestanas + Pestana(ruta, texto, texto), activa = ruta) }
                validarAhora(ruta)
            } catch (e: Exception) {
                mensaje(e.message ?: "No se pudo abrir el archivo")
            }
        }
    }

    fun activar(ruta: String) = _estado.update { it.copy(activa = ruta) }

    fun cerrarPestana(ruta: String) {
        _estado.update { e ->
            val restantes = e.pestanas.filterNot { it.ruta == ruta }
            e.copy(pestanas = restantes, activa = if (e.activa == ruta) restantes.lastOrNull()?.ruta else e.activa, diagnosticos = e.diagnosticos - ruta)
        }
    }

    fun alCambiarTexto(ruta: String, texto: String) {
        ultimaEdicion = System.currentTimeMillis()
        _estado.update { e -> e.copy(pestanas = e.pestanas.map { if (it.ruta == ruta) it.copy(textoActual = texto) else it }) }
        if (preferencias.value.validacionEnVivo) {
            trabajoValidacion?.cancel()
            trabajoValidacion = viewModelScope.launch {
                delay(400)
                validarAhora(ruta)
            }
        }
    }

    fun alMoverCursor(posicion: PosicionCursor) = _estado.update { it.copy(cursor = posicion) }

    private suspend fun validarAhora(ruta: String) {
        val pestana = _estado.value.pestanas.firstOrNull { it.ruta == ruta } ?: return
        _estado.update { it.copy(validando = true) }
        val resultado = servicio.validar(pestana.lenguaje, pestana.textoActual)
        _estado.update { it.copy(validando = false, diagnosticos = it.diagnosticos + (ruta to resultado.diagnosticos), duracionValidacionMs = resultado.duracionMs, motorValidacion = resultado.motor) }
    }

    fun validar() {
        val ruta = _estado.value.activa ?: return
        viewModelScope.launch { validarAhora(ruta) }
    }

    fun guardar(ruta: String? = _estado.value.activa, silencioso: Boolean = false) {
        val proyecto = _estado.value.proyecto ?: return
        val pestana = _estado.value.pestanas.firstOrNull { it.ruta == ruta } ?: return
        viewModelScope.launch {
            guardarInterno(proyecto, pestana)
            if (!silencioso) mensaje("${pestana.nombre} guardado")
        }
    }

    private suspend fun guardarInterno(proyecto: ProyectoEntidad, pestana: Pestana) {
        try {
            proyectos.guardar(proyecto, pestana.ruta, pestana.textoActual)
            _estado.update { e -> e.copy(pestanas = e.pestanas.map { if (it.ruta == pestana.ruta) it.copy(textoGuardado = pestana.textoActual) else it }) }
            actividad.registrarGuardado()
            aprendizaje.registrarDiagnosticos(_estado.value.diagnosticos[pestana.ruta].orEmpty(), "${proyecto.id}:${pestana.ruta}")
        } catch (e: Exception) {
            mensaje(e.message ?: "No se pudo guardar")
        }
    }

    suspend fun guardarTodo() {
        val proyecto = _estado.value.proyecto ?: return
        _estado.value.pestanas.filter { it.modificada }.forEach { guardarInterno(proyecto, it) }
    }

    fun mostrarPanel(panel: PanelEditor) = _estado.update { it.copy(panel = if (it.panel == panel) PanelEditor.NINGUNO else panel) }
    fun cerrarPanel() = _estado.update { it.copy(panel = PanelEditor.NINGUNO) }
    fun cambiarEntrada(texto: String) = _estado.update { it.copy(entradaPrograma = texto) }

    fun ejecutar() {
        val e = _estado.value
        val pestana = e.pestanaActiva ?: return
        val proyecto = e.proyecto ?: return
        when (pestana.lenguaje) {
            Lenguaje.HTML, Lenguaje.CSS -> abrirVistaPrevia()
            Lenguaje.JAVASCRIPT -> viewModelScope.launch {
                val usadoPorHtml = e.archivosProyecto.filter { it.endsWith(".html") }.any { html ->
                    runCatching { proyectos.leer(proyecto, html) }.getOrDefault("").contains(pestana.nombre)
                }
                if (usadoPorHtml) abrirVistaPrevia() else ejecutarCodigo(pestana, proyecto)
            }
            Lenguaje.PYTHON -> ejecutarCodigo(pestana, proyecto)
            else -> mensaje("Los archivos ${pestana.lenguaje.nombreVisible} no se ejecutan; se validan automáticamente")
        }
    }

    fun ejecutarComoScript() {
        val e = _estado.value
        val pestana = e.pestanaActiva ?: return
        val proyecto = e.proyecto ?: return
        ejecutarCodigo(pestana, proyecto)
    }

    private fun ejecutarCodigo(pestana: Pestana, proyecto: ProyectoEntidad) {
        trabajoEjecucion?.cancel()
        trabajoEjecucion = viewModelScope.launch {
            _estado.update { it.copy(ejecutando = true, panel = PanelEditor.EJECUCION, resultado = ResultadoEjecucion(EstadoEjecucion.EN_CURSO)) }
            val validacion = servicio.validar(pestana.lenguaje, pestana.textoActual)
            _estado.update { it.copy(diagnosticos = it.diagnosticos + (pestana.ruta to validacion.diagnosticos)) }
            if (!validacion.esValido) {
                val primero = validacion.errores.first()
                aprendizaje.registrarDiagnosticos(validacion.errores, "${proyecto.id}:${pestana.ruta}", "EJECUCION")
                _estado.update {
                    it.copy(ejecutando = false, resultado = ResultadoEjecucion(EstadoEjecucion.ERROR, "", "${primero.titulo} (línea ${primero.linea}): ${primero.mensajeOriginal}", primero.tipo, primero.linea))
                }
                return@launch
            }
            if (pestana.modificada) guardarInterno(proyecto, pestana)
            val limite = preferencias.value.limiteEjecucionSegundos
            val resultado = when (pestana.lenguaje) {
                Lenguaje.PYTHON -> servicio.ejecutarPython(pestana.textoActual, _estado.value.entradaPrograma, limite)
                else -> servicio.ejecutarJavaScript(pestana.textoActual, limite)
            }
            actividad.registrarEjecucion()
            if (resultado.estado == EstadoEjecucion.ERROR || resultado.estado == EstadoEjecucion.TIEMPO_AGOTADO) {
                val excepcion = resultado.excepcion ?: if (resultado.estado == EstadoEjecucion.TIEMPO_AGOTADO) "TiempoAgotado" else "Error"
                val codigo = if (pestana.lenguaje == Lenguaje.PYTHON) ClasificadorMensajes.excepcionPython(excepcion) else ClasificadorMensajes.excepcionJavaScript(excepcion)
                aprendizaje.registrarCodigo(codigo, pestana.lenguaje, "EJECUCION")
            }
            _estado.update { it.copy(ejecutando = false, resultado = resultado) }
        }
    }

    fun detenerEjecucion() {
        servicio.cancelarPython()
        trabajoEjecucion?.cancel()
        _estado.update { it.copy(ejecutando = false, resultado = (it.resultado ?: ResultadoEjecucion(EstadoEjecucion.CANCELADO)).copy(estado = EstadoEjecucion.CANCELADO)) }
    }

    fun abrirVistaPrevia() {
        val e = _estado.value
        viewModelScope.launch {
            guardarTodo()
            val activa = e.pestanaActiva?.ruta
            val html = when {
                activa != null && activa.endsWith(".html") -> activa
                e.archivosProyecto.contains("index.html") -> "index.html"
                else -> e.archivosProyecto.firstOrNull { it.endsWith(".html") }
            }
            if (html == null) {
                mensaje("Crea un archivo .html para ver la vista previa")
                return@launch
            }
            _estado.update { it.copy(archivoVistaPrevia = html, panel = PanelEditor.VISTA_PREVIA, versionVistaPrevia = it.versionVistaPrevia + 1, consolaWeb = emptyList()) }
        }
    }

    fun recargarVistaPrevia() {
        viewModelScope.launch {
            guardarTodo()
            _estado.update { it.copy(versionVistaPrevia = it.versionVistaPrevia + 1, consolaWeb = emptyList()) }
        }
    }

    fun agregarConsola(linea: LineaConsola) = _estado.update { it.copy(consolaWeb = (it.consolaWeb + linea).takeLast(300)) }

    fun revisar() {
        val pestana = _estado.value.pestanaActiva ?: return
        viewModelScope.launch {
            _estado.update { it.copy(revisando = true, panel = PanelEditor.REVISION) }
            val diagnosticos = servicio.validar(pestana.lenguaje, pestana.textoActual).diagnosticos
            val informe = runCatching { servicio.revisar(pestana.lenguaje, pestana.textoActual, diagnosticos) }.getOrNull()
            _estado.update { it.copy(revisando = false, revision = informe) }
        }
    }

    fun propuesta(diagnostico: Diagnostico): PropuestaCorreccion? {
        val pestana = _estado.value.pestanaActiva ?: return null
        return CorrectorAutomatico.proponer(diagnostico, pestana.textoActual)
    }

    fun aplicarPropuesta(propuesta: PropuestaCorreccion): String? {
        val pestana = _estado.value.pestanaActiva ?: return null
        val lineas = pestana.textoActual.split("\n").toMutableList()
        if (propuesta.linea - 1 !in lineas.indices) return null
        lineas[propuesta.linea - 1] = propuesta.corregida
        return lineas.joinToString("\n")
    }

    fun prepararMentor(tipo: TipoSolicitud, diagnostico: Diagnostico? = null) {
        val e = _estado.value
        val pestana = e.pestanaActiva ?: return
        val codigo = e.cursor.seleccion.ifBlank { pestana.textoActual }
        SolicitudMentorCompartida.establecer(tipo, pestana.lenguaje, codigo, diagnostico, e.revision?.hallazgos.orEmpty())
    }

    fun guardarFragmento(titulo: String, etiquetas: String) {
        val e = _estado.value
        val pestana = e.pestanaActiva ?: return
        val codigo = e.cursor.seleccion.ifBlank { pestana.textoActual }
        viewModelScope.launch {
            runCatching { fragmentos.guardar(null, titulo, "Guardado desde ${pestana.nombre}", codigo, pestana.lenguaje, etiquetas.split(','), true) }
                .onSuccess { mensaje("Fragmento guardado en tu biblioteca") }
                .onFailure { mensaje(it.message ?: "No se pudo guardar el fragmento") }
        }
    }

    fun refrescarArchivos() {
        val proyecto = _estado.value.proyecto ?: return
        viewModelScope.launch {
            val archivos = runCatching { proyectos.archivos.listarArchivos(proyecto.carpeta) }.getOrDefault(emptyList())
            _estado.update { it.copy(archivosProyecto = archivos) }
        }
    }

    fun crearArchivo(nombre: String) {
        val proyecto = _estado.value.proyecto ?: return
        viewModelScope.launch {
            runCatching { proyectos.crearArchivo(proyecto, "", nombre) }
                .onSuccess { ruta -> refrescarArchivos(); abrir(ruta) }
                .onFailure { mensaje(it.message ?: "No se pudo crear el archivo") }
        }
    }

    fun mensaje(texto: String?) = _estado.update { it.copy(mensaje = texto) }

    fun carpetaRaiz(): java.io.File = proyectos.archivos.raiz
}
