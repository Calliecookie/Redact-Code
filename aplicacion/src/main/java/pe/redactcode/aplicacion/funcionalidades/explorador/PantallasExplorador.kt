package pe.redactcode.aplicacion.funcionalidades.explorador

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.datos.PlantillaProyecto
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.nucleo.archivos.CoincidenciaBusqueda
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.archivos.NodoArchivo
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoEntidad
import pe.redactcode.aplicacion.nucleo.diseno.CampoTexto
import pe.redactcode.aplicacion.nucleo.diseno.DialogoConfirmacion
import pe.redactcode.aplicacion.nucleo.diseno.DialogoEntradaTexto
import pe.redactcode.aplicacion.nucleo.diseno.EncabezadoPantalla
import pe.redactcode.aplicacion.nucleo.diseno.EstadoVacio
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresEditor
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.Separador
import pe.redactcode.aplicacion.nucleo.diseno.Superficie
import pe.redactcode.aplicacion.nucleo.preferencias.OrdenArchivos
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.nucleo.modelo.Lenguaje
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@Composable
fun colorLenguaje(lenguaje: Lenguaje): Color {
    val c = LocalColoresEditor.current
    return when (lenguaje) {
        Lenguaje.PYTHON -> c.funcion
        Lenguaje.JAVASCRIPT -> c.atributo
        Lenguaje.HTML -> c.numero
        Lenguaje.CSS -> c.palabraClave
        Lenguaje.JSON -> c.cadena
        Lenguaje.TEXTO -> c.comentario
    }
}

@HiltViewModel
class ProyectosViewModel @Inject constructor(private val repositorio: RepositorioProyectos) : ViewModel() {
    private val consulta = MutableStateFlow("")
    val textoBusqueda: StateFlow<String> = consulta.asStateFlow()
    val proyectos: StateFlow<List<ProyectoEntidad>> = combine(repositorio.proyectos, consulta) { lista, q ->
        if (q.isBlank()) lista else lista.filter { it.nombre.contains(q, true) || it.descripcion.contains(q, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mensaje = MutableStateFlow<String?>(null)

    fun buscar(q: String) { consulta.value = q }

    fun crear(nombre: String, descripcion: String, plantilla: PlantillaProyecto, alCrear: (Long) -> Unit) {
        viewModelScope.launch {
            runCatching { repositorio.crear(nombre, descripcion, plantilla) }.onSuccess(alCrear).onFailure { mensaje.value = it.message }
        }
    }

    fun renombrar(p: ProyectoEntidad, nombre: String) = viewModelScope.launch { runCatching { repositorio.renombrarProyecto(p, nombre) }.onFailure { mensaje.value = it.message } }
    fun eliminar(p: ProyectoEntidad) = viewModelScope.launch { runCatching { repositorio.eliminarProyecto(p) }.onSuccess { mensaje.value = "Proyecto eliminado" }.onFailure { mensaje.value = it.message } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantallaProyectos(alAbrirProyecto: (Long) -> Unit, vm: ProyectosViewModel = hiltViewModel()) {
    val proyectos by vm.proyectos.collectAsStateWithLifecycle()
    val busqueda by vm.textoBusqueda.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var crear by remember { mutableStateOf(false) }
    var seleccionado by remember { mutableStateOf<ProyectoEntidad?>(null) }
    var renombrar by remember { mutableStateOf<ProyectoEntidad?>(null) }
    var eliminar by remember { mutableStateOf<ProyectoEntidad?>(null) }
    LaunchedEffect(mensaje) { mensaje?.let { avisos.showSnackbar(it); vm.mensaje.value = null } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(avisos) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { crear = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Nuevo proyecto") })
        },
        topBar = { EncabezadoPantalla("Proyectos", "${proyectos.size} en este dispositivo") }
    ) { relleno ->
        LazyColumn(Modifier.fillMaxSize().padding(relleno), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { CampoTexto(busqueda, vm::buscar, "Buscar proyectos") }
            if (proyectos.isEmpty()) {
                item { EstadoVacio(Icons.Outlined.FolderOpen, "Sin proyectos", if (busqueda.isBlank()) "Crea un proyecto para empezar a programar." else "No hay coincidencias.") }
            }
            items(proyectos, key = { it.id }) { p ->
                Box {
                    Superficie(Modifier.combinedClickable(onClick = { alAbrirProyecto(p.id) }, onLongClick = { seleccionado = p })) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, null, tint = colorLenguaje(Lenguaje.desdeClave(p.lenguajePrincipal)))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (p.descripcion.ifBlank { Lenguaje.desdeClave(p.lenguajePrincipal).nombreVisible }) + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.modificadoEn)),
                                    style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            when (p.origen) {
                                ProyectoEntidad.ORIGEN_GUIADO -> Etiqueta("Guiado")
                                ProyectoEntidad.ORIGEN_GITHUB -> Etiqueta("GitHub")
                            }
                            IconButton(onClick = { seleccionado = p }) { Icon(Icons.Outlined.MoreVert, "Opciones de ${p.nombre}") }
                        }
                    }
                    DropdownMenu(expanded = seleccionado == p, onDismissRequest = { seleccionado = null }) {
                        DropdownMenuItem(text = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { renombrar = p; seleccionado = null })
                        DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { eliminar = p; seleccionado = null })
                    }
                }
            }
        }
    }

    if (crear) DialogoNuevoProyecto(alCerrar = { crear = false }) { nombre, descripcion, plantilla ->
        crear = false
        vm.crear(nombre, descripcion, plantilla, alAbrirProyecto)
    }
    renombrar?.let { p ->
        DialogoEntradaTexto("Renombrar proyecto", "Nombre", p.nombre, "Renombrar", validar = { if (it.length > 60) "Máximo 60 caracteres" else null },
            alConfirmar = { vm.renombrar(p, it); renombrar = null }, alCerrar = { renombrar = null })
    }
    eliminar?.let { p ->
        DialogoConfirmacion("Eliminar proyecto", "Se eliminarán «${p.nombre}» y todos sus archivos de este dispositivo. Esta acción no se puede deshacer.", "Eliminar", true,
            alConfirmar = { vm.eliminar(p); eliminar = null }, alCerrar = { eliminar = null })
    }
}

