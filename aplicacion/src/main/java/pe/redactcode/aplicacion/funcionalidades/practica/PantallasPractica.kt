package pe.redactcode.aplicacion.funcionalidades.practica

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.redactcode.aplicacion.datos.RepositorioRetos
import pe.redactcode.aplicacion.datos.RetoConEstado
import pe.redactcode.aplicacion.funcionalidades.editor.BarraSimbolos
import pe.redactcode.aplicacion.funcionalidades.editor.VistaEditor
import pe.redactcode.aplicacion.funcionalidades.editor.rememberControladorEditores
import pe.redactcode.aplicacion.funcionalidades.problemas.Consola
import pe.redactcode.aplicacion.nucleo.base_datos.IntentoRetoEntidad
import pe.redactcode.aplicacion.nucleo.diseno.BarraProgreso
import pe.redactcode.aplicacion.nucleo.diseno.BotonPrincipal
import pe.redactcode.aplicacion.nucleo.diseno.Cargando
import pe.redactcode.aplicacion.nucleo.diseno.EncabezadoPantalla
import pe.redactcode.aplicacion.nucleo.diseno.EstadoVacio
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresEditor
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.Separador
import pe.redactcode.aplicacion.nucleo.diseno.Superficie
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.nucleo.modelo.CategoriaConcepto
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.retos.ComparadorValores
import pe.redactcode.nucleo.retos.Dificultad
import pe.redactcode.nucleo.retos.ResultadoPruebas
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

val categoriasPractica = listOf(
    CategoriaConcepto.FUNDAMENTOS, CategoriaConcepto.VARIABLES, CategoriaConcepto.CONDICIONALES, CategoriaConcepto.BUCLES,
    CategoriaConcepto.FUNCIONES, CategoriaConcepto.LISTAS, CategoriaConcepto.CADENAS, CategoriaConcepto.OBJETOS,
    CategoriaConcepto.ALGORITMOS, CategoriaConcepto.ESTRUCTURAS_DATOS
)

data class FiltrosRetos(val categoria: CategoriaConcepto? = null, val dificultad: Dificultad? = null, val lenguaje: Lenguaje? = null, val soloPendientes: Boolean = false)

