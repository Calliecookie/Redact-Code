package pe.redactcode.aplicacion.funcionalidades.espacio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.redactcode.aplicacion.datos.FabricaProveedoresAgente
import pe.redactcode.aplicacion.datos.RepositorioGitHub
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.archivos.NodoArchivo
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoEntidad
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.nucleo.agentes.AccionCambio
import pe.redactcode.nucleo.agentes.AgenteCodigo
import pe.redactcode.nucleo.agentes.CambioPropuesto
import pe.redactcode.nucleo.agentes.MensajeAgente
import pe.redactcode.nucleo.agentes.PropuestaAgente
import pe.redactcode.nucleo.control.CambioTrabajo
import pe.redactcode.nucleo.control.CommitLocal
import pe.redactcode.nucleo.control.ControlVersiones
import pe.redactcode.nucleo.control.DiferenciaArchivo
import pe.redactcode.nucleo.github.AutorGit
import pe.redactcode.nucleo.github.ClienteGitHub
import pe.redactcode.nucleo.github.ResumenCommitGitHub
import pe.redactcode.nucleo.github.SincronizadorGitHub
import pe.redactcode.nucleo.github.VinculoRemoto
import pe.redactcode.nucleo.ide.BusquedaGlobal
import pe.redactcode.nucleo.ide.Diferencias
import pe.redactcode.nucleo.ide.OpcionesBusqueda
import pe.redactcode.nucleo.ide.ResultadoBusquedaGlobal
import pe.redactcode.nucleo.ide.ComandoInterno
import pe.redactcode.nucleo.ide.ResultadoComando
import pe.redactcode.nucleo.ide.TerminalLocal
import pe.redactcode.nucleo.modelo.EstadoEjecucion
import pe.redactcode.nucleo.modelo.Lenguaje
import java.io.File
import javax.inject.Inject

abstract class ViewModelProyecto(
    estadoGuardado: SavedStateHandle,
    protected val proyectos: RepositorioProyectos
) : ViewModel() {
    protected val proyectoId: Long = checkNotNull(estadoGuardado.get<Long>("proyectoId"))
    protected var proyecto: ProyectoEntidad? = null
    protected val _mensajes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val mensajes: SharedFlow<String> = _mensajes.asSharedFlow()
    protected val _archivosCambiados = MutableSharedFlow<List<String>?>(extraBufferCapacity = 16)
    val archivosCambiados: SharedFlow<List<String>?> = _archivosCambiados.asSharedFlow()

    protected suspend fun proyectoActual(): ProyectoEntidad? = proyecto ?: proyectos.obtener(proyectoId).also { proyecto = it }

    protected suspend fun carpeta(): File? = proyectoActual()?.let { proyectos.archivos.carpetaProyecto(it.carpeta) }

    protected fun avisar(texto: String?) { texto?.let { _mensajes.tryEmit(it) } }
}

data class EstadoExplorador(val nodos: List<NodoArchivo> = emptyList(), val expandidas: Set<String> = emptySet(), val cargando: Boolean = true)

