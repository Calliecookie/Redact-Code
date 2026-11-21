package pe.redactcode.aplicacion.funcionalidades.fragmentos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.datos.PlantillaProyecto
import pe.redactcode.aplicacion.datos.RepositorioFragmentos
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.datos.comoFecha
import pe.redactcode.aplicacion.nucleo.base_datos.FragmentoConRepaso
import pe.redactcode.aplicacion.nucleo.base_datos.HistorialRepasoEntidad
import pe.redactcode.aplicacion.nucleo.diseno.BloqueCodigo
import pe.redactcode.aplicacion.nucleo.diseno.BotonPrincipal
import pe.redactcode.aplicacion.nucleo.diseno.BotonSecundario
import pe.redactcode.aplicacion.nucleo.diseno.Cargando
import pe.redactcode.aplicacion.nucleo.diseno.CampoTexto
import pe.redactcode.aplicacion.nucleo.diseno.DialogoConfirmacion
import pe.redactcode.aplicacion.nucleo.diseno.EncabezadoPantalla
import pe.redactcode.aplicacion.nucleo.diseno.EstadoVacio
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.FilaInterruptor
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.Superficie
import pe.redactcode.nucleo.modelo.Lenguaje
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class FragmentosViewModel @Inject constructor(
    private val repositorio: RepositorioFragmentos,
    private val proyectos: RepositorioProyectos
) : ViewModel() {
    val consulta = MutableStateFlow("")
    val etiqueta = MutableStateFlow<String?>(null)
    val lenguaje = MutableStateFlow<Lenguaje?>(null)
    val mensaje = MutableStateFlow<String?>(null)
    val todos: StateFlow<List<FragmentoConRepaso>> = repositorio.fragmentos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val filtrados: StateFlow<List<FragmentoConRepaso>?> = combine(repositorio.fragmentos, consulta, etiqueta, lenguaje) { lista, q, e, l ->
        lista.filter { f ->
            (q.isBlank() || f.fragmento.titulo.contains(q, true) || f.fragmento.codigo.contains(q, true) || f.fragmento.descripcion.contains(q, true)) &&
                (e == null || e in f.fragmento.listaEtiquetas) &&
                (l == null || Lenguaje.desdeClave(f.fragmento.lenguaje) == l)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val pendientes: StateFlow<List<FragmentoConRepaso>> = repositorio.pendientes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun eliminar(id: Long) = viewModelScope.launch { repositorio.eliminar(id); mensaje.value = "Fragmento eliminado" }

    fun abrirEnEditor(f: FragmentoConRepaso, alAbrir: (Long, String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val existente = proyectos.proyectos.first().firstOrNull { it.nombre == "Mis fragmentos" }
                val proyecto = existente ?: proyectos.obtener(proyectos.crear("Mis fragmentos", "Fragmentos abiertos desde la biblioteca", PlantillaProyecto.VACIO, emptyMap()))!!
                val lenguaje = Lenguaje.desdeClave(f.fragmento.lenguaje)
                val base = f.fragmento.titulo.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').take(40).ifEmpty { "fragmento" }
                val ruta = "${base}_${f.fragmento.id}.${lenguaje.extensionPrincipal}"
                proyectos.guardar(proyecto, ruta, f.fragmento.codigo)
                proyecto.id to ruta
            }.onSuccess { (id, ruta) -> alAbrir(id, ruta) }.onFailure { mensaje.value = it.message }
        }
    }
}

@Composable
fun PantallaFragmentos(
    alEditar: (Long?) -> Unit,
    alRepasar: () -> Unit,
    alAbrirArchivo: (Long, String) -> Unit,
    vm: FragmentosViewModel = hiltViewModel()
) {
    val lista by vm.filtrados.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()
    val pendientes by vm.pendientes.collectAsStateWithLifecycle()
    val consulta by vm.consulta.collectAsStateWithLifecycle()
    val etiqueta by vm.etiqueta.collectAsStateWithLifecycle()
    val lenguaje by vm.lenguaje.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    var eliminar by remember { mutableStateOf<FragmentoConRepaso?>(null) }
    LaunchedEffect(mensaje) { mensaje?.let { avisos.showSnackbar(it); vm.mensaje.value = null } }
    val etiquetas = remember(todos) { todos.flatMap { it.fragmento.listaEtiquetas }.distinct().sorted() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(avisos) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { alEditar(null) }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Nuevo fragmento") })
        },
        topBar = { EncabezadoPantalla("Biblioteca de fragmentos", "${todos.size} guardados") }
    ) { relleno ->
        LazyColumn(Modifier.fillMaxSize().padding(relleno), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Superficie(alPulsar = if (pendientes.isNotEmpty()) alRepasar else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Replay, null, tint = if (pendientes.isNotEmpty()) LocalColoresExtendidos.current.acento else LocalColoresExtendidos.current.textoTenue)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(if (pendientes.isEmpty()) "Sin repasos pendientes" else "${pendientes.size} para repasar hoy", style = MaterialTheme.typography.titleSmall)
                            Text("Repetición espaciada con el algoritmo SM-2", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                        }
                    }
                }
            }
            item { CampoTexto(consulta, { vm.consulta.value = it }, "Buscar por título o código") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lenguaje.entries.forEach { l -> Etiqueta(l.nombreVisible, seleccionada = lenguaje == l, alPulsar = { vm.lenguaje.value = if (lenguaje == l) null else l }) }
                    etiquetas.forEach { e -> Etiqueta("#$e", seleccionada = etiqueta == e, alPulsar = { vm.etiqueta.value = if (etiqueta == e) null else e }) }
                }
            }
            val datos = lista
            if (datos == null) item { Cargando(Modifier.height(80.dp)) }
            else if (datos.isEmpty()) item { EstadoVacio(Icons.Outlined.CollectionsBookmark, "Sin fragmentos", "Guarda código útil para reutilizarlo y repasarlo.") }
            else items(datos, key = { it.fragmento.id }) { f ->
                Superficie {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.fragmento.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val proximo = f.repaso?.proximaFecha?.comoFecha()
                            Text(
                                Lenguaje.desdeClave(f.fragmento.lenguaje).nombreVisible + (proximo?.let { " · repaso: ${describirFecha(it)}" } ?: "") +
                                    (if (f.fragmento.listaEtiquetas.isNotEmpty()) " · " + f.fragmento.listaEtiquetas.joinToString(" ") { "#$it" } else ""),
                                style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    BloqueCodigo(f.fragmento.codigo.lines().take(6).joinToString("\n"))
                    Row {
                        IconButton(onClick = { copiar(contexto, f.fragmento.codigo); vm.mensaje.value = "Código copiado" }) { Icon(Icons.Outlined.ContentCopy, "Copiar código") }
                        IconButton(onClick = { vm.abrirEnEditor(f, alAbrirArchivo) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir en el editor") }
                        IconButton(onClick = { alEditar(f.fragmento.id) }) { Icon(Icons.Outlined.Edit, "Editar") }
                        IconButton(onClick = { eliminar = f }) { Icon(Icons.Outlined.Delete, "Eliminar") }
                    }
                }
            }
        }
    }
    eliminar?.let { f ->
        DialogoConfirmacion("Eliminar fragmento", "Se eliminará «${f.fragmento.titulo}» y su historial de repaso.", "Eliminar", true,
            alConfirmar = { vm.eliminar(f.fragmento.id); eliminar = null }, alCerrar = { eliminar = null })
    }
}