@HiltViewModel
class PracticaViewModel @Inject constructor(
    private val repositorio: RepositorioRetos,
    @ApplicationContext private val contexto: Context
) : ViewModel() {
    val filtros = MutableStateFlow(FiltrosRetos())
    val retos: StateFlow<List<RetoConEstado>?> = combine(repositorio.retos, filtros) { lista, f ->
        lista.filter { r ->
            (f.categoria == null || r.definicion.categoria == f.categoria) &&
                (f.dificultad == null || r.definicion.dificultad == f.dificultad) &&
                (f.lenguaje == null || r.definicion.lenguaje == f.lenguaje) &&
                (!f.soloPendientes || !r.entidad.resuelto)
        }.sortedWith(compareBy({ it.definicion.dificultad.orden }, { categoriasPractica.indexOf(it.definicion.categoria) }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val totales: StateFlow<Pair<Int, Int>> = repositorio.retos.combine(filtros) { l, _ -> l.count { it.entidad.resuelto } to l.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)
    val historial: StateFlow<List<IntentoRetoEntidad>> = repositorio.historial.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mensaje = MutableStateFlow<String?>(null)

    fun importar(uri: android.net.Uri) {
        viewModelScope.launch {
            val resultado = runCatching {
                val texto = withContext(Dispatchers.IO) {
                    contexto.contentResolver.openInputStream(uri)?.use { flujo ->
                        val bytes = flujo.readBytes()
                        require(bytes.size <= 2 * 1024 * 1024) { "El archivo supera 2 MB" }
                        String(bytes, Charsets.UTF_8)
                    } ?: error("No se pudo leer el archivo")
                }
                repositorio.importar(texto)
            }
            mensaje.value = resultado.fold(
                { r -> "Se importaron ${r.retos.size} retos" + if (r.errores.isNotEmpty()) ". Avisos: ${r.errores.take(2).joinToString("; ")}" else "" },
                { it.message ?: "No se pudo importar" }
            )
        }
    }
}

@Composable
fun PantallaPractica(alAbrirReto: (String) -> Unit, vm: PracticaViewModel = hiltViewModel()) {
    val retos by vm.retos.collectAsStateWithLifecycle()
    val filtros by vm.filtros.collectAsStateWithLifecycle()
    val totales by vm.totales.collectAsStateWithLifecycle()
    val historial by vm.historial.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var pestana by remember { mutableIntStateOf(0) }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importar) }
    LaunchedEffect(mensaje) { mensaje?.let { avisos.showSnackbar(it); vm.mensaje.value = null } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            EncabezadoPantalla("Práctica", "${totales.first} de ${totales.second} retos resueltos") {
                IconButton(onClick = { selector.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Icon(Icons.Outlined.FileUpload, "Importar retos en formato JSON") }
            }
            PrimaryTabRow(selectedTabIndex = pestana, containerColor = MaterialTheme.colorScheme.background) {
                Tab(pestana == 0, { pestana = 0 }, text = { Text("Retos") })
                Tab(pestana == 1, { pestana = 1 }, text = { Text("Historial") })
            }
            if (pestana == 0) {
                Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Etiqueta("Pendientes", seleccionada = filtros.soloPendientes, alPulsar = { vm.filtros.value = filtros.copy(soloPendientes = !filtros.soloPendientes) })
                        listOf(Lenguaje.PYTHON, Lenguaje.JAVASCRIPT).forEach { l ->
                            Etiqueta(l.nombreVisible, seleccionada = filtros.lenguaje == l, alPulsar = { vm.filtros.value = filtros.copy(lenguaje = if (filtros.lenguaje == l) null else l) })
                        }
                        Dificultad.entries.forEach { d ->
                            Etiqueta(d.nombreVisible, seleccionada = filtros.dificultad == d, alPulsar = { vm.filtros.value = filtros.copy(dificultad = if (filtros.dificultad == d) null else d) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categoriasPractica.forEach { c ->
                            Etiqueta(c.nombreVisible, seleccionada = filtros.categoria == c, alPulsar = { vm.filtros.value = filtros.copy(categoria = if (filtros.categoria == c) null else c) })
                        }
                    }
                }
                val lista = retos
                when {
                    lista == null -> Cargando()
                    lista.isEmpty() -> EstadoVacio(Icons.Outlined.Bolt, "Sin retos", "No hay retos con estos filtros.")
                    else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(lista, key = { it.definicion.id }) { r ->
                            Superficie(alPulsar = { alAbrirReto(r.definicion.id) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (r.entidad.resuelto) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = if (r.entidad.resuelto) "Resuelto" else "Pendiente",
                                        tint = if (r.entidad.resuelto) LocalColoresExtendidos.current.exito else LocalColoresExtendidos.current.textoTenue
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(r.definicion.titulo, style = MaterialTheme.typography.titleSmall)
                                        Text("${r.definicion.categoria.nombreVisible} · ${r.definicion.dificultad.nombreVisible} · ${r.definicion.minutosEstimados} min", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                                    }
                                    Etiqueta(r.definicion.lenguaje.nombreVisible)
                                }
                            }
                        }
                    }
                }
            } else {
                if (historial.isEmpty()) EstadoVacio(Icons.Outlined.History, "Sin intentos", "Cuando evalúes un reto, tus intentos aparecerán aquí.")
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(historial.take(200)) { i ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (i.aprobado) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (i.aprobado) LocalColoresExtendidos.current.exito else LocalColoresExtendidos.current.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(i.retoId, style = MaterialTheme.typography.bodyMedium)
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(i.fecha)), style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                            }
                            Text("${i.aprobados}/${i.total} · ${i.puntaje}%", style = MaterialTheme.typography.labelLarge)
                        }
                        Separador()
                    }
                }
            }
        }
        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter))
    }
}