@HiltViewModel
class ExploradorEspacioViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    proyectos: RepositorioProyectos,
    private val repositorioPreferencias: RepositorioPreferencias
) : ViewModelProyecto(estadoGuardado, proyectos) {
    private val _estado = MutableStateFlow(EstadoExplorador())
    val estado: StateFlow<EstadoExplorador> = _estado.asStateFlow()
    val preferencias: StateFlow<PreferenciasUsuario> = repositorioPreferencias.preferencias.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasUsuario())
    private val _renombrados = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)
    val renombrados: SharedFlow<Pair<String, String>> = _renombrados.asSharedFlow()
    private val _eliminados = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val eliminados: SharedFlow<String> = _eliminados.asSharedFlow()
    private val _creados = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val creados: SharedFlow<String> = _creados.asSharedFlow()

    init { refrescar() }

    fun refrescar() {
        viewModelScope.launch {
            val p = proyectoActual() ?: return@launch
            val nodos = runCatching { proyectos.arbol(p, repositorioPreferencias.actuales().ordenArchivos) }.getOrDefault(emptyList())
            _estado.update { it.copy(nodos = nodos, cargando = false) }
        }
    }

    fun alternar(ruta: String) = _estado.update { it.copy(expandidas = if (ruta in it.expandidas) it.expandidas - ruta else it.expandidas + ruta) }

    fun expandirHasta(ruta: String) {
        val partes = ruta.split('/').dropLast(1)
        val prefijos = partes.indices.map { partes.subList(0, it + 1).joinToString("/") }
        _estado.update { it.copy(expandidas = it.expandidas + prefijos) }
    }

    fun crear(padre: String, nombre: String, carpeta: Boolean) {
        viewModelScope.launch {
            val p = proyectoActual() ?: return@launch
            runCatching { if (carpeta) proyectos.crearCarpeta(p, padre, nombre) else proyectos.crearArchivo(p, padre, nombre) }
                .onSuccess { ruta ->
                    if (padre.isNotBlank()) _estado.update { it.copy(expandidas = it.expandidas + padre) }
                    refrescar()
                    if (!carpeta) _creados.emit(ruta)
                }
                .onFailure { avisar(it.message) }
        }
    }

    fun renombrar(ruta: String, nombre: String) {
        viewModelScope.launch {
            val p = proyectoActual() ?: return@launch
            runCatching { proyectos.renombrar(p, ruta, nombre) }
                .onSuccess { nueva -> refrescar(); _renombrados.emit(ruta to nueva) }
                .onFailure { avisar(it.message) }
        }
    }

    fun mover(ruta: String, destino: String) {
        viewModelScope.launch {
            val p = proyectoActual() ?: return@launch
            runCatching { proyectos.mover(p, ruta, destino) }
                .onSuccess { nueva -> refrescar(); _renombrados.emit(ruta to nueva) }
                .onFailure { avisar(it.message) }
        }
    }

    fun eliminar(ruta: String) {
        viewModelScope.launch {
            val p = proyectoActual() ?: return@launch
            runCatching { proyectos.eliminar(p, ruta) }
                .onSuccess { refrescar(); _eliminados.emit(ruta); avisar("Eliminado: $ruta") }
                .onFailure { avisar(it.message) }
        }
    }

    fun carpetas(): List<String> {
        fun recorrer(n: List<NodoArchivo>): List<String> = n.filter { it.esCarpeta }.flatMap { listOf(it.rutaRelativa) + recorrer(it.hijos) }
        return listOf("") + recorrer(_estado.value.nodos)
    }
}

data class EstadoBusqueda(
    val opciones: OpcionesBusqueda = OpcionesBusqueda(""),
    val reemplazo: String = "",
    val resultado: ResultadoBusquedaGlobal? = null,
    val buscando: Boolean = false
)

@HiltViewModel
class BusquedaEspacioViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    proyectos: RepositorioProyectos,
    private val repositorioPreferencias: RepositorioPreferencias
) : ViewModelProyecto(estadoGuardado, proyectos) {
    private val _estado = MutableStateFlow(EstadoBusqueda())
    val estado: StateFlow<EstadoBusqueda> = _estado.asStateFlow()
    private var trabajo: Job? = null

    init {
        viewModelScope.launch {
            val excluir = repositorioPreferencias.actuales().excluirBusqueda
            if (excluir.isNotBlank()) _estado.update { it.copy(opciones = it.opciones.copy(excluir = excluir)) }
        }
    }

    fun opciones(nuevas: OpcionesBusqueda) {
        _estado.update { it.copy(opciones = nuevas) }
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            delay(300)
            buscarAhora()
        }
    }

    fun reemplazo(texto: String) = _estado.update { it.copy(reemplazo = texto) }

    private suspend fun buscarAhora() {
        val raiz = carpeta() ?: return
        val o = _estado.value.opciones
        if (o.consulta.isEmpty()) { _estado.update { it.copy(resultado = null, buscando = false) }; return }
        _estado.update { it.copy(buscando = true) }
        val r = withContext(Dispatchers.IO) { BusquedaGlobal.buscar(BusquedaGlobal.archivosDeCarpeta(raiz), o) }
        _estado.update { it.copy(resultado = r, buscando = false) }
    }

    fun refrescar() = viewModelScope.launch { buscarAhora() }

    fun reemplazarEn(rutas: List<String>?) {
        viewModelScope.launch {
            val p = proyectoActual() ?: return@launch
            val e = _estado.value
            val objetivos = rutas ?: e.resultado?.archivos?.map { it.ruta }.orEmpty()
            var total = 0
            val cambiados = mutableListOf<String>()
            runCatching {
                objetivos.forEach { ruta ->
                    val texto = proyectos.leer(p, ruta)
                    val (nuevo, cantidad) = BusquedaGlobal.reemplazar(texto, e.opciones, e.reemplazo)
                    if (cantidad > 0) {
                        proyectos.guardar(p, ruta, nuevo)
                        total += cantidad
                        cambiados += ruta
                    }
                }
            }.onFailure { avisar(it.message) }
            avisar("$total reemplazo(s) en ${cambiados.size} archivo(s)")
            _archivosCambiados.emit(cambiados)
            buscarAhora()
        }
    }
}

