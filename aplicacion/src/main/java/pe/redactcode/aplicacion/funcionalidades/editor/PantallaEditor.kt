package pe.redactcode.aplicacion.funcionalidades.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.funcionalidades.ejecucion.VistaPreviaWeb
import pe.redactcode.aplicacion.funcionalidades.problemas.PanelConsolaWeb
import pe.redactcode.aplicacion.funcionalidades.problemas.PanelEjecucion
import pe.redactcode.aplicacion.funcionalidades.problemas.PanelProblemas
import pe.redactcode.aplicacion.funcionalidades.problemas.PanelRevision
import pe.redactcode.aplicacion.nucleo.diseno.CampoTexto
import pe.redactcode.aplicacion.nucleo.diseno.Cargando
import pe.redactcode.aplicacion.nucleo.diseno.DialogoConfirmacion
import pe.redactcode.aplicacion.nucleo.diseno.DialogoEntradaTexto
import pe.redactcode.aplicacion.nucleo.diseno.EncabezadoPantalla
import pe.redactcode.aplicacion.nucleo.diseno.EstadoVacio
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresEditor
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.PuntoEstado
import pe.redactcode.aplicacion.nucleo.diseno.Separador
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.nucleo.mentor.TipoSolicitud
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.Severidad

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEditor(
    alVolver: () -> Unit,
    alAbrirMentor: () -> Unit,
    alPublicarGitHub: (Long, String) -> Unit,
    vm: EditorViewModel = hiltViewModel()
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val preferencias by vm.preferencias.collectAsStateWithLifecycle()
    val controlador = rememberControladorEditores()
    val colores = LocalColoresEditor.current
    val avisos = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    var menuAbierto by remember { mutableStateOf(false) }
    var busquedaAbierta by remember { mutableStateOf(false) }
    var archivosAbiertos by remember { mutableStateOf(false) }
    var dialogoFragmento by remember { mutableStateOf(false) }
    var dialogoNuevoArchivo by remember { mutableStateOf(false) }
    var confirmarSalida by remember { mutableStateOf(false) }

    controlador.alCambiarTexto = vm::alCambiarTexto
    controlador.alMoverCursor = vm::alMoverCursor

    LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let {
            avisos.showSnackbar(it)
            vm.mensaje(null)
        }
    }
    LaunchedEffect(estado.diagnosticos, estado.activa) {
        estado.diagnosticos.forEach { (ruta, lista) -> controlador.aplicarDiagnosticos(ruta, lista) }
    }
    LaunchedEffect(preferencias, colores) {
        controlador.reconfigurarTodos(estado.pestanas.associate { it.ruta to it.lenguaje }, preferencias, colores)
    }

    val salir = { if (estado.hayCambiosSinGuardar) confirmarSalida = true else alVolver() }
    BackHandler(enabled = true) {
        when {
            estado.panel != PanelEditor.NINGUNO -> vm.cerrarPanel()
            busquedaAbierta -> { busquedaAbierta = false; controlador.detenerBusqueda() }
            else -> salir()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        val pestana = estado.pestanaActiva
        EncabezadoPantalla(
            titulo = pestana?.nombre ?: (estado.proyecto?.nombre ?: "Editor"),
            subtitulo = estado.proyecto?.nombre,
            alVolver = salir
        ) {
            IconButton(onClick = { controlador.deshacer() }) { Icon(Icons.AutoMirrored.Outlined.Undo, "Deshacer") }
            IconButton(onClick = { controlador.rehacer() }) { Icon(Icons.AutoMirrored.Outlined.Redo, "Rehacer") }
            IconButton(onClick = { vm.guardar() }, enabled = pestana?.modificada == true) {
                Box {
                    Icon(Icons.Outlined.Save, "Guardar")
                    if (pestana?.modificada == true) Box(Modifier.align(Alignment.TopEnd)) { PuntoEstado(LocalColoresExtendidos.current.acento, 7.dp) }
                }
            }
            if (estado.ejecutando) {
                IconButton(onClick = vm::detenerEjecucion) { Icon(Icons.Outlined.Stop, "Detener", tint = LocalColoresExtendidos.current.error) }
            } else {
                IconButton(onClick = vm::ejecutar, enabled = pestana != null) { Icon(Icons.Outlined.PlayArrow, "Ejecutar", tint = LocalColoresExtendidos.current.acento) }
            }
            Box {
                IconButton(onClick = { menuAbierto = true }) { Icon(Icons.Outlined.MoreVert, "Más opciones") }
                DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                    DropdownMenuItem(text = { Text("Buscar y reemplazar") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, onClick = { menuAbierto = false; busquedaAbierta = true })
                    DropdownMenuItem(text = { Text("Archivos del proyecto") }, leadingIcon = { Icon(Icons.Outlined.Folder, null) }, onClick = { menuAbierto = false; vm.refrescarArchivos(); archivosAbiertos = true })
                    DropdownMenuItem(text = { Text("Nuevo archivo") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, null) }, onClick = { menuAbierto = false; dialogoNuevoArchivo = true })
                    DropdownMenuItem(text = { Text("Problemas") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ViewList, null) }, onClick = { menuAbierto = false; vm.mostrarPanel(PanelEditor.PROBLEMAS) })
                    DropdownMenuItem(text = { Text("Revisar calidad del código") }, leadingIcon = { Icon(Icons.Outlined.Shield, null) }, onClick = { menuAbierto = false; vm.revisar() })
                    DropdownMenuItem(text = { Text("Explicar con el mentor") }, leadingIcon = { Icon(Icons.Outlined.Psychology, null) }, onClick = {
                        menuAbierto = false; vm.prepararMentor(TipoSolicitud.EXPLICAR_CODIGO); alAbrirMentor()
                    })
                    DropdownMenuItem(text = { Text("Explicar línea por línea") }, leadingIcon = { Icon(Icons.Outlined.Psychology, null) }, onClick = {
                        menuAbierto = false; vm.prepararMentor(TipoSolicitud.LINEA_POR_LINEA); alAbrirMentor()
                    })
                    if (pestana?.lenguaje == Lenguaje.JAVASCRIPT) {
                        DropdownMenuItem(text = { Text("Ejecutar como script") }, leadingIcon = { Icon(Icons.Outlined.Terminal, null) }, onClick = { menuAbierto = false; vm.ejecutarComoScript() })
                    }
                    DropdownMenuItem(text = { Text("Guardar como fragmento") }, leadingIcon = { Icon(Icons.Outlined.BookmarkAdd, null) }, onClick = { menuAbierto = false; dialogoFragmento = true })
                    DropdownMenuItem(text = { Text("Publicar en GitHub") }, leadingIcon = { Icon(Icons.Outlined.Cloud, null) }, onClick = {
                        menuAbierto = false
                        val p = estado.proyecto
                        if (p != null && pestana != null) {
                            alcance.launch { vm.guardarTodo(); alPublicarGitHub(p.id, pestana.ruta) }
                        }
                    })
                }
            }
        }

        if (estado.pestanas.isNotEmpty()) {
            BarraPestanas(estado, alActivar = vm::activar, alCerrar = { ruta ->
                val p = estado.pestanas.firstOrNull { it.ruta == ruta }
                if (p?.modificada == true) vm.guardar(ruta, silencioso = true)
                controlador.cerrar(ruta)
                vm.cerrarPestana(ruta)
            })
        }

        if (busquedaAbierta) {
            BarraBusqueda(controlador) { busquedaAbierta = false; controlador.detenerBusqueda() }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                estado.cargando -> Cargando()
                estado.error != null -> EstadoVacio(Icons.Outlined.Folder, "No disponible", estado.error!!)
                pestana == null -> EstadoVacio(Icons.Outlined.Folder, "Ningún archivo abierto", "Abre un archivo del proyecto para empezar.", accion = "Ver archivos", alPulsarAccion = { vm.refrescarArchivos(); archivosAbiertos = true })
                else -> VistaEditor(controlador, pestana.ruta, pestana.textoGuardado, pestana.lenguaje, preferencias, Modifier.fillMaxSize().background(colores.fondo))
            }
        }

        if (estado.panel != PanelEditor.NINGUNO) {
            PanelInferior(estado, vm, controlador, alAbrirMentor)
        }

        BarraEstado(estado) { vm.mostrarPanel(PanelEditor.PROBLEMAS) }
        if (pestana != null) BarraSimbolos(controlador, pestana.lenguaje, Modifier.navigationBarsPadding())
    }

    SnackbarHost(avisos)

    if (archivosAbiertos) {
        ModalBottomSheet(onDismissRequest = { archivosAbiertos = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Text("Archivos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                items(estado.archivosProyecto) { ruta ->
                    Row(Modifier.fillMaxWidth().clickable { archivosAbiertos = false; vm.abrir(ruta) }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(ruta, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (dialogoFragmento) {
        var etiquetas by remember { mutableStateOf("") }
        var titulo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogoFragmento = false },
            title = { Text("Guardar como fragmento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (estado.cursor.seleccion.isNotBlank()) "Se guardará el texto seleccionado." else "Se guardará el archivo completo.", style = MaterialTheme.typography.bodySmall)
                    CampoTexto(titulo, { titulo = it }, "Título")
                    CampoTexto(etiquetas, { etiquetas = it }, "Etiquetas separadas por comas")
                }
            },
            confirmButton = { TextButton(enabled = titulo.isNotBlank(), onClick = { dialogoFragmento = false; vm.guardarFragmento(titulo, etiquetas) }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { dialogoFragmento = false }) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
    if (dialogoNuevoArchivo) {
        DialogoEntradaTexto(
            titulo = "Nuevo archivo", etiqueta = "Nombre con extensión (.py, .js, .html, .css, .json, .txt)",
            validar = { GestorArchivos.validarNombre(it, esArchivo = true) },
            alConfirmar = { dialogoNuevoArchivo = false; vm.crearArchivo(it) },
            alCerrar = { dialogoNuevoArchivo = false }
        )
    }
    if (confirmarSalida) {
        DialogoConfirmacion(
            titulo = "Cambios sin guardar",
            mensaje = "Hay archivos con cambios sin guardar. ¿Quieres guardarlos antes de salir?",
            textoConfirmar = "Guardar y salir",
            alConfirmar = { confirmarSalida = false; alcance.launch { vm.guardarTodo(); alVolver() } },
            alCerrar = { confirmarSalida = false }
        )
    }
}

@Composable
private fun BarraPestanas(estado: EstadoEditor, alActivar: (String) -> Unit, alCerrar: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        estado.pestanas.forEach { p ->
            val activa = p.ruta == estado.activa
            val errores = estado.diagnosticos[p.ruta].orEmpty().count { it.severidad == Severidad.ERROR }
            Row(
                Modifier
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(if (activa) LocalColoresEditor.current.fondo else MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { alActivar(p.ruta) }
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    p.nombre,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (errores > 0) LocalColoresExtendidos.current.error else if (activa) MaterialTheme.colorScheme.onSurface else LocalColoresExtendidos.current.textoTenue,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (p.modificada) {
                    Spacer(Modifier.width(6.dp))
                    PuntoEstado(LocalColoresExtendidos.current.acento, 6.dp)
                }
                IconButton(onClick = { alCerrar(p.ruta) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, "Cerrar ${p.nombre}", modifier = Modifier.size(16.dp), tint = LocalColoresExtendidos.current.textoTenue)
                }
            }
        }
    }
}

@Composable
private fun BarraBusqueda(controlador: ControladorEditores, alCerrar: () -> Unit) {
    var consulta by remember { mutableStateOf("") }
    var reemplazo by remember { mutableStateOf("") }
    var mayusculas by remember { mutableStateOf(false) }
    var palabra by remember { mutableStateOf(false) }
    var total by remember { mutableIntStateOf(0) }
    LaunchedEffect(consulta, mayusculas, palabra) {
        controlador.buscar(consulta, mayusculas, palabra)
        kotlinx.coroutines.delay(250)
        total = controlador.coincidencias()
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CampoTexto(consulta, { consulta = it }, "Buscar", opcionesTeclado = KeyboardOptions.Default) }
            IconButton(onClick = { controlador.anterior() }) { Icon(Icons.Outlined.KeyboardArrowUp, "Anterior") }
            IconButton(onClick = { controlador.siguiente() }) { Icon(Icons.Outlined.KeyboardArrowDown, "Siguiente") }
            IconButton(onClick = alCerrar) { Icon(Icons.Outlined.Close, "Cerrar búsqueda") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CampoTexto(reemplazo, { reemplazo = it }, "Reemplazar con") }
            TextButton(onClick = { controlador.reemplazarActual(reemplazo) }, enabled = consulta.isNotEmpty()) { Text("Uno") }
            TextButton(onClick = { controlador.reemplazarTodo(reemplazo) }, enabled = consulta.isNotEmpty()) { Text("Todos") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            pe.redactcode.aplicacion.nucleo.diseno.Etiqueta("Aa", seleccionada = mayusculas, alPulsar = { mayusculas = !mayusculas })
            pe.redactcode.aplicacion.nucleo.diseno.Etiqueta("Palabra completa", seleccionada = palabra, alPulsar = { palabra = !palabra })
            Spacer(Modifier.weight(1f))
            if (consulta.isNotEmpty()) Text("$total coincidencias", style = MaterialTheme.typography.labelMedium, color = LocalColoresExtendidos.current.textoTenue)
        }
    }
}

@Composable
private fun BarraEstado(estado: EstadoEditor, alPulsarProblemas: () -> Unit) {
    val errores = estado.diagnosticosActivos.count { it.severidad == Severidad.ERROR }
    val advertencias = estado.diagnosticosActivos.count { it.severidad == Severidad.ADVERTENCIA }
    Separador()
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ln ${estado.cursor.linea}, Col ${estado.cursor.columna}", style = MaterialTheme.typography.labelSmall, color = LocalColoresExtendidos.current.textoTenue)
        Text(estado.pestanaActiva?.lenguaje?.nombreVisible ?: "", style = MaterialTheme.typography.labelSmall, color = LocalColoresExtendidos.current.textoTenue)
        Spacer(Modifier.weight(1f))
        if (estado.validando) Text("Validando…", style = MaterialTheme.typography.labelSmall, color = LocalColoresExtendidos.current.textoTenue)
        Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = alPulsarProblemas).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            PuntoEstado(if (errores > 0) LocalColoresExtendidos.current.error else LocalColoresExtendidos.current.exito)
            Spacer(Modifier.width(4.dp))
            Text("$errores", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            PuntoEstado(LocalColoresExtendidos.current.advertencia)
            Spacer(Modifier.width(4.dp))
            Text("$advertencias", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PanelInferior(estado: EstadoEditor, vm: EditorViewModel, controlador: ControladorEditores, alAbrirMentor: () -> Unit) {
    val alto = if (estado.panel == PanelEditor.VISTA_PREVIA) 460.dp else 300.dp
    Column(Modifier.fillMaxWidth().height(alto).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Separador()
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (estado.panel) {
                    PanelEditor.PROBLEMAS -> "Problemas"
                    PanelEditor.EJECUCION -> "Ejecución"
                    PanelEditor.REVISION -> "Revisión de código"
                    PanelEditor.VISTA_PREVIA -> "Vista previa · ${estado.archivoVistaPrevia}"
                    PanelEditor.NINGUNO -> ""
                },
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)
            )
            if (estado.panel == PanelEditor.VISTA_PREVIA) IconButton(onClick = vm::recargarVistaPrevia) { Icon(Icons.Outlined.Refresh, "Recargar vista previa") }
            if (estado.panel == PanelEditor.EJECUCION && !estado.ejecutando) IconButton(onClick = vm::ejecutar) { Icon(Icons.Outlined.PlayArrow, "Volver a ejecutar") }
            IconButton(onClick = vm::cerrarPanel) { Icon(Icons.Outlined.Close, "Cerrar panel") }
        }
        val irA: (Int) -> Unit = { linea -> controlador.irA(linea, 1) }
        when (estado.panel) {
            PanelEditor.PROBLEMAS -> PanelProblemas(
                estado.diagnosticosActivos, estado.motorValidacion, estado.duracionValidacionMs,
                propuestaPara = vm::propuesta,
                alIrALinea = { controlador.irA(it.linea, it.columna) },
                alCorregir = { propuesta ->
                    val ruta = estado.activa
                    val nuevo = vm.aplicarPropuesta(propuesta)
                    if (ruta != null && nuevo != null) {
                        controlador.reemplazarTexto(ruta, nuevo)
                        vm.alCambiarTexto(ruta, nuevo)
                    }
                },
                alPreguntarMentor = { vm.prepararMentor(TipoSolicitud.EXPLICAR_ERROR, it); alAbrirMentor() },
                modifier = Modifier.fillMaxSize()
            )
            PanelEditor.EJECUCION -> PanelEjecucion(
                estado.resultado, estado.ejecutando, estado.entradaPrograma, estado.pestanaActiva?.lenguaje == Lenguaje.PYTHON,
                vm::cambiarEntrada, irA, Modifier.fillMaxSize()
            )
            PanelEditor.REVISION -> PanelRevision(estado.revision, estado.revisando, irA, Modifier.fillMaxSize())
            PanelEditor.VISTA_PREVIA -> {
                val proyecto = estado.proyecto
                val html = estado.archivoVistaPrevia
                if (proyecto != null && html != null) {
                    val carpeta = remember(proyecto.carpeta) { java.io.File(vm.carpetaRaiz(), proyecto.carpeta) }
                    VistaPreviaWeb(
                        carpetaProyecto = carpeta, archivoHtml = html, version = estado.versionVistaPrevia,
                        alMensajeConsola = { nivel, mensaje, linea -> vm.agregarConsola(LineaConsola(nivel, mensaje, linea)) },
                        alNavegacionBloqueada = { vm.mensaje("Por seguridad, la vista previa no abre enlaces externos") },
                        alProcesoDetenido = { vm.mensaje("La vista previa se detuvo por falta de memoria o un error del navegador. Ábrela de nuevo."); vm.cerrarPanel() },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    Separador()
                    Text("Consola", style = MaterialTheme.typography.labelMedium, color = LocalColoresExtendidos.current.textoTenue, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    PanelConsolaWeb(estado.consolaWeb, Modifier.fillMaxWidth().height(110.dp))
                }
            }
            PanelEditor.NINGUNO -> Unit
        }
    }
}