@HiltViewModel
class RetoViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val repositorio: RepositorioRetos,
    preferencias: RepositorioPreferencias
) : ViewModel() {
    private val retoId: String = checkNotNull(estadoGuardado.get<String>("retoId"))
    val reto: StateFlow<RetoConEstado?> = repositorio.observar(retoId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val intentos: StateFlow<List<IntentoRetoEntidad>> = repositorio.intentos(retoId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val preferencias: StateFlow<PreferenciasUsuario> = preferencias.preferencias.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasUsuario())
    val resultado = MutableStateFlow<ResultadoPruebas?>(null)
    val evaluando = MutableStateFlow(false)
    var codigoActual: String = ""

    fun evaluar() {
        val r = reto.value ?: return
        viewModelScope.launch {
            evaluando.value = true
            resultado.value = runCatching { repositorio.evaluar(r, codigoActual) }.getOrElse { ResultadoPruebas(emptyList(), it.message ?: "Error al evaluar", null, "", 0) }
            evaluando.value = false
        }
    }

    fun guardarBorrador() {
        val r = reto.value ?: return
        viewModelScope.launch { repositorio.guardarCodigo(r.definicion.id, codigoActual) }
    }
}

@Composable
fun PantallaReto(alVolver: () -> Unit, vm: RetoViewModel = hiltViewModel()) {
    val reto by vm.reto.collectAsStateWithLifecycle()
    val resultado by vm.resultado.collectAsStateWithLifecycle()
    val evaluando by vm.evaluando.collectAsStateWithLifecycle()
    val intentos by vm.intentos.collectAsStateWithLifecycle()
    val preferencias by vm.preferencias.collectAsStateWithLifecycle()
    val controlador = rememberControladorEditores()
    var pestana by remember { mutableIntStateOf(0) }
    var pistasVisibles by remember { mutableIntStateOf(0) }
    var reinicio by remember { mutableIntStateOf(0) }
    val r = reto
    if (r == null) {
        Cargando()
        return
    }
    val d = r.definicion
    val inicial = remember(d.id, reinicio) { if (reinicio == 0) r.entidad.codigoGuardado ?: d.codigoInicial else d.codigoInicial }
    LaunchedEffect(inicial) { vm.codigoActual = inicial }
    controlador.alCambiarTexto = { _, texto -> vm.codigoActual = texto }
    LaunchedEffect(resultado) { if (resultado != null) pestana = 2 }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.guardarBorrador() } }

    Column(Modifier.fillMaxSize().imePadding()) {
        EncabezadoPantalla(d.titulo, "${d.lenguaje.nombreVisible} · ${d.dificultad.nombreVisible}", alVolver = { vm.guardarBorrador(); alVolver() }) {
            IconButton(onClick = { reinicio++; controlador.cerrar("reto:${d.id}:${reinicio - 1}") }) { Icon(Icons.Outlined.RestartAlt, "Restablecer código inicial") }
            IconButton(onClick = vm::evaluar, enabled = !evaluando) { Icon(Icons.Outlined.PlayArrow, "Evaluar solución", tint = LocalColoresExtendidos.current.acento) }
        }
        PrimaryTabRow(selectedTabIndex = pestana, containerColor = MaterialTheme.colorScheme.background) {
            Tab(pestana == 0, { pestana = 0 }, text = { Text("Enunciado") })
            Tab(pestana == 1, { pestana = 1 }, text = { Text("Código") })
            Tab(pestana == 2, { pestana = 2 }, text = { Text("Resultado") })
        }
        Box(Modifier.weight(1f)) {
            when (pestana) {
                0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Etiqueta(d.categoria.nombreVisible)
                        Etiqueta(d.dificultad.nombreVisible)
                        if (r.entidad.resuelto) Etiqueta("Resuelto", color = LocalColoresExtendidos.current.exito)
                    }
                    Text(d.enunciado, style = MaterialTheme.typography.bodyLarge)
                    Text("Ejemplos", style = MaterialTheme.typography.titleSmall)
                    d.pruebas.filterNot { it.oculto }.forEach { caso ->
                        pe.redactcode.aplicacion.nucleo.diseno.BloqueCodigo("${d.funcion}(${ComparadorValores.formatearEntrada(caso.entrada)}) → ${ComparadorValores.formatear(caso.esperado)}")
                    }
                    val ocultas = d.pruebas.count { it.oculto }
                    if (ocultas > 0) Text("Además hay $ocultas prueba(s) oculta(s).", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                    if (d.pistas.isNotEmpty()) {
                        Text("Pistas", style = MaterialTheme.typography.titleSmall)
                        d.pistas.take(pistasVisibles).forEachIndexed { i, pista -> Text("${i + 1}. $pista", style = MaterialTheme.typography.bodyMedium) }
                        if (pistasVisibles < d.pistas.size) {
                            pe.redactcode.aplicacion.nucleo.diseno.BotonSecundario("Ver pista ${pistasVisibles + 1}", { pistasVisibles++ }, icono = Icons.Outlined.Lightbulb)
                        }
                    }
                    if (intentos.isNotEmpty()) {
                        Text("Tus intentos", style = MaterialTheme.typography.titleSmall)
                        intentos.take(5).forEach { i ->
                            Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(i.fecha))} · ${i.aprobados}/${i.total} pruebas", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                        }
                    }
                    BotonPrincipal("Ir al código", { pestana = 1 }, Modifier.fillMaxWidth())
                }
                1 -> Column(Modifier.fillMaxSize()) {
                    VistaEditor(controlador, "reto:${d.id}:$reinicio", inicial, d.lenguaje, preferencias, Modifier.weight(1f).fillMaxWidth())
                    BarraSimbolos(controlador, d.lenguaje, Modifier.navigationBarsPadding())
                }
                else -> PanelResultado(resultado, evaluando, vm::evaluar)
            }
        }
    }
}

