package pe.redactcode.aplicacion.funcionalidades.espacio

import pe.redactcode.aplicacion.nucleo.diseno.anchoVentanaDp
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.redactcode.aplicacion.datos.RepositorioExtensiones
import pe.redactcode.aplicacion.funcionalidades.ejecucion.VistaPreviaWeb
import pe.redactcode.aplicacion.funcionalidades.editor.BarraSimbolos
import pe.redactcode.aplicacion.funcionalidades.editor.EditorViewModel
import pe.redactcode.aplicacion.funcionalidades.editor.EstadoEditor
import pe.redactcode.aplicacion.funcionalidades.editor.LineaConsola
import pe.redactcode.aplicacion.funcionalidades.editor.PanelEditor
import pe.redactcode.aplicacion.funcionalidades.editor.VistaEditor
import pe.redactcode.aplicacion.funcionalidades.editor.rememberControladorEditores
import pe.redactcode.aplicacion.funcionalidades.editor.ControladorEditores
import pe.redactcode.aplicacion.funcionalidades.problemas.PanelConsolaWeb
import pe.redactcode.aplicacion.funcionalidades.problemas.PanelEjecucion
import pe.redactcode.aplicacion.funcionalidades.problemas.PanelProblemas
import pe.redactcode.aplicacion.funcionalidades.problemas.PanelRevision
import pe.redactcode.aplicacion.nucleo.diseno.CampoTexto
import pe.redactcode.aplicacion.nucleo.diseno.Cargando
import pe.redactcode.aplicacion.nucleo.diseno.DialogoConfirmacion
import pe.redactcode.aplicacion.nucleo.diseno.DialogoEntradaTexto
import pe.redactcode.aplicacion.nucleo.diseno.EstadoVacio
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.LocalAnimacionesActivas
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresEditor
import pe.redactcode.aplicacion.nucleo.diseno.LocalDimensiones
import pe.redactcode.aplicacion.nucleo.diseno.LocalTokens
import pe.redactcode.aplicacion.nucleo.diseno.PuntoEstado
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.nucleo.ide.ExtractorSimbolos
import pe.redactcode.nucleo.ide.OpcionesBusqueda
import pe.redactcode.nucleo.ide.SimboloCodigo
import pe.redactcode.nucleo.mentor.TipoSolicitud
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.Severidad

enum class Lateral(val titulo: String, val icono: ImageVector) {
    EXPLORADOR("Explorador", Icons.Outlined.Folder),
    BUSQUEDA("Buscar", Icons.Outlined.Search),
    CONTROL("Control de código", Icons.Outlined.AccountTree),
    AGENTE("Agente de código", Icons.Outlined.SmartToy),
    EXTENSIONES("Extensiones", Icons.Outlined.Extension)
}

private const val UMBRAL_ANCHO_DP = 600