data class EstadoControl(
    val inicializado: Boolean = false,
    val rama: String = "",
    val ramas: List<String> = emptyList(),
    val cambios: List<CambioTrabajo> = emptyList(),
    val historial: List<CommitLocal> = emptyList(),
    val vinculo: VinculoRemoto? = null,
    val pendientes: Int = 0,
    val historialRemoto: List<ResumenCommitGitHub> = emptyList(),
    val diferencia: DiferenciaArchivo? = null,
    val diferenciasCommit: Pair<CommitLocal, List<DiferenciaArchivo>>? = null,
    val conflictos: List<String> = emptyList(),
    val ocupado: String? = null,
    val usuarioGitHub: String? = null
)

@HiltViewModel
class ControlCodigoViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    proyectos: RepositorioProyectos,
    private val repositorioPreferencias: RepositorioPreferencias,
    private val cliente: ClienteGitHub,
    private val github: RepositorioGitHub
) : ViewModelProyecto(estadoGuardado, proyectos) {
    private val _estado = MutableStateFlow(EstadoControl())
    val estado: StateFlow<EstadoControl> = _estado.asStateFlow()
    private var sincronizador: SincronizadorGitHub? = null

    init { refrescar() }

    private suspend fun sync(): SincronizadorGitHub? {
        sincronizador?.let { return it }
        val raiz = carpeta() ?: return null
        return SincronizadorGitHub(cliente, raiz).also { sincronizador = it }
    }

    fun refrescar() {
        viewModelScope.launch {
            val s = sync() ?: return@launch
            github.cargarEstado()
            val nuevo = withContext(Dispatchers.IO) {
                val vcs = s.vcs
                if (!vcs.inicializado()) EstadoControl(inicializado = false, usuarioGitHub = github.usuario.value)
                else EstadoControl(
                    inicializado = true,
                    rama = vcs.ramaActual(),
                    ramas = vcs.ramas(),
                    cambios = vcs.estado(),
                    historial = vcs.historial(100),
                    vinculo = s.vinculo(),
                    pendientes = s.pendientesDePublicar().let { it.first.size + it.second.size },
                    usuarioGitHub = github.usuario.value
                )
            }
            _estado.update { nuevo.copy(diferencia = it.diferencia, diferenciasCommit = it.diferenciasCommit, conflictos = it.conflictos, historialRemoto = it.historialRemoto) }
        }
    }

    private fun operar(descripcion: String, cambiaArchivos: Boolean, bloque: suspend (SincronizadorGitHub) -> String?) {
        viewModelScope.launch {
            val s = sync() ?: return@launch
            _estado.update { it.copy(ocupado = descripcion) }
            runCatching { withContext(Dispatchers.IO) { bloque(s) } }
                .onSuccess { avisar(it) }
                .onFailure { avisar(it.message ?: "No se pudo completar la operación") }
            _estado.update { it.copy(ocupado = null) }
            if (cambiaArchivos) _archivosCambiados.emit(null)
            refrescar()
        }
    }

    private suspend fun autor(): Pair<String, String> {
        val p = repositorioPreferencias.actuales()
        return p.gitAutor.ifBlank { github.usuario.value ?: "Redact Code" } to p.gitCorreo
    }

    fun inicializar() = operar("Inicializando", false) {
        it.vcs.inicializar(repositorioPreferencias.actuales().ramaPredeterminada.ifBlank { "main" })
        "Control de versiones inicializado"
    }

    fun confirmar(mensaje: String, rutas: Set<String>?) = operar("Confirmando", false) {
        val (nombre, correo) = autor()
        val c = it.vcs.confirmar(mensaje, nombre, correo, rutas)
        "Commit ${c.id.take(7)} creado"
    }

    fun descartar(ruta: String) = operar("Descartando", true) { it.vcs.descartar(ruta); "Cambios descartados en $ruta" }

    fun verDiferencia(ruta: String) {
        viewModelScope.launch {
            val s = sync() ?: return@launch
            val d = runCatching { withContext(Dispatchers.IO) { s.vcs.diferenciaTrabajo(ruta) } }.getOrNull()
            _estado.update { it.copy(diferencia = d) }
        }
    }

    fun verCommit(c: CommitLocal) {
        viewModelScope.launch {
            val s = sync() ?: return@launch
            val d = runCatching { withContext(Dispatchers.IO) { s.vcs.diferenciasCommit(c.id) } }.getOrElse { avisar(it.message); return@launch }
            _estado.update { it.copy(diferenciasCommit = c to d) }
        }
    }

    fun cerrarDiferencias() = _estado.update { it.copy(diferencia = null, diferenciasCommit = null) }

    fun crearRama(nombre: String) = operar("Creando rama", false) { it.vcs.crearRama(nombre.trim()); "Rama ${nombre.trim()} creada" }

    fun cambiarRama(nombre: String) = operar("Cambiando de rama", true) { it.vcs.cambiarRama(nombre); "Ahora estás en $nombre" }

    fun eliminarRama(nombre: String) = operar("Eliminando rama", false) { it.vcs.eliminarRama(nombre); "Rama $nombre eliminada" }

    fun vincular(completo: String, rama: String) = operar("Vinculando con GitHub", false) {
        val partes = completo.trim().removePrefix("https://github.com/").removeSuffix(".git").split('/')
        if (partes.size != 2 || partes.any { p -> p.isBlank() }) throw IllegalArgumentException("Escribe el repositorio como dueño/nombre")
        it.vincular(partes[0], partes[1], rama.trim().ifBlank { "main" })
        "Vinculado con ${partes[0]}/${partes[1]}"
    }

    fun desvincular() = operar("Desvinculando", false) { it.desvincular(); "El proyecto ya no está vinculado" }

    fun traer() = operar("Trayendo cambios", true) { s ->
        val r = s.traer(autor().first)
        _estado.update { e -> e.copy(conflictos = r.conflictos) }
        when {
            r.yaActualizado -> "Ya estás al día con el remoto"
            r.conflictos.isNotEmpty() -> "Conflictos en ${r.conflictos.size} archivo(s): no se aplicó ningún cambio"
            else -> "Traídos ${r.actualizados.size} archivo(s) actualizados y ${r.eliminados.size} eliminados"
        }
    }

    fun publicar(mensaje: String, ramaNueva: String?) = operar("Publicando en GitHub", false) { s ->
        val (nombre, correo) = autor()
        val r = s.publicar(mensaje, if (correo.isNotBlank()) AutorGit(nombre, correo) else null, ramaNueva?.trim()?.ifBlank { null })
        "Publicado ${r.shaCommit.take(7)}: ${r.modificados.size} modificado(s), ${r.eliminados.size} eliminado(s)"
    }

    fun cargarHistorialRemoto() {
        viewModelScope.launch {
            val v = sync()?.vinculo() ?: return@launch
            runCatching { cliente.historial(v.dueno, v.repositorio, v.rama) }
                .onSuccess { lista -> _estado.update { it.copy(historialRemoto = lista) } }
                .onFailure { avisar(it.message) }
        }
    }

    fun limpiarConflictos() = _estado.update { it.copy(conflictos = emptyList()) }
}

