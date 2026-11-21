package pe.redactcode.aplicacion.funcionalidades.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import pe.redactcode.aplicacion.nucleo.preferencias.GuardadoAutomatico
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.nucleo.ide.Diferencias
import pe.redactcode.nucleo.ide.ExtractorSimbolos
import pe.redactcode.nucleo.ide.Formateador
import pe.redactcode.nucleo.ide.ResultadoDiferencias
import pe.redactcode.nucleo.ide.SimboloCodigo
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

enum class PanelEditor(val titulo: String) {
    NINGUNO(""), PROBLEMAS("Problemas"), EJECUCION("Salida"), TERMINAL("Terminal"), CONSOLA("Consola"), REVISION("Revisión"), VISTA_PREVIA("Vista previa")
}

data class CambioExterno(val ruta: String, val texto: String, val deshacible: Boolean)

data class Comparacion(val izquierda: String, val derecha: String, val resultado: ResultadoDiferencias)

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
    val comparacion: Comparacion? = null,
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
    private val repositorioPreferencias: RepositorioPreferencias
) : ViewModel() {
    private val proyectoId: Long = checkNotNull(estadoGuardado.get<Long>("proyectoId"))
    private val rutaInicial: String? = estadoGuardado.get<String>("ruta")?.takeIf { it.isNotBlank() }

    private val _estado = MutableStateFlow(EstadoEditor())
    val estado: StateFlow<EstadoEditor> = _estado.asStateFlow()

    val preferencias: StateFlow<PreferenciasUsuario> = repositorioPreferencias.preferencias.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasUsuario())

    private val _cambiosExternos = MutableSharedFlow<CambioExterno>(extraBufferCapacity = 64)
    val cambiosExternos: SharedFlow<CambioExterno> = _cambiosExternos.asSharedFlow()
    private var trabajoGuardado: Job? = null
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
            repositorioPreferencias.ultimoProyecto(proyecto.id)
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
        val extension = ruta.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty() && extension !in Lenguaje.extensionesEditables) {
            mensaje("«${ruta.substringAfterLast('/')}» no es un archivo de texto editable")
            return
        }
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

    fun activar(ruta: String) {
        val anterior = _estado.value.activa
        if (anterior != null && anterior != ruta && preferencias.value.guardadoAutomatico != GuardadoAutomatico.DESACTIVADO) {
            _estado.value.pestanas.firstOrNull { it.ruta == anterior && it.modificada }?.let { guardar(it.ruta, silencioso = true) }
        }
        _estado.update { it.copy(activa = ruta) }
    }

    fun recargarDesdeDisco(rutas: Collection<String>? = null) {
        val proyecto = _estado.value.proyecto ?: return
        viewModelScope.launch {
            val archivos = runCatching { proyectos.archivos.listarArchivos(proyecto.carpeta) }.getOrDefault(emptyList())
            _estado.update { it.copy(archivosProyecto = archivos) }
            var conflictos = 0
            _estado.value.pestanas.filter { rutas == null || it.ruta in rutas }.forEach { p ->
                if (p.ruta !in archivos) {
                    if (!p.modificada) cerrarPestana(p.ruta)
                    return@forEach
                }
                val texto = runCatching { proyectos.leer(proyecto, p.ruta) }.getOrNull() ?: return@forEach
                if (texto == p.textoGuardado) return@forEach
                if (p.modificada) { conflictos++; return@forEach }
                _estado.update { e -> e.copy(pestanas = e.pestanas.map { if (it.ruta == p.ruta) it.copy(textoGuardado = texto, textoActual = texto) else it }) }
                _cambiosExternos.emit(CambioExterno(p.ruta, texto, deshacible = false))
                validarAhora(p.ruta)
            }
            if (conflictos > 0) mensaje("$conflictos archivo(s) cambiaron en disco pero tienen cambios sin guardar; se conservó tu versión")
        }
    }

    fun rutaRenombrada(anterior: String, nueva: String) {
        _estado.update { e ->
            fun mapear(r: String) = when {
                r == anterior -> nueva
                r.startsWith("$anterior/") -> nueva + r.removePrefix(anterior)
                else -> r
            }
            e.copy(
                pestanas = e.pestanas.map { it.copy(ruta = mapear(it.ruta)) },
                activa = e.activa?.let(::mapear),
                diagnosticos = e.diagnosticos.mapKeys { mapear(it.key) }
            )
        }
        refrescarArchivos()
    }

    fun rutaEliminada(ruta: String): List<String> {
        val afectadas = _estado.value.pestanas.map { it.ruta }.filter { it == ruta || it.startsWith("$ruta/") }
        afectadas.forEach(::cerrarPestana)
        refrescarArchivos()
        return afectadas
    }

    fun formatear(): Boolean {
        val pestana = _estado.value.pestanaActiva ?: return false
        val r = Formateador.formatear(pestana.lenguaje, pestana.textoActual, preferencias.value.sangriaEspacios)
        when {
            r.error != null -> mensaje(r.error)
            !r.cambiado -> mensaje("El documento ya tiene el formato correcto")
            else -> {
                _cambiosExternos.tryEmit(CambioExterno(pestana.ruta, r.texto, deshacible = true))
                mensaje(r.metodo)
            }
        }
        return r.cambiado
    }

    fun simbolosActivos(): List<SimboloCodigo> = _estado.value.pestanaActiva?.let { ExtractorSimbolos.extraer(it.lenguaje, it.textoActual) }.orEmpty()

    suspend fun buscarDefinicion(nombre: String): Triple<String, Int, Int>? {
        val e = _estado.value
        val proyecto = e.proyecto ?: return null
        e.pestanaActiva?.let { p -> ExtractorSimbolos.definicion(p.lenguaje, p.textoActual, nombre)?.let { return Triple(p.ruta, it.linea, it.columna) } }
        for (ruta in e.archivosProyecto) {
            val lenguaje = Lenguaje.desdeNombreArchivo(ruta)
            if (lenguaje != e.pestanaActiva?.lenguaje) continue
            val texto = e.pestanas.firstOrNull { it.ruta == ruta }?.textoActual ?: runCatching { proyectos.leer(proyecto, ruta) }.getOrNull() ?: continue
            ExtractorSimbolos.definicion(lenguaje, texto, nombre)?.let { return Triple(ruta, it.linea, it.columna) }
        }
        return null
    }

    fun compararCon(otra: String) {
        val e = _estado.value
        val proyecto = e.proyecto ?: return
        val activa = e.pestanaActiva ?: return
        viewModelScope.launch {
            val texto = e.pestanas.firstOrNull { it.ruta == otra }?.textoActual ?: runCatching { proyectos.leer(proyecto, otra) }.getOrElse { mensaje(it.message); return@launch }
            _estado.update { it.copy(comparacion = Comparacion(otra, activa.ruta, Diferencias.comparar(texto, activa.textoActual))) }
        }
    }

    fun compararConGuardado() {
        val activa = _estado.value.pestanaActiva ?: return
        _estado.update { it.copy(comparacion = Comparacion("${activa.nombre} (guardado)", "${activa.nombre} (actual)", Diferencias.comparar(activa.textoGuardado, activa.textoActual))) }
    }

    fun cerrarComparacion() = _estado.update { it.copy(comparacion = null) }

    fun abrirPanel(panel: PanelEditor) = _estado.update { it.copy(panel = panel) }

    fun cerrarPestana(ruta: String) {
        _estado.update { e ->
            val restantes = e.pestanas.filterNot { it.ruta == ruta }
            e.copy(pestanas = restantes, activa = if (e.activa == ruta) restantes.lastOrNull()?.ruta else e.activa, diagnosticos = e.diagnosticos - ruta)
        }
    }

    fun alCambiarTexto(ruta: String, texto: String) {
        ultimaEdicion = System.currentTimeMillis()
        _estado.update { e -> e.copy(pestanas = e.pestanas.map { if (it.ruta == ruta) it.copy(textoActual = texto) else it }) }
        val p = preferencias.value
        if (p.validacionEnVivo) {
            trabajoValidacion?.cancel()
            trabajoValidacion = viewModelScope.launch {
                delay(p.retrasoValidacionMs.toLong())
                validarAhora(ruta)
            }
        }
        if (p.guardadoAutomatico == GuardadoAutomatico.CON_RETRASO) {
            trabajoGuardado?.cancel()
            trabajoGuardado = viewModelScope.launch {
                delay(1500)
                guardar(ruta, silencioso = true)
            }
        }
    }

    fun alMoverCursor(posicion: PosicionCursor) = _estado.update { it.copy(cursor = posicion) }

    private suspend fun validarAhora(ruta: String) {
        val pestana = _estado.value.pestanas.firstOrNull { it.ruta == ruta } ?: return
        if (pestana.lenguaje.name in preferencias.value.lenguajesSinValidacion) {
            _estado.update { it.copy(diagnosticos = it.diagnosticos + (ruta to emptyList()), motorValidacion = "Validación desactivada para ${pestana.lenguaje.nombreVisible}") }
            return
        }
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

    private suspend fun guardarInterno(proyecto: ProyectoEntidad, original: Pestana) {
        var pestana = original
        if (preferencias.value.formatearAlGuardar && pestana.lenguaje in setOf(Lenguaje.JSON, Lenguaje.CSS, Lenguaje.HTML)) {
            val r = Formateador.formatear(pestana.lenguaje, pestana.textoActual, preferencias.value.sangriaEspacios)
            if (r.error == null && r.cambiado) {
                pestana = pestana.copy(textoActual = r.texto)
                _estado.update { e -> e.copy(pestanas = e.pestanas.map { if (it.ruta == pestana.ruta) it.copy(textoActual = r.texto) else it }) }
                _cambiosExternos.emit(CambioExterno(pestana.ruta, r.texto, deshacible = true))
            }
        }
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