private fun describirFecha(fecha: LocalDate): String {
    val dias = ChronoUnit.DAYS.between(LocalDate.now(), fecha)
    return when {
        dias <= 0 -> "hoy"
        dias == 1L -> "mañana"
        dias < 7 -> "en $dias días"
        else -> fecha.format(DateTimeFormatter.ofPattern("d/M/yyyy"))
    }
}

fun copiar(contexto: Context, texto: String) {
    val portapapeles = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    portapapeles.setPrimaryClip(ClipData.newPlainText("Código", texto))
}

@HiltViewModel
class EditarFragmentoViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val repositorio: RepositorioFragmentos
) : ViewModel() {
    val id: Long? = estadoGuardado.get<Long>("fragmentoId")?.takeIf { it > 0 }
    val titulo = MutableStateFlow("")
    val descripcion = MutableStateFlow("")
    val codigo = MutableStateFlow("")
    val etiquetas = MutableStateFlow("")
    val lenguaje = MutableStateFlow(Lenguaje.PYTHON)
    val repaso = MutableStateFlow(true)
    val historial = MutableStateFlow<List<HistorialRepasoEntidad>>(emptyList())
    val error = MutableStateFlow<String?>(null)

    init {
        id?.let { identificador ->
            viewModelScope.launch {
                repositorio.obtener(identificador)?.fragmento?.let { f ->
                    titulo.value = f.titulo; descripcion.value = f.descripcion; codigo.value = f.codigo
                    etiquetas.value = f.etiquetas.replace(",", ", "); lenguaje.value = Lenguaje.desdeClave(f.lenguaje); repaso.value = f.repasoActivo
                }
                historial.value = repositorio.historial(identificador)
            }
        }
    }

    fun guardar(alTerminar: () -> Unit) {
        viewModelScope.launch {
            runCatching { repositorio.guardar(id, titulo.value, descripcion.value, codigo.value, lenguaje.value, etiquetas.value.split(','), repaso.value) }
                .onSuccess { alTerminar() }.onFailure { error.value = it.message }
        }
    }
}