enum class TipoLineaTerminal { COMANDO, SALIDA, ERROR, INFO }

data class LineaTerminal(val texto: String, val tipo: TipoLineaTerminal)

data class EstadoTerminal(
    val lineas: List<LineaTerminal> = emptyList(),
    val ruta: String = "~",
    val ejecutando: Boolean = false,
    val consola: List<LineaTerminal> = emptyList(),
    val lenguajeConsola: Lenguaje = Lenguaje.JAVASCRIPT,
    val evaluando: Boolean = false
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    proyectos: RepositorioProyectos,
    private val servicio: ServicioCodigo,
    private val repositorioPreferencias: RepositorioPreferencias
) : ViewModelProyecto(estadoGuardado, proyectos) {
    private val _estado = MutableStateFlow(EstadoTerminal())
    val estado: StateFlow<EstadoTerminal> = _estado.asStateFlow()
    val preferencias: StateFlow<PreferenciasUsuario> = repositorioPreferencias.preferencias.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasUsuario())
    private var terminal: TerminalLocal? = null
    private val entradasJs = ArrayDeque<String>()
    private var indiceHistorial = -1

    private suspend fun terminal(): TerminalLocal? {
        terminal?.let { return it }
        val raiz = carpeta() ?: return null
        val internos = mapOf(
            "python" to ComandoInterno { args, dir -> ejecutarArchivo(args, dir, Lenguaje.PYTHON) },
            "python3" to ComandoInterno { args, dir -> ejecutarArchivo(args, dir, Lenguaje.PYTHON) },
            "node" to ComandoInterno { args, dir -> ejecutarArchivo(args, dir, Lenguaje.JAVASCRIPT) }
        )
        return TerminalLocal(raiz, internos = internos).also {
            terminal = it
            _estado.update { e -> e.copy(lineas = listOf(LineaTerminal("Terminal de Redact Code · escribe help para ver los comandos", TipoLineaTerminal.INFO))) }
        }
    }

    private suspend fun ejecutarArchivo(args: List<String>, dir: File, lenguaje: Lenguaje): ResultadoComando {
        val nombre = args.firstOrNull() ?: return ResultadoComando("", "Uso: ${if (lenguaje == Lenguaje.PYTHON) "python" else "node"} archivo\n", 2)
        val raiz = carpeta() ?: return ResultadoComando("", "Proyecto no disponible\n", 1)
        val archivo = File(dir, nombre).canonicalFile
        if (!archivo.path.startsWith(raiz.canonicalPath)) return ResultadoComando("", "El archivo está fuera del proyecto\n", 1)
        if (!archivo.isFile) return ResultadoComando("", "No existe el archivo $nombre\n", 1)
        val codigo = withContext(Dispatchers.IO) { archivo.readText() }
        val limite = repositorioPreferencias.actuales().limiteEjecucionSegundos
        val r = if (lenguaje == Lenguaje.PYTHON) servicio.ejecutarPython(codigo, "", limite) else servicio.ejecutarJavaScript(codigo, limite)
        val salida = r.salida.let { if (it.isEmpty() || it.endsWith("\n")) it else "$it\n" }
        val errores = r.errores.let { if (it.isEmpty() || it.endsWith("\n")) it else "$it\n" }
        return ResultadoComando(salida, errores, if (r.estado == EstadoEjecucion.EXITO) 0 else 1)
    }

    fun iniciar() = viewModelScope.launch { val t = terminal(); _estado.update { it.copy(ruta = t?.rutaVisible ?: "~") } }

    fun ejecutar(linea: String) {
        if (linea.isBlank() || _estado.value.ejecutando) return
        viewModelScope.launch {
            val t = terminal() ?: return@launch
            indiceHistorial = -1
            _estado.update { it.copy(ejecutando = true, lineas = it.lineas + LineaTerminal("${t.rutaVisible} $ $linea", TipoLineaTerminal.COMANDO)) }
            val r = t.ejecutar(linea, repositorioPreferencias.actuales().limiteTerminalSegundos)
            _estado.update { e ->
                val nuevas = if (r.limpiar) emptyList() else buildList {
                    addAll(e.lineas)
                    r.salida.trimEnd('\n').takeIf { it.isNotEmpty() }?.let { add(LineaTerminal(it, TipoLineaTerminal.SALIDA)) }
                    r.error.trimEnd('\n').takeIf { it.isNotEmpty() }?.let { add(LineaTerminal(it, TipoLineaTerminal.ERROR)) }
                    if (r.codigo != 0 && r.error.isEmpty()) add(LineaTerminal("Código de salida ${r.codigo}", TipoLineaTerminal.INFO))
                }
                e.copy(lineas = nuevas.takeLast(2000), ejecutando = false, ruta = t.rutaVisible)
            }
            if (linea.trim().split(' ').firstOrNull() in setOf("touch", "rm", "mv", "cp", "mkdir", "rmdir", "echo", "sed", "tee", "python", "node")) _archivosCambiados.emit(null)
        }
    }

    fun cancelar() { terminal?.cancelar() }

    fun historial(arriba: Boolean): String? {
        val h = terminal?.historial ?: return null
        if (h.isEmpty()) return null
        indiceHistorial = if (arriba) (if (indiceHistorial < 0) h.lastIndex else (indiceHistorial - 1).coerceAtLeast(0)) else (indiceHistorial + 1).takeIf { it <= h.lastIndex } ?: -1
        return if (indiceHistorial < 0) "" else h[indiceHistorial]
    }

    fun autocompletar(linea: String): String? {
        val opciones = terminal?.autocompletar(linea).orEmpty()
        if (opciones.size != 1) {
            if (opciones.size > 1) _estado.update { it.copy(lineas = it.lineas + LineaTerminal(opciones.joinToString("   "), TipoLineaTerminal.INFO)) }
            return null
        }
        val partes = TerminalLocal.dividir(linea)
        val base = if (linea.endsWith(" ")) linea else linea.dropLast(partes.lastOrNull()?.length ?: 0)
        return base + opciones.first()
    }

    fun limpiar() = _estado.update { it.copy(lineas = emptyList()) }

    fun lenguajeConsola(l: Lenguaje) = _estado.update { it.copy(lenguajeConsola = l) }

    fun evaluar(entrada: String) {
        if (entrada.isBlank() || _estado.value.evaluando) return
        val lenguaje = _estado.value.lenguajeConsola
        viewModelScope.launch {
            _estado.update { it.copy(evaluando = true, consola = it.consola + LineaTerminal((if (lenguaje == Lenguaje.PYTHON) ">>> " else "> ") + entrada, TipoLineaTerminal.COMANDO)) }
            val limite = repositorioPreferencias.actuales().limiteEjecucionSegundos
            val r = if (lenguaje == Lenguaje.PYTHON) servicio.python.consola(entrada, limite) else {
                val previo = entradasJs.joinToString("\n")
                val programa = "var __l=console.log,__e=console.error,__w=console.warn,__i=console.info;" +
                    "console.log=console.error=console.warn=console.info=function(){};\n" + previo +
                    "\nconsole.log=__l;console.error=__e;console.warn=__w;console.info=__i;\n" +
                    "var __r=eval(${pe.redactcode.nucleo.javascript.ArnesJavaScript.literal(entrada)});if(__r!==undefined)console.log(__r);"
                servicio.ejecutarJavaScript(programa, limite).also { if (it.estado == EstadoEjecucion.EXITO) { entradasJs.addLast(entrada); while (entradasJs.size > 100) entradasJs.removeFirst() } }
            }
            _estado.update { e ->
                val nuevas = buildList {
                    addAll(e.consola)
                    r.salida.trimEnd('\n').takeIf { it.isNotEmpty() }?.let { add(LineaTerminal(it, TipoLineaTerminal.SALIDA)) }
                    r.errores.trimEnd('\n').takeIf { it.isNotEmpty() }?.let { add(LineaTerminal(it, TipoLineaTerminal.ERROR)) }
                }
                e.copy(consola = nuevas.takeLast(1000), evaluando = false)
            }
        }
    }

    fun reiniciarConsola() {
        viewModelScope.launch {
            entradasJs.clear()
            runCatching { servicio.python.reiniciarConsola() }
            _estado.update { it.copy(consola = listOf(LineaTerminal("Sesión reiniciada", TipoLineaTerminal.INFO))) }
        }
    }
}