@Composable
fun PantallaEspacio(
    alVolver: () -> Unit,
    alAbrirMentor: () -> Unit,
    alPublicarGitHub: (Long, String) -> Unit,
    alAbrirAjustes: () -> Unit,
    alAbrirGitHub: () -> Unit,
    vm: EditorViewModel = hiltViewModel(),
    explorador: ExploradorEspacioViewModel = hiltViewModel(),
    busqueda: BusquedaEspacioViewModel = hiltViewModel(),
    control: ControlCodigoViewModel = hiltViewModel(),
    terminal: TerminalViewModel = hiltViewModel(),
    agente: AgenteViewModel = hiltViewModel(),
    extVm: ExtensionesViewModel = hiltViewModel()
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val prefs by vm.preferencias.collectAsStateWithLifecycle()
    val extensiones by extVm.extensiones.collectAsStateWithLifecycle()
    val estadoBusqueda by busqueda.estado.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    val anim = LocalAnimacionesActivas.current
    val ancha = anchoVentanaDp() >= UMBRAL_ANCHO_DP
    val alcance = rememberCoroutineScope()
    val contextoApp = androidx.compose.ui.platform.LocalContext.current
    val controlador = rememberControladorEditores()
    val avisos = remember { SnackbarHostState() }

    var lateral by rememberSaveable { mutableStateOf(Lateral.EXPLORADOR) }
    var lateralVisible by rememberSaveable { mutableStateOf(false) }
    var inicializado by rememberSaveable { mutableStateOf(false) }
    var paleta by remember { mutableStateOf<ModoPaleta?>(null) }
    var busquedaArchivo by remember { mutableStateOf(false) }
    var menuAbierto by remember { mutableStateOf(false) }
    var dialogoFragmento by remember { mutableStateOf(false) }
    var dialogoNuevoArchivo by remember { mutableStateOf(false) }
    var confirmarSalida by remember { mutableStateOf(false) }
    var panelMaximizado by remember { mutableStateOf(false) }
    var fraccionPanel by remember { mutableFloatStateOf(0.4f) }
    var pendiente by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }

    val fragmentos = remember(extensiones) { extensiones.filter { it.activa }.flatMap { it.manifiesto.fragmentos } }
    val temaElegido = remember(extensiones, prefs.temaEditor) {
        extensiones.filter { it.activa }.flatMap { e -> e.manifiesto.temas.mapIndexed { i, tema -> "${e.manifiesto.id}:$i" to tema } }.firstOrNull { it.first == prefs.temaEditor }?.second
    }
    val coloresEditor = RepositorioExtensiones.aplicarTema(LocalColoresEditor.current, temaElegido)

    controlador.alCambiarTexto = vm::alCambiarTexto
    controlador.alMoverCursor = vm::alMoverCursor
    controlador.fragmentos = fragmentos

    LaunchedEffect(Unit) {
        merge(explorador.mensajes, busqueda.mensajes, control.mensajes, terminal.mensajes, agente.mensajes, extVm.mensajes).collect { texto ->
            alcance.launch { avisos.showSnackbar(texto) }
        }
    }
    LaunchedEffect(estado.mensaje) { estado.mensaje?.let { alcance.launch { avisos.showSnackbar(it) }; vm.mensaje(null) } }
    LaunchedEffect(Unit) { vm.cambiosExternos.collect { controlador.reemplazarDocumento(it.ruta, it.texto) } }
    LaunchedEffect(Unit) {
        explorador.renombrados.collect { (anterior, nueva) ->
            controlador.claves().filter { it == anterior || it.startsWith("$anterior/") }.forEach { controlador.renombrarClave(it, nueva + it.removePrefix(anterior)) }
            vm.rutaRenombrada(anterior, nueva)
        }
    }
    LaunchedEffect(Unit) { explorador.eliminados.collect { ruta -> vm.rutaEliminada(ruta).forEach(controlador::cerrar) } }
    LaunchedEffect(Unit) { explorador.creados.collect { ruta -> vm.refrescarArchivos(); vm.abrir(ruta) } }
    LaunchedEffect(Unit) {
        merge(busqueda.archivosCambiados, control.archivosCambiados, terminal.archivosCambiados, agente.archivosCambiados).collect { rutas ->
            vm.recargarDesdeDisco(rutas)
            explorador.refrescar()
        }
    }
    LaunchedEffect(estado.diagnosticos, estado.activa) { estado.diagnosticos.forEach { (ruta, lista) -> controlador.aplicarDiagnosticos(ruta, lista) } }
    LaunchedEffect(prefs, coloresEditor, fragmentos) { controlador.reconfigurarTodos(estado.pestanas.associate { it.ruta to it.lenguaje }, prefs, coloresEditor) }
    LaunchedEffect(pendiente, estado.activa) {
        val p = pendiente ?: return@LaunchedEffect
        var intentos = 0
        while (intentos < 40) {
            if (estado.activa == p.first && p.first in controlador.claves()) {
                delay(80)
                controlador.irA(p.second, p.third)
                pendiente = null
                break
            }
            delay(60)
            intentos++
        }
    }

    fun irAArchivo(ruta: String, linea: Int, columna: Int) { vm.abrir(ruta); pendiente = Triple(ruta, linea, columna) }
    val salir = { if (estado.hayCambiosSinGuardar) confirmarSalida = true else alVolver() }
    val guardarTodo: () -> Unit = { alcance.launch { vm.guardarTodo() } }
    val pestana = estado.pestanaActiva

    fun irADefinicion() {
        val p = pestana ?: return
        val pos = controlador.cursorActivo() ?: return
        val nombre = ExtractorSimbolos.palabraEn(p.textoActual, pos.first, pos.second)
        if (nombre == null) { vm.mensaje("Coloca el cursor sobre un identificador"); return }
        alcance.launch {
            val d = vm.buscarDefinicion(nombre)
            if (d == null) vm.mensaje("No se encontró la definición de «$nombre»") else irAArchivo(d.first, d.second, d.third)
        }
    }

    fun buscarReferencias() {
        val p = pestana ?: return
        val pos = controlador.cursorActivo() ?: return
        val nombre = ExtractorSimbolos.palabraEn(p.textoActual, pos.first, pos.second)
        if (nombre == null) { vm.mensaje("Coloca el cursor sobre un identificador"); return }
        busqueda.opciones(OpcionesBusqueda(nombre, distinguirMayusculas = true, palabraCompleta = true, excluir = prefs.excluirBusqueda))
        lateral = Lateral.BUSQUEDA
        lateralVisible = true
    }

    val lenguajeActivo = pestana?.lenguaje ?: Lenguaje.TEXTO
    val comandos = listOf(
        ComandoIde("guardar", "Guardar", "Archivo", enMenu = false, habilitado = pestana?.modificada == true) { vm.guardar() },
        ComandoIde("guardar_todo", "Guardar todo", "Archivo", habilitado = estado.hayCambiosSinGuardar) { guardarTodo() },
        ComandoIde("nuevo_archivo", "Nuevo archivo", "Archivo", enMenu = true) { dialogoNuevoArchivo = true },
        ComandoIde("cerrar_pestana", "Cerrar pestaña", "Archivo", habilitado = pestana != null) { pestana?.let { p -> if (p.modificada) vm.guardar(p.ruta, true); controlador.cerrar(p.ruta); vm.cerrarPestana(p.ruta) } },
        ComandoIde("ir_archivo", "Ir a archivo…", "Navegación") { paleta = ModoPaleta.ARCHIVOS },
        ComandoIde("ir_linea", "Ir a línea…", "Navegación", habilitado = pestana != null) { paleta = ModoPaleta.LINEA },
        ComandoIde("ir_simbolo", "Ir a símbolo del archivo…", "Navegación", habilitado = pestana != null) { paleta = ModoPaleta.SIMBOLOS },
        ComandoIde("ir_definicion", "Ir a definición", "Navegación", habilitado = pestana != null) { irADefinicion() },
        ComandoIde("referencias", "Buscar referencias", "Navegación", habilitado = pestana != null) { buscarReferencias() },
        ComandoIde("deshacer", "Deshacer", "Editar", habilitado = pestana != null) { controlador.deshacer() },
        ComandoIde("rehacer", "Rehacer", "Editar", habilitado = pestana != null) { controlador.rehacer() },
        ComandoIde("buscar_archivo", "Buscar y reemplazar en el archivo", "Editar", enMenu = true, habilitado = pestana != null) { busquedaArchivo = true },
        ComandoIde("buscar_proyecto", "Buscar en el proyecto", "Editar") { lateral = Lateral.BUSQUEDA; lateralVisible = true },
        ComandoIde("seleccionar_todo", "Seleccionar todo", "Editar", habilitado = pestana != null) { controlador.seleccionarTodo() },
        ComandoIde("duplicar", "Duplicar línea o selección", "Editar", habilitado = pestana != null) { controlador.duplicarLinea() },
        ComandoIde("subir_linea", "Mover línea arriba", "Editar", habilitado = pestana != null) { controlador.moverLinea(true) },
        ComandoIde("bajar_linea", "Mover línea abajo", "Editar", habilitado = pestana != null) { controlador.moverLinea(false) },
        ComandoIde("comentar", "Alternar comentario", "Editar", habilitado = pestana != null) { controlador.alternarComentario(lenguajeActivo) },
        ComandoIde("formatear", "Formatear documento", "Editar", enMenu = true, habilitado = pestana != null) { vm.formatear() },
        ComandoIde("comparar_guardado", "Comparar con la versión guardada", "Editar", enMenu = true, habilitado = pestana?.modificada == true) { vm.compararConGuardado() },
        ComandoIde("ejecutar", "Ejecutar archivo", "Ejecutar", habilitado = pestana != null && !estado.ejecutando) { vm.ejecutar() },
        ComandoIde("detener", "Detener ejecución", "Ejecutar", habilitado = estado.ejecutando) { vm.detenerEjecucion() },
        ComandoIde("ejecutar_script", "Ejecutar como script", "Ejecutar", enMenu = lenguajeActivo == Lenguaje.JAVASCRIPT, habilitado = lenguajeActivo == Lenguaje.JAVASCRIPT) { vm.ejecutarComoScript() },
        ComandoIde("vista_previa", "Abrir vista previa web", "Ejecutar", habilitado = estado.archivosProyecto.any { it.endsWith(".html") }) { vm.abrirVistaPrevia() },
        ComandoIde("ver_explorador", "Mostrar explorador", "Ver") { lateral = Lateral.EXPLORADOR; lateralVisible = true },
        ComandoIde("ver_control", "Mostrar control de código", "Ver") { lateral = Lateral.CONTROL; lateralVisible = true },
        ComandoIde("ver_agente", "Mostrar agente de código", "Ver") { lateral = Lateral.AGENTE; lateralVisible = true },
        ComandoIde("ver_extensiones", "Mostrar extensiones", "Ver") { lateral = Lateral.EXTENSIONES; lateralVisible = true },
        ComandoIde("alternar_lateral", "Alternar panel lateral", "Ver") { lateralVisible = !lateralVisible },
        ComandoIde("panel_problemas", "Mostrar problemas", "Ver", enMenu = true) { vm.abrirPanel(PanelEditor.PROBLEMAS) },
        ComandoIde("panel_salida", "Mostrar salida", "Ver") { vm.abrirPanel(PanelEditor.EJECUCION) },
        ComandoIde("panel_terminal", "Mostrar terminal", "Ver") { vm.abrirPanel(PanelEditor.TERMINAL) },
        ComandoIde("panel_consola", "Mostrar consola JavaScript/Python", "Ver") { vm.abrirPanel(PanelEditor.CONSOLA) },
        ComandoIde("alternar_panel", "Alternar panel inferior", "Ver") { if (estado.panel == PanelEditor.NINGUNO) vm.abrirPanel(PanelEditor.PROBLEMAS) else vm.cerrarPanel() },
        ComandoIde("revisar", "Revisar calidad del código", "Herramientas", enMenu = true, habilitado = pestana != null) { vm.revisar() },
        ComandoIde("mentor_explicar", "Explicar con el mentor", "Mentor", enMenu = true, habilitado = pestana != null) { vm.prepararMentor(TipoSolicitud.EXPLICAR_CODIGO); alAbrirMentor() },
        ComandoIde("mentor_lineas", "Explicar línea por línea", "Mentor", enMenu = true, habilitado = pestana != null) { vm.prepararMentor(TipoSolicitud.LINEA_POR_LINEA); alAbrirMentor() },
        ComandoIde("fragmento", "Guardar como fragmento", "Mentor", enMenu = true, habilitado = pestana != null) { dialogoFragmento = true },
        ComandoIde("publicar_archivo", "Publicar este archivo en GitHub", "GitHub", enMenu = true, habilitado = pestana != null && estado.proyecto != null) {
            val p = estado.proyecto
            if (p != null && pestana != null) alcance.launch { vm.guardarTodo(); alPublicarGitHub(p.id, pestana.ruta) }
        },
        ComandoIde("antigravity", "Abrir panel de Google Antigravity en el navegador", "Agentes") {
            pe.redactcode.aplicacion.funcionalidades.ajustes.abrirEnNavegador(contextoApp, prefs.urlAntigravity) { vm.mensaje(it) }
        },
        ComandoIde("ajustes", "Abrir ajustes", "Ajustes", enMenu = true) { alAbrirAjustes() }
    )

    BackHandler(enabled = true) {
        when {
            paleta != null -> paleta = null
            estado.comparacion != null -> vm.cerrarComparacion()
            busquedaArchivo -> { busquedaArchivo = false; controlador.detenerBusqueda() }
            lateralVisible && !ancha -> lateralVisible = false
            estado.panel != PanelEditor.NINGUNO -> vm.cerrarPanel()
            else -> salir()
        }
    }

    val dim = LocalDimensiones.current
    CompositionLocalProvider(LocalColoresEditor provides coloresEditor) {
        Box(Modifier.fillMaxSize().background(t.fondoAplicacion)) {
            LaunchedEffect(ancha) { if (!inicializado) { inicializado = true; if (ancha) lateralVisible = true } }
            val contenidoLateral: @Composable (Modifier) -> Unit = { m ->
                Box(m) {
                    when (lateral) {
                        Lateral.EXPLORADOR -> PanelExplorador(explorador, estado.proyecto?.nombre ?: "Proyecto", estado.activa, alAbrir = { vm.abrir(it); if (!ancha) lateralVisible = false }, alCompararCon = { vm.compararCon(it); if (!ancha) lateralVisible = false })
                        Lateral.BUSQUEDA -> PanelBusqueda(busqueda, alAbrir = { r, l, c -> irAArchivo(r, l, c); if (!ancha) lateralVisible = false })
                        Lateral.CONTROL -> PanelControl(control, prefs.confirmarPublicacion, alAbrirArchivo = { vm.abrir(it) }, alConectarGitHub = alAbrirGitHub, alGuardarTodo = guardarTodo)
                        Lateral.AGENTE -> PanelAgente(agente, estado.activa, { estado.pestanas.associate { it.ruta to it.textoActual } }, alAbrirAjustes, guardarTodo)
                        Lateral.EXTENSIONES -> PanelExtensiones(extVm)
                    }
                }
            }
            Row(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                if (ancha) {
                    BarraActividad(lateral, lateralVisible, { l -> if (lateralVisible && lateral == l) lateralVisible = false else { lateral = l; lateralVisible = true } }, salir, alAbrirAjustes, { paleta = ModoPaleta.COMANDOS })
                    AnimatedVisibility(lateralVisible, enter = if (anim) slideInHorizontally(tween(180)) { -it } + fadeIn(tween(180)) else fadeIn(snap()), exit = if (anim) slideOutHorizontally(tween(150)) { -it } + fadeOut(tween(150)) else fadeOut(snap())) {
                        Row {
                            contenidoLateral(Modifier.width(dim.anchoPanelLateral).fillMaxHeight().background(t.panelLateral))
                            Box(Modifier.width(1.dp).fillMaxHeight().background(t.bordeSutil))
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    BarraSuperior(ancha, estado, vm, controlador, salir, { lateralVisible = !lateralVisible }, { paleta = ModoPaleta.ARCHIVOS }, comandos, menuAbierto, { menuAbierto = it })
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val altoTotal = maxHeight
                        val hayPanel = estado.panel != PanelEditor.NINGUNO
                        val altoPanel = if (!hayPanel) 0.dp else if (panelMaximizado) altoTotal * 0.88f else altoTotal * fraccionPanel
                        Column(Modifier.fillMaxSize()) {
                            if (!panelMaximizado || !hayPanel) {
                                ZonaEditor(estado, prefs, vm, controlador, busquedaArchivo, { busquedaArchivo = it }, simbolosEn(estado), { lateralVisible = true; lateral = Lateral.EXPLORADOR }, Modifier.weight(1f))
                            }
                            if (hayPanel) {
                                PanelInferiorEspacio(
                                    estado, vm, terminal, controlador, panelMaximizado, { panelMaximizado = !panelMaximizado },
                                    { fraccionPanel = if (fraccionPanel < 0.5f) 0.6f else 0.3f }, alAbrirMentor,
                                    if (panelMaximizado) Modifier.weight(1f) else Modifier.height(altoPanel).animateContentSizeSi(anim)
                                )
                            }
                        }
                    }
                    Column(Modifier.navigationBarsPadding()) {
                        if (prefs.mostrarBarraEstado) BarraEstadoEspacio(estado, ancha) { vm.mostrarPanel(PanelEditor.PROBLEMAS) }
                        if (pestana != null && prefs.barraSimbolos) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
                                if (!ancha) {
                                    BotonIcono(Icons.AutoMirrored.Outlined.Undo, "Deshacer", { controlador.deshacer() })
                                    BotonIcono(Icons.AutoMirrored.Outlined.Redo, "Rehacer", { controlador.rehacer() })
                                }
                                BarraSimbolos(controlador, pestana.lenguaje, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (!ancha) {
                AnimatedVisibility(lateralVisible, enter = fadeIn(if (anim) tween(150) else snap()), exit = fadeOut(if (anim) tween(150) else snap())) {
                    Box(Modifier.fillMaxSize().background(t.velo).clickable(onClick = { lateralVisible = false }, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }))
                }
                AnimatedVisibility(lateralVisible, enter = if (anim) slideInHorizontally(tween(200)) { -it } else fadeIn(snap()), exit = if (anim) slideOutHorizontally(tween(160)) { -it } else fadeOut(snap())) {
                    Column(Modifier.fillMaxHeight().widthIn(max = 340.dp).fillMaxWidth(0.9f).background(t.panelLateral).statusBarsPadding().navigationBarsPadding().imePadding()) {
                        Row(Modifier.fillMaxWidth().background(t.barraActividad).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            Lateral.entries.forEach { l -> ElementoActividad(l.icono, l.titulo, lateral == l, true) { lateral = l } }
                            Spacer(Modifier.weight(1f))
                            BotonIcono(Icons.Outlined.Settings, "Ajustes", alAbrirAjustes)
                            BotonIcono(Icons.Outlined.Close, "Cerrar panel lateral", { lateralVisible = false })
                        }
                        HorizontalDivider(color = t.bordeSutil)
                        contenidoLateral(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
        }
    }

    paleta?.let { modo ->
        PaletaComandos(
            modoInicial = modo,
            archivos = estado.archivosProyecto,
            comandos = comandos,
            simbolos = { vm.simbolosActivos() },
            alAbrirArchivo = { vm.abrir(it) },
            alIrALinea = { controlador.irA(it, 1) },
            alIrASimbolo = { controlador.irA(it.linea, it.columna) },
            alCerrar = { paleta = null }
        )
    }
    estado.comparacion?.let { c ->
        DialogoPantallaCompleta("${c.izquierda} ↔ ${c.derecha}", vm::cerrarComparacion) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ResumenDiferencia(c.resultado.agregadas, c.resultado.eliminadas) }
                VisorDiferencias(c.resultado, Modifier.weight(1f))
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
        DialogoEntradaTexto("Nuevo archivo", "Nombre con extensión (por ejemplo main.py)", validar = { GestorArchivos.validarNombre(it, esArchivo = true) },
            alConfirmar = { dialogoNuevoArchivo = false; explorador.crear("", it, false) }, alCerrar = { dialogoNuevoArchivo = false })
    }
    if (confirmarSalida) {
        DialogoConfirmacion("Cambios sin guardar", "Hay archivos con cambios sin guardar. ¿Quieres guardarlos antes de salir?", "Guardar y salir",
            alConfirmar = { confirmarSalida = false; alcance.launch { vm.guardarTodo(); alVolver() } }, alCerrar = { confirmarSalida = false })
    }
}

@Composable
private fun simbolosEn(estado: EstadoEditor): List<SimboloCodigo> {
    val p = estado.pestanaActiva
    val lista by produceState(emptyList<SimboloCodigo>(), p?.ruta, p?.textoActual) {
        if (p == null) { value = emptyList(); return@produceState }
        delay(300)
        value = withContext(Dispatchers.Default) { ExtractorSimbolos.extraer(p.lenguaje, p.textoActual) }
    }
    return lista
}

private fun Modifier.animateContentSizeSi(activo: Boolean): Modifier = if (activo) this.animateContentSize(tween(180)) else this

@Composable
private fun BarraActividad(
    seleccionado: Lateral, visible: Boolean, alElegir: (Lateral) -> Unit, alSalir: () -> Unit, alAjustes: () -> Unit, alPaleta: () -> Unit
) {
    val t = LocalTokens.current
    Column(Modifier.width(LocalDimensiones.current.anchoBarraActividad).fillMaxHeight().background(t.barraActividad), horizontalAlignment = Alignment.CenterHorizontally) {
        BotonIcono(Icons.AutoMirrored.Outlined.ArrowBack, "Volver a proyectos", alSalir)
        Lateral.entries.forEach { l -> ElementoActividad(l.icono, l.titulo, visible && seleccionado == l, false) { alElegir(l) } }
        Spacer(Modifier.weight(1f))
        BotonIcono(Icons.Outlined.Search, "Paleta de comandos", alPaleta)
        BotonIcono(Icons.Outlined.Settings, "Ajustes", alAjustes)
    }
}

@Composable
private fun ElementoActividad(icono: ImageVector, titulo: String, activo: Boolean, horizontal: Boolean, alPulsar: () -> Unit) {
    val t = LocalTokens.current
    val color by animateColorAsState(if (activo) t.acento else t.textoTenue, tween(if (LocalAnimacionesActivas.current) 150 else 0), label = "actividad")
    Box(
        Modifier.size(LocalDimensiones.current.objetivoTactil).clickable(onClick = alPulsar)
            .semantics { contentDescription = titulo; role = androidx.compose.ui.semantics.Role.Tab; selected = activo },
        contentAlignment = Alignment.Center
    ) {
        if (activo) Box(Modifier.align(if (horizontal) Alignment.BottomCenter else Alignment.CenterStart).then(if (horizontal) Modifier.width(24.dp).height(2.dp) else Modifier.width(2.dp).height(24.dp)).background(t.acento))
        Icon(icono, null, tint = color, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun BarraSuperior(
    ancha: Boolean, estado: EstadoEditor, vm: EditorViewModel, controlador: ControladorEditores, alSalir: () -> Unit,
    alAlternarLateral: () -> Unit, alPaleta: () -> Unit, comandos: List<ComandoIde>, menuAbierto: Boolean, alCambiarMenu: (Boolean) -> Unit
) {
    val t = LocalTokens.current
    val pestana = estado.pestanaActiva
    Row(Modifier.fillMaxWidth().background(t.barraTitulo).height(LocalDimensiones.current.objetivoTactil), verticalAlignment = Alignment.CenterVertically) {
        BotonIcono(Icons.Outlined.Menu, if (ancha) "Alternar panel lateral" else "Abrir panel lateral", alAlternarLateral)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(pestana?.nombre ?: (estado.proyecto?.nombre ?: "Editor"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pestana != null) Text(estado.proyecto?.nombre.orEmpty(), style = MaterialTheme.typography.labelSmall, color = t.textoTenue, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (ancha) {
            BotonIcono(Icons.AutoMirrored.Outlined.Undo, "Deshacer", { controlador.deshacer() }, pestana != null)
            BotonIcono(Icons.AutoMirrored.Outlined.Redo, "Rehacer", { controlador.rehacer() }, pestana != null)
        }
        Box {
            BotonIcono(Icons.Outlined.Save, "Guardar", { vm.guardar() }, pestana?.modificada == true)
            if (pestana?.modificada == true) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { PuntoEstado(t.acento, 7.dp) }
        }
        if (estado.ejecutando) BotonIcono(Icons.Outlined.Stop, "Detener", vm::detenerEjecucion, tinte = t.error)
        else BotonIcono(Icons.Outlined.PlayArrow, "Ejecutar", vm::ejecutar, pestana != null, tinte = t.acento)
        BotonIcono(Icons.Outlined.Search, "Ir a archivo o comando", alPaleta)
        Box {
            BotonIcono(Icons.Outlined.MoreVert, "Más opciones", { alCambiarMenu(true) })
            DropdownMenu(expanded = menuAbierto, onDismissRequest = { alCambiarMenu(false) }) {
                DropdownMenuItem(text = { Text("Paleta de comandos") }, onClick = { alCambiarMenu(false); alPaleta() })
                comandos.filter { it.enMenu }.forEach { c ->
                    DropdownMenuItem(text = { Text(c.titulo) }, enabled = c.habilitado, onClick = { alCambiarMenu(false); c.ejecutar() })
                }
            }
        }
    }
    HorizontalDivider(color = t.bordeSutil)
}

@Composable
private fun ZonaEditor(
    estado: EstadoEditor, prefs: pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario, vm: EditorViewModel, controlador: ControladorEditores,
    busquedaAbierta: Boolean, alCambiarBusqueda: (Boolean) -> Unit, simbolos: List<SimboloCodigo>, alMostrarArchivos: () -> Unit, modifier: Modifier
) {
    val colores = LocalColoresEditor.current
    val pestana = estado.pestanaActiva
    Column(modifier.fillMaxWidth()) {
        if (estado.pestanas.isNotEmpty()) {
            BarraPestanas(estado, vm::activar) { ruta ->
                val p = estado.pestanas.firstOrNull { it.ruta == ruta }
                if (p?.modificada == true) vm.guardar(ruta, silencioso = true)
                controlador.cerrar(ruta)
                vm.cerrarPestana(ruta)
            }
        }
        if (prefs.mostrarMigas && pestana != null) {
            val actual = simbolos.lastOrNull { it.linea <= estado.cursor.linea }
            val migas = (listOf(estado.proyecto?.nombre.orEmpty()) + pestana.ruta.split('/')).filter { it.isNotEmpty() }.joinToString("  ›  ") + (actual?.let { "  ›  ${it.nombre}" } ?: "")
            Text(migas, style = MaterialTheme.typography.labelSmall, color = LocalTokens.current.textoTenue, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().background(colores.fondo).padding(horizontal = 12.dp, vertical = 3.dp))
        }
        if (busquedaAbierta) BarraBusquedaArchivo(controlador) { alCambiarBusqueda(false); controlador.detenerBusqueda() }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                estado.cargando -> Cargando()
                estado.error != null -> EstadoVacio(Icons.Outlined.Folder, "No disponible", estado.error)
                pestana == null -> EstadoVacio(Icons.Outlined.Folder, "Ningún archivo abierto", "Abre un archivo desde el explorador o con la paleta de comandos.", accion = "Ver archivos", alPulsarAccion = alMostrarArchivos)
                else -> VistaEditor(controlador, pestana.ruta, pestana.textoGuardado, pestana.lenguaje, prefs, Modifier.fillMaxSize().background(colores.fondo))
            }
        }
    }
}

@Composable
private fun BarraPestanas(estado: EstadoEditor, alActivar: (String) -> Unit, alCerrar: (String) -> Unit) {
    val t = LocalTokens.current
    val colores = LocalColoresEditor.current
    val anim = LocalAnimacionesActivas.current
    Row(Modifier.fillMaxWidth().background(t.barraTitulo).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        estado.pestanas.forEach { p ->
            val activa = p.ruta == estado.activa
            val errores = estado.diagnosticos[p.ruta].orEmpty().count { it.severidad == Severidad.ERROR }
            val fondo by animateColorAsState(if (activa) colores.fondo else t.panelLateral, tween(if (anim) 150 else 0), label = "pestana")
            Row(
                Modifier.height(LocalDimensiones.current.alturaFila).background(fondo).clickable { alActivar(p.ruta) }
                    .semantics { contentDescription = "Pestaña ${p.nombre}" + if (p.modificada) ", modificada" else ""; selected = activa; role = androidx.compose.ui.semantics.Role.Tab }
                    .padding(start = 12.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(p.nombre, style = MaterialTheme.typography.labelLarge, color = if (errores > 0) t.error else if (activa) t.texto else t.textoTenue, fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                if (p.modificada) { Spacer(Modifier.width(6.dp)); PuntoEstado(t.acento, 6.dp) }
                IconButton(onClick = { alCerrar(p.ruta) }, modifier = Modifier.size(LocalDimensiones.current.alturaFila)) {
                    Icon(Icons.Outlined.Close, "Cerrar ${p.nombre}", modifier = Modifier.size(16.dp), tint = t.textoTenue)
                }
            }
        }
    }
}

@Composable
private fun BarraBusquedaArchivo(controlador: ControladorEditores, alCerrar: () -> Unit) {
    var consulta by remember { mutableStateOf("") }
    var reemplazo by remember { mutableStateOf("") }
    var mayusculas by remember { mutableStateOf(false) }
    var palabra by remember { mutableStateOf(false) }
    var total by remember { mutableIntStateOf(0) }
    val t = LocalTokens.current
    LaunchedEffect(consulta, mayusculas, palabra) {
        controlador.buscar(consulta, mayusculas, palabra)
        delay(250)
        total = controlador.coincidencias()
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CampoTexto(consulta, { consulta = it }, "Buscar") }
            BotonIcono(Icons.Outlined.KeyboardArrowUp, "Anterior", { controlador.anterior() })
            BotonIcono(Icons.Outlined.KeyboardArrowDown, "Siguiente", { controlador.siguiente() })
            BotonIcono(Icons.Outlined.Close, "Cerrar búsqueda", alCerrar)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CampoTexto(reemplazo, { reemplazo = it }, "Reemplazar con") }
            TextButton(onClick = { controlador.reemplazarActual(reemplazo) }, enabled = consulta.isNotEmpty()) { Text("Uno") }
            TextButton(onClick = { controlador.reemplazarTodo(reemplazo) }, enabled = consulta.isNotEmpty()) { Text("Todos") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Etiqueta("Aa", seleccionada = mayusculas, alPulsar = { mayusculas = !mayusculas })
            Etiqueta("Palabra completa", seleccionada = palabra, alPulsar = { palabra = !palabra })
            Spacer(Modifier.weight(1f))
            if (consulta.isNotEmpty()) Text("$total coincidencias", style = MaterialTheme.typography.labelMedium, color = t.textoTenue)
        }
    }
}

@Composable
private fun BarraEstadoEspacio(estado: EstadoEditor, ancha: Boolean, alPulsarProblemas: () -> Unit) {
    val t = LocalTokens.current
    val errores = estado.diagnosticosActivos.count { it.severidad == Severidad.ERROR }
    val advertencias = estado.diagnosticosActivos.count { it.severidad == Severidad.ADVERTENCIA }
    val color = t.textoBarraEstado
    Row(Modifier.fillMaxWidth().background(t.barraEstado).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ln ${estado.cursor.linea}, Col ${estado.cursor.columna}", style = MaterialTheme.typography.labelSmall, color = color)
        Text(estado.pestanaActiva?.lenguaje?.nombreVisible.orEmpty(), style = MaterialTheme.typography.labelSmall, color = color)
        if (ancha && estado.motorValidacion.isNotEmpty()) Text(estado.motorValidacion, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        if (estado.ejecutando) Text("Ejecutando…", style = MaterialTheme.typography.labelSmall, color = color)
        else if (estado.validando) Text("Validando…", style = MaterialTheme.typography.labelSmall, color = color)
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = alPulsarProblemas).padding(horizontal = 6.dp, vertical = 2.dp)
                .semantics { contentDescription = "$errores errores y $advertencias advertencias. Abrir problemas" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✕ $errores", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
            Spacer(Modifier.width(10.dp))
            Text("⚠ $advertencias", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun PanelInferiorEspacio(
    estado: EstadoEditor, vm: EditorViewModel, terminal: TerminalViewModel, controlador: ControladorEditores,
    maximizado: Boolean, alMaximizar: () -> Unit, alAjustarAltura: () -> Unit, alAbrirMentor: () -> Unit, modifier: Modifier
) {
    val t = LocalTokens.current
    val errores = estado.diagnosticosActivos.size
    val pestanasPanel = listOf(PanelEditor.PROBLEMAS, PanelEditor.EJECUCION, PanelEditor.TERMINAL, PanelEditor.CONSOLA, PanelEditor.REVISION, PanelEditor.VISTA_PREVIA)
    Column(modifier.fillMaxWidth().background(t.panelInferior)) {
        HorizontalDivider(color = t.borde)
        Row(Modifier.fillMaxWidth().height(LocalDimensiones.current.alturaFila), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                pestanasPanel.forEach { p ->
                    val activo = estado.panel == p
                    Box(
                        Modifier.height(LocalDimensiones.current.alturaFila).clickable { vm.abrirPanel(p) }.padding(horizontal = 12.dp)
                            .semantics { contentDescription = p.titulo; selected = activo; role = androidx.compose.ui.semantics.Role.Tab },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(p.titulo + if (p == PanelEditor.PROBLEMAS && errores > 0) " ($errores)" else "", style = MaterialTheme.typography.labelLarge, color = if (activo) t.texto else t.textoTenue, fontWeight = if (activo) FontWeight.SemiBold else FontWeight.Normal)
                        if (activo) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(t.acento))
                    }
                }
            }
            if (estado.panel == PanelEditor.VISTA_PREVIA) BotonIcono(Icons.Outlined.Refresh, "Recargar vista previa", vm::recargarVistaPrevia)
            if (estado.panel == PanelEditor.EJECUCION && !estado.ejecutando) BotonIcono(Icons.Outlined.PlayArrow, "Volver a ejecutar", vm::ejecutar)
            if (!maximizado) BotonIcono(Icons.Outlined.KeyboardArrowUp, "Cambiar altura del panel", alAjustarAltura)
            BotonIcono(if (maximizado) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (maximizado) "Restaurar panel" else "Maximizar panel", alMaximizar)
            BotonIcono(Icons.Outlined.Close, "Cerrar panel", vm::cerrarPanel)
        }
        HorizontalDivider(color = t.bordeSutil)
        val irA: (Int) -> Unit = { linea -> controlador.irA(linea, 1) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (estado.panel) {
                PanelEditor.PROBLEMAS -> PanelProblemas(
                    estado.diagnosticosActivos, estado.motorValidacion, estado.duracionValidacionMs,
                    propuestaPara = vm::propuesta,
                    alIrALinea = { controlador.irA(it.linea, it.columna) },
                    alCorregir = { propuesta ->
                        val ruta = estado.activa
                        val nuevo = vm.aplicarPropuesta(propuesta)
                        if (ruta != null && nuevo != null) { controlador.reemplazarTexto(ruta, nuevo); vm.alCambiarTexto(ruta, nuevo) }
                    },
                    alPreguntarMentor = { vm.prepararMentor(TipoSolicitud.EXPLICAR_ERROR, it); alAbrirMentor() },
                    modifier = Modifier.fillMaxSize()
                )
                PanelEditor.EJECUCION -> PanelEjecucion(estado.resultado, estado.ejecutando, estado.entradaPrograma, estado.pestanaActiva?.lenguaje == Lenguaje.PYTHON, vm::cambiarEntrada, irA, Modifier.fillMaxSize())
                PanelEditor.TERMINAL -> PanelTerminal(terminal)
                PanelEditor.CONSOLA -> PanelConsola(terminal)
                PanelEditor.REVISION -> PanelRevision(estado.revision, estado.revisando, irA, Modifier.fillMaxSize())
                PanelEditor.VISTA_PREVIA -> {
                    val proyecto = estado.proyecto
                    val html = estado.archivoVistaPrevia
                    if (proyecto != null && html != null) {
                        val carpeta = remember(proyecto.carpeta) { java.io.File(vm.carpetaRaiz(), proyecto.carpeta) }
                        Column(Modifier.fillMaxSize()) {
                            VistaPreviaWeb(
                                carpetaProyecto = carpeta, archivoHtml = html, version = estado.versionVistaPrevia,
                                alMensajeConsola = { nivel, mensaje, linea -> vm.agregarConsola(LineaConsola(nivel, mensaje, linea)) },
                                alNavegacionBloqueada = { vm.mensaje("Por seguridad, la vista previa no abre enlaces externos") },
                                alProcesoDetenido = { vm.mensaje("La vista previa se detuvo por falta de memoria o un error del navegador. Ábrela de nuevo."); vm.cerrarPanel() },
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                            HorizontalDivider(color = t.bordeSutil)
                            Text("Consola de la página", style = MaterialTheme.typography.labelMedium, color = t.textoTenue, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            PanelConsolaWeb(estado.consolaWeb, Modifier.fillMaxWidth().height(96.dp))
                        }
                    } else EstadoVacio(Icons.Outlined.Terminal, "Sin vista previa", "Ejecuta un archivo HTML para ver su vista previa.")
                }
                PanelEditor.NINGUNO -> Unit
            }
        }
    }
}