@Composable
fun PantallaEditarFragmento(alVolver: () -> Unit, vm: EditarFragmentoViewModel = hiltViewModel()) {
    val titulo by vm.titulo.collectAsStateWithLifecycle()
    val descripcion by vm.descripcion.collectAsStateWithLifecycle()
    val codigo by vm.codigo.collectAsStateWithLifecycle()
    val etiquetas by vm.etiquetas.collectAsStateWithLifecycle()
    val lenguaje by vm.lenguaje.collectAsStateWithLifecycle()
    val repaso by vm.repaso.collectAsStateWithLifecycle()
    val historial by vm.historial.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding()) {
        EncabezadoPantalla(if (vm.id == null) "Nuevo fragmento" else "Editar fragmento", alVolver = alVolver)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CampoTexto(titulo, { vm.titulo.value = it.take(80) }, "Título")
            CampoTexto(descripcion, { vm.descripcion.value = it.take(300) }, "Descripción", unaLinea = false)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Lenguaje.entries.forEach { l -> Etiqueta(l.nombreVisible, seleccionada = lenguaje == l, alPulsar = { vm.lenguaje.value = l }) }
            }
            CampoTexto(codigo, { vm.codigo.value = it }, "Código", unaLinea = false, monoespaciado = true, lineasMinimas = 8)
            CampoTexto(etiquetas, { vm.etiquetas.value = it }, "Etiquetas separadas por comas")
            Box(Modifier.fillMaxWidth()) { FilaInterruptor("Incluir en repasos", "Programar repasos con repetición espaciada", repaso, { vm.repaso.value = it }) }
            if (historial.isNotEmpty()) {
                Text("Historial de repaso", style = MaterialTheme.typography.titleSmall)
                historial.take(10).forEach { h ->
                    Text("${h.fecha.comoFecha().format(DateTimeFormatter.ofPattern("d/M/yyyy"))} · calidad ${h.calidad}/5 · siguiente en ${h.intervaloResultante} día(s)", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                }
            }
            error?.let { Text(it, color = LocalColoresExtendidos.current.error) }
            BotonPrincipal("Guardar", { vm.guardar(alVolver) }, Modifier.fillMaxWidth(), habilitado = titulo.isNotBlank() && codigo.isNotBlank())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@HiltViewModel
class RepasoViewModel @Inject constructor(private val repositorio: RepositorioFragmentos) : ViewModel() {
    val pendientes: StateFlow<List<FragmentoConRepaso>?> = repositorio.pendientes().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mostrarRespuesta = MutableStateFlow(false)
    val completados = MutableStateFlow(0)

    fun calificar(id: Long, calidad: Int) {
        viewModelScope.launch {
            repositorio.repasar(id, calidad)
            completados.value++
            mostrarRespuesta.value = false
        }
    }
}

@Composable
fun PantallaRepaso(alVolver: () -> Unit, vm: RepasoViewModel = hiltViewModel()) {
    val pendientes by vm.pendientes.collectAsStateWithLifecycle()
    val mostrar by vm.mostrarRespuesta.collectAsStateWithLifecycle()
    val completados by vm.completados.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        EncabezadoPantalla("Repaso", "$completados completados en esta sesión", alVolver = alVolver)
        val lista = pendientes
        when {
            lista == null -> Cargando()
            lista.isEmpty() -> EstadoVacio(Icons.Outlined.TaskAlt, "¡Repaso al día!", "No hay más fragmentos pendientes por hoy.", accion = "Volver", alPulsarAccion = alVolver)
            else -> {
                val f = lista.first()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${lista.size} pendiente(s)", style = MaterialTheme.typography.labelMedium, color = LocalColoresExtendidos.current.textoTenue)
                    Text(f.fragmento.titulo, style = MaterialTheme.typography.headlineSmall)
                    if (f.fragmento.descripcion.isNotBlank()) Text(f.fragmento.descripcion, style = MaterialTheme.typography.bodyLarge)
                    Text("Intenta recordar el código antes de mostrarlo.", style = MaterialTheme.typography.bodyMedium, color = LocalColoresExtendidos.current.textoTenue)
                    if (!mostrar) {
                        BotonPrincipal("Mostrar código", { vm.mostrarRespuesta.value = true }, Modifier.fillMaxWidth())
                    } else {
                        BloqueCodigo(f.fragmento.codigo)
                        Text("¿Qué tan bien lo recordaste?", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotonSecundario("Olvidé", { vm.calificar(f.fragmento.id, 1) }, Modifier.weight(1f))
                            BotonSecundario("Difícil", { vm.calificar(f.fragmento.id, 3) }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotonSecundario("Bien", { vm.calificar(f.fragmento.id, 4) }, Modifier.weight(1f))
                            BotonPrincipal("Fácil", { vm.calificar(f.fragmento.id, 5) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