@Composable
private fun PanelResultado(resultado: ResultadoPruebas?, evaluando: Boolean, alEvaluar: () -> Unit) {
    if (evaluando) {
        Cargando()
        return
    }
    if (resultado == null) {
        EstadoVacio(Icons.Outlined.PlayArrow, "Sin evaluar", "Escribe tu solución y pulsa Evaluar para ejecutar las pruebas automáticas en tu dispositivo.", accion = "Evaluar", alPulsarAccion = alEvaluar)
        return
    }
    val ext = LocalColoresExtendidos.current
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            when {
                resultado.errorGeneral != null -> {
                    Text("No se pudo evaluar", style = MaterialTheme.typography.titleMedium, color = ext.error)
                    Text(resultado.errorGeneral + (resultado.lineaError?.let { " (línea $it)" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                }
                resultado.todosAprobados -> {
                    Text("¡Reto superado!", style = MaterialTheme.typography.headlineSmall, color = ext.exito)
                    Text("Todas las pruebas pasaron en ${resultado.duracionMs} ms.", style = MaterialTheme.typography.bodyMedium, color = ext.textoTenue)
                }
                else -> {
                    Text("${resultado.aprobados} de ${resultado.total} pruebas aprobadas", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    BarraProgreso(resultado.aprobados / resultado.total.toFloat(), color = ext.advertencia)
                }
            }
        }
        items(resultado.casos) { c ->
            Superficie {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (c.aprobado) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (c.aprobado) ext.exito else ext.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Prueba ${c.indice + 1}" + if (c.caso.oculto) " (oculta)" else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
                if (!c.caso.oculto || !c.aprobado) {
                    Spacer(Modifier.height(6.dp))
                    Text("Entrada: ${if (c.caso.oculto) "oculta" else ComparadorValores.formatearEntrada(c.caso.entrada)}", style = MaterialTheme.typography.bodySmall)
                    if (!c.caso.oculto) Text("Esperado: ${ComparadorValores.formatear(c.caso.esperado)}", style = MaterialTheme.typography.bodySmall)
                    c.obtenido?.let { Text("Obtenido: $it", style = MaterialTheme.typography.bodySmall, color = if (c.aprobado) ext.textoTenue else ext.error) }
                    c.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ext.error) }
                }
            }
        }
        if (resultado.salida.isNotBlank()) {
            item {
                Text("Salida del programa", style = MaterialTheme.typography.labelMedium, color = ext.textoTenue)
                Consola(resultado.salida, LocalColoresEditor.current.texto)
            }
        }
    }
}