@Composable
private fun DialogoNuevoProyecto(alCerrar: () -> Unit, alCrear: (String, String, PlantillaProyecto) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var plantilla by remember { mutableStateOf(PlantillaProyecto.PYTHON) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Nuevo proyecto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoTexto(nombre, { nombre = it.take(60) }, "Nombre")
                CampoTexto(descripcion, { descripcion = it.take(140) }, "Descripción (opcional)")
                Text("Plantilla", style = MaterialTheme.typography.labelLarge)
                PlantillaProyecto.entries.forEach { p ->
                    Etiqueta(p.nombreVisible, seleccionada = plantilla == p, alPulsar = { plantilla = p }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { alCrear(nombre, descripcion, plantilla) }, enabled = nombre.isNotBlank()) { Text("Crear") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

data class EstadoExplorador(
    val proyecto: ProyectoEntidad? = null,
    val arbol: List<NodoArchivo> = emptyList(),
    val expandidas: Set<String> = emptySet(),
    val resultadosBusqueda: List<CoincidenciaBusqueda>? = null
)

@HiltViewModel
class ExploradorViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val repositorio: RepositorioProyectos,
    private val repositorioPreferencias: RepositorioPreferencias
) : ViewModel() {
    private val proyectoId: Long = checkNotNull(estadoGuardado.get<Long>("proyectoId"))
    private val _estado = MutableStateFlow(EstadoExplorador())
    val estado: StateFlow<EstadoExplorador> = _estado.asStateFlow()
    val preferencias: StateFlow<PreferenciasUsuario> = repositorioPreferencias.preferencias.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasUsuario())
    val mensaje = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repositorio.observar(proyectoId).collectLatest { p ->
                _estado.update { it.copy(proyecto = p) }
                recargar()
            }
        }
        viewModelScope.launch { repositorioPreferencias.preferencias.collectLatest { recargar() } }
    }

    fun recargar() {
        val p = _estado.value.proyecto ?: return
        viewModelScope.launch {
            val arbol = runCatching { repositorio.arbol(p, preferencias.value.ordenArchivos) }.getOrDefault(emptyList())
            _estado.update { it.copy(arbol = arbol) }
        }
    }

    fun alternar(ruta: String) = _estado.update { e -> e.copy(expandidas = if (ruta in e.expandidas) e.expandidas - ruta else e.expandidas + ruta) }

    private fun operar(accion: suspend (ProyectoEntidad) -> Unit) {
        val p = _estado.value.proyecto ?: return
        viewModelScope.launch {
            runCatching { accion(p) }.onFailure { mensaje.value = it.message ?: "Operación no completada" }
            recargar()
        }
    }

    fun crearArchivo(padre: String, nombre: String, alCrear: (String) -> Unit) = operar { p ->
        val ruta = repositorio.crearArchivo(p, padre, nombre)
        if (padre.isNotBlank()) _estado.update { it.copy(expandidas = it.expandidas + padre) }
        alCrear(ruta)
    }
    fun crearCarpeta(padre: String, nombre: String) = operar { p -> repositorio.crearCarpeta(p, padre, nombre) }
    fun renombrar(ruta: String, nombre: String) = operar { p -> repositorio.renombrar(p, ruta, nombre) }
    fun mover(ruta: String, destino: String) = operar { p -> repositorio.mover(p, ruta, destino); mensaje.value = "Elemento movido" }
    fun eliminar(ruta: String) = operar { p -> repositorio.eliminar(p, ruta) }
    fun ordenar(orden: OrdenArchivos) = viewModelScope.launch { repositorioPreferencias.ordenArchivos(orden) }

    fun buscar(consulta: String) {
        val p = _estado.value.proyecto ?: return
        if (consulta.isBlank()) {
            _estado.update { it.copy(resultadosBusqueda = null) }
            return
        }
        viewModelScope.launch {
            val r = runCatching { repositorio.buscar(p, consulta) }.getOrDefault(emptyList())
            _estado.update { it.copy(resultadosBusqueda = r) }
        }
    }

    fun carpetas(): List<String> {
        fun recorrer(nodos: List<NodoArchivo>): List<String> = nodos.filter { it.esCarpeta }.flatMap { listOf(it.rutaRelativa) + recorrer(it.hijos) }
        return listOf("") + recorrer(_estado.value.arbol)
    }
}