data class EstadoAgente(
    val conversacion: List<MensajeAgente> = emptyList(),
    val propuesta: PropuestaAgente? = null,
    val decisiones: Map<String, Boolean> = emptyMap(),
    val trabajando: Boolean = false,
    val proveedor: String? = null,
    val incluidos: List<String> = emptyList(),
    val omitidos: List<String> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AgenteViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    proyectos: RepositorioProyectos,
    private val fabrica: FabricaProveedoresAgente
) : ViewModelProyecto(estadoGuardado, proyectos) {
    private val _estado = MutableStateFlow(EstadoAgente())
    val estado: StateFlow<EstadoAgente> = _estado.asStateFlow()
    private var trabajo: Job? = null

    fun enviar(instruccion: String, archivoActivo: String?, textosAbiertos: Map<String, String>) {
        if (instruccion.isBlank() || _estado.value.trabajando) return
        trabajo = viewModelScope.launch {
            val p = proyectoActual() ?: return@launch
            val (proveedor, motivo) = fabrica.obtener()
            if (proveedor == null) { _estado.update { it.copy(error = motivo) }; return@launch }
            val permisos = fabrica.permisos()
            _estado.update { it.copy(trabajando = true, error = null, proveedor = proveedor.nombre) }
            val archivos = withContext(Dispatchers.IO) {
                proyectos.archivos.listarArchivos(p.carpeta).filter { Lenguaje.desdeNombreArchivo(it) != Lenguaje.TEXTO || it.substringAfterLast('.') in Lenguaje.extensionesEditables }
                    .associateWith { ruta -> textosAbiertos[ruta] ?: runCatching { proyectos.leer(p, ruta) }.getOrDefault("") }
            }
            val prioridad = listOfNotNull(archivoActivo) + textosAbiertos.keys.filter { it != archivoActivo }
            val contexto = AgenteCodigo.contexto(archivos, prioridad, permisos)
            val solicitud = buildString {
                appendLine(contexto.texto)
                archivoActivo?.let { appendLine("Archivo activo en el editor: $it") }
                appendLine()
                appendLine("Instrucción del usuario: $instruccion")
            }
            val historial = _estado.value.conversacion.takeLast(6)
            val mensajes = historial + MensajeAgente(MensajeAgente.USUARIO, solicitud)
            runCatching { proveedor.completar(AgenteCodigo.sistema(permisos), mensajes) }
                .onSuccess { respuesta ->
                    val propuesta = AgenteCodigo.interpretar(respuesta, archivos, permisos) { ruta -> GestorArchivos.validarNombre(ruta.substringAfterLast('/'), esArchivo = true) }
                    _estado.update {
                        it.copy(
                            trabajando = false,
                            conversacion = (historial + MensajeAgente(MensajeAgente.USUARIO, instruccion) + MensajeAgente(MensajeAgente.ASISTENTE, propuesta.resumen)),
                            propuesta = propuesta,
                            decisiones = propuesta.cambios.associate { c -> c.ruta to true },
                            incluidos = contexto.incluidos,
                            omitidos = contexto.omitidos
                        )
                    }
                }
                .onFailure { e -> _estado.update { it.copy(trabajando = false, error = e.message ?: "Error del proveedor") } }
        }
    }

    fun cancelar() {
        trabajo?.cancel()
        _estado.update { it.copy(trabajando = false) }
    }

    fun decidir(ruta: String, aceptar: Boolean) = _estado.update { it.copy(decisiones = it.decisiones + (ruta to aceptar)) }

    fun aplicar(bloquesPorRuta: Map<String, Set<Int>> = emptyMap()) {
        val e = _estado.value
        val propuesta = e.propuesta ?: return
        viewModelScope.launch {
            val p = proyectoActual() ?: return@launch
            val aplicados = mutableListOf<String>()
            val errores = mutableListOf<String>()
            propuesta.cambios.filter { e.decisiones[it.ruta] == true }.forEach { c: CambioPropuesto ->
                runCatching {
                    when (c.accion) {
                        AccionCambio.ELIMINAR -> proyectos.eliminar(p, c.ruta)
                        AccionCambio.CREAR -> {
                            val padre = c.ruta.substringBeforeLast('/', "")
                            if (padre.isNotEmpty()) withContext(Dispatchers.IO) { proyectos.archivos.resolver(p.carpeta, padre).mkdirs() }
                            if (proyectos.archivos.existe(p.carpeta, c.ruta)) proyectos.guardar(p, c.ruta, c.nuevo.orEmpty())
                            else proyectos.archivos.crearArchivo(p.carpeta, padre, c.ruta.substringAfterLast('/'), c.nuevo.orEmpty())
                        }
                        AccionCambio.MODIFICAR -> {
                            val bloques = bloquesPorRuta[c.ruta]
                            val final = if (bloques == null) c.nuevo.orEmpty() else Diferencias.aplicarBloques(c.anterior.orEmpty(), c.nuevo.orEmpty(), c.diferencias, bloques)
                            proyectos.guardar(p, c.ruta, final)
                        }
                    }
                    aplicados += c.ruta
                }.onFailure { errores += "${c.ruta}: ${it.message}" }
            }
            avisar(if (errores.isEmpty()) "Se aplicaron ${aplicados.size} cambio(s)" else "Aplicados ${aplicados.size}; errores: ${errores.joinToString("; ")}")
            _estado.update { it.copy(propuesta = null, decisiones = emptyMap()) }
            _archivosCambiados.emit(null)
        }
    }

    fun descartarPropuesta() = _estado.update { it.copy(propuesta = null, decisiones = emptyMap()) }

    fun nuevaConversacion() = _estado.update { EstadoAgente() }
}