private sealed interface DialogoExplorador {
    data class NuevoArchivo(val padre: String) : DialogoExplorador
    data class NuevaCarpeta(val padre: String) : DialogoExplorador
    data class Renombrar(val nodo: NodoArchivo) : DialogoExplorador
    data class Mover(val nodo: NodoArchivo) : DialogoExplorador
    data class Eliminar(val nodo: NodoArchivo) : DialogoExplorador
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantallaExplorador(alVolver: () -> Unit, alAbrirArchivo: (Long, String) -> Unit, vm: ExploradorViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val preferencias by vm.preferencias.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var dialogo by remember { mutableStateOf<DialogoExplorador?>(null) }
    var menuNodo by remember { mutableStateOf<String?>(null) }
    var menuOrden by remember { mutableStateOf(false) }
    var buscando by remember { mutableStateOf(false) }
    var consulta by remember { mutableStateOf("") }
    LaunchedEffect(mensaje) { mensaje?.let { avisos.showSnackbar(it); vm.mensaje.value = null } }
    LaunchedEffect(consulta) { kotlinx.coroutines.delay(250); vm.buscar(consulta) }
    val proyecto = estado.proyecto

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(avisos) },
        topBar = {
            EncabezadoPantalla(proyecto?.nombre ?: "Proyecto", "Explorador de archivos", alVolver = alVolver) {
                IconButton(onClick = { buscando = !buscando; if (!buscando) consulta = "" }) { Icon(Icons.Outlined.Search, "Buscar en el proyecto") }
                Box {
                    IconButton(onClick = { menuOrden = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Ordenar") }
                    DropdownMenu(expanded = menuOrden, onDismissRequest = { menuOrden = false }) {
                        OrdenArchivos.entries.forEach { o ->
                            DropdownMenuItem(text = { Text(o.nombreVisible + if (preferencias.ordenArchivos == o) "  ✓" else "") }, onClick = { vm.ordenar(o); menuOrden = false })
                        }
                    }
                }
                IconButton(onClick = { dialogo = DialogoExplorador.NuevaCarpeta("") }) { Icon(Icons.Outlined.CreateNewFolder, "Nueva carpeta") }
                IconButton(onClick = { dialogo = DialogoExplorador.NuevoArchivo("") }) { Icon(Icons.AutoMirrored.Outlined.NoteAdd, "Nuevo archivo") }
            }
        }
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno).navigationBarsPadding()) {
            if (buscando) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { CampoTexto(consulta, { consulta = it }, "Buscar nombres y contenido") }
            }
            val resultados = estado.resultadosBusqueda
            if (buscando && resultados != null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (resultados.isEmpty()) item { EstadoVacio(Icons.Outlined.Search, "Sin resultados", "No se encontró «$consulta» en el proyecto.") }
                    items(resultados) { r ->
                        Column(Modifier.fillMaxWidth().combinedClickable(onClick = { proyecto?.let { alAbrirArchivo(it.id, r.rutaRelativa) } }).padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(r.rutaRelativa + if (r.enNombre) "" else " · línea ${r.linea}", style = MaterialTheme.typography.labelMedium, color = LocalColoresExtendidos.current.textoTenue)
                            Text(r.texto, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Separador()
                    }
                }
            } else {
                val visibles = remember(estado.arbol, estado.expandidas) {
                    val lista = mutableListOf<NodoArchivo>()
                    fun agregar(nodos: List<NodoArchivo>) {
                        nodos.forEach { n ->
                            lista += n
                            if (n.esCarpeta && n.rutaRelativa in estado.expandidas) agregar(n.hijos)
                        }
                    }
                    agregar(estado.arbol)
                    lista
                }
                if (visibles.isEmpty()) {
                    EstadoVacio(Icons.Outlined.Description, "Proyecto vacío", "Crea un archivo para empezar.", accion = "Nuevo archivo", alPulsarAccion = { dialogo = DialogoExplorador.NuevoArchivo("") })
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visibles, key = { it.rutaRelativa }) { nodo ->
                        Box {
                            Row(
                                Modifier.fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { if (nodo.esCarpeta) vm.alternar(nodo.rutaRelativa) else proyecto?.let { alAbrirArchivo(it.id, nodo.rutaRelativa) } },
                                        onLongClick = { menuNodo = nodo.rutaRelativa }
                                    )
                                    .padding(start = (16 + nodo.profundidad * 18).dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (nodo.esCarpeta) (if (nodo.rutaRelativa in estado.expandidas) Icons.Outlined.FolderOpen else Icons.Outlined.Folder) else Icons.Outlined.Description,
                                    contentDescription = if (nodo.esCarpeta) "Carpeta" else "Archivo ${nodo.lenguaje.nombreVisible}",
                                    tint = if (nodo.esCarpeta) LocalColoresExtendidos.current.textoTenue else colorLenguaje(nodo.lenguaje),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    if (preferencias.mostrarExtensiones || nodo.esCarpeta) nodo.nombre else nodo.nombreSinExtension,
                                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (!nodo.esCarpeta) Text(formatoTamano(nodo.tamano), style = MaterialTheme.typography.labelSmall, color = LocalColoresExtendidos.current.textoTenue)
                                IconButton(onClick = { menuNodo = nodo.rutaRelativa }) { Icon(Icons.Outlined.MoreVert, "Opciones de ${nodo.nombre}", tint = LocalColoresExtendidos.current.textoTenue) }
                            }
                            DropdownMenu(expanded = menuNodo == nodo.rutaRelativa, onDismissRequest = { menuNodo = null }) {
                                if (nodo.esCarpeta) {
                                    DropdownMenuItem(text = { Text("Nuevo archivo aquí") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, null) }, onClick = { dialogo = DialogoExplorador.NuevoArchivo(nodo.rutaRelativa); menuNodo = null })
                                    DropdownMenuItem(text = { Text("Nueva carpeta aquí") }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, onClick = { dialogo = DialogoExplorador.NuevaCarpeta(nodo.rutaRelativa); menuNodo = null })
                                }
                                DropdownMenuItem(text = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) }, onClick = { dialogo = DialogoExplorador.Renombrar(nodo); menuNodo = null })
                                DropdownMenuItem(text = { Text("Mover") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) }, onClick = { dialogo = DialogoExplorador.Mover(nodo); menuNodo = null })
                                DropdownMenuItem(text = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { dialogo = DialogoExplorador.Eliminar(nodo); menuNodo = null })
                            }
                        }
                    }
                }
            }
        }
    }

    when (val d = dialogo) {
        is DialogoExplorador.NuevoArchivo -> DialogoEntradaTexto(
            "Nuevo archivo", "Nombre con extensión", "", "Crear",
            validar = { GestorArchivos.validarNombre(it, true) },
            alConfirmar = { nombre -> dialogo = null; vm.crearArchivo(d.padre, nombre) { ruta -> proyecto?.let { alAbrirArchivo(it.id, ruta) } } },
            alCerrar = { dialogo = null }
        )
        is DialogoExplorador.NuevaCarpeta -> DialogoEntradaTexto("Nueva carpeta", "Nombre", "", "Crear", validar = { GestorArchivos.validarNombre(it, false) },
            alConfirmar = { dialogo = null; vm.crearCarpeta(d.padre, it) }, alCerrar = { dialogo = null })
        is DialogoExplorador.Renombrar -> DialogoEntradaTexto("Renombrar", "Nuevo nombre", d.nodo.nombre, "Renombrar", validar = { GestorArchivos.validarNombre(it, !d.nodo.esCarpeta) },
            alConfirmar = { dialogo = null; vm.renombrar(d.nodo.rutaRelativa, it) }, alCerrar = { dialogo = null })
        is DialogoExplorador.Eliminar -> DialogoConfirmacion("Eliminar", "¿Eliminar «${d.nodo.nombre}»${if (d.nodo.esCarpeta) " y todo su contenido" else ""}? No se puede deshacer.", "Eliminar", true,
            alConfirmar = { dialogo = null; vm.eliminar(d.nodo.rutaRelativa) }, alCerrar = { dialogo = null })
        is DialogoExplorador.Mover -> {
            val destinos = vm.carpetas().filter { it != d.nodo.rutaRelativa && !it.startsWith(d.nodo.rutaRelativa + "/") && it != d.nodo.rutaRelativa.substringBeforeLast('/', "") }
            AlertDialog(
                onDismissRequest = { dialogo = null },
                title = { Text("Mover «${d.nodo.nombre}» a…") },
                text = {
                    LazyColumn {
                        items(destinos) { destino ->
                            Text(if (destino.isEmpty()) "/ (raíz del proyecto)" else "/$destino", style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { dialogo = null; vm.mover(d.nodo.rutaRelativa, destino) }).padding(vertical = 12.dp))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { dialogo = null }) { Text("Cancelar") } },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
        null -> Unit
    }
}

private fun formatoTamano(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
}
