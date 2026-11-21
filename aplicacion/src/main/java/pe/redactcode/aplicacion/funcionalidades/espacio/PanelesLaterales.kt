package pe.redactcode.aplicacion.funcionalidades.espacio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.archivos.NodoArchivo
import pe.redactcode.aplicacion.nucleo.diseno.BotonPrincipal
import pe.redactcode.aplicacion.nucleo.diseno.BotonSecundario
import pe.redactcode.aplicacion.nucleo.diseno.CampoTexto
import pe.redactcode.aplicacion.nucleo.diseno.DialogoConfirmacion
import pe.redactcode.aplicacion.nucleo.diseno.DialogoEntradaTexto
import pe.redactcode.aplicacion.nucleo.diseno.EstadoVacio
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.FuenteMonoJetBrains
import pe.redactcode.aplicacion.nucleo.diseno.LocalDimensiones
import pe.redactcode.aplicacion.nucleo.diseno.LocalTokens
import pe.redactcode.aplicacion.nucleo.diseno.PuntoEstado
import pe.redactcode.nucleo.agentes.AccionCambio
import pe.redactcode.nucleo.terminal.NivelRiesgo
import androidx.compose.material.icons.outlined.PlayArrow
import pe.redactcode.nucleo.control.CambioTrabajo
import pe.redactcode.nucleo.control.ControlVersiones
import pe.redactcode.nucleo.control.DiferenciaArchivo
import pe.redactcode.nucleo.control.EstadoCambio
import pe.redactcode.nucleo.extensiones.ManifiestoExtension
import pe.redactcode.nucleo.ide.OpcionesBusqueda
import pe.redactcode.nucleo.modelo.Lenguaje

@Composable
fun EncabezadoPanel(titulo: String, modifier: Modifier = Modifier, acciones: @Composable () -> Unit = {}) {
    val t = LocalTokens.current
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).height(LocalDimensiones.current.alturaFila), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo.uppercase(), style = MaterialTheme.typography.labelMedium, color = t.textoTenue, modifier = Modifier.weight(1f).semantics { contentDescription = titulo }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        acciones()
    }
}

@Composable
fun BotonIcono(icono: ImageVector, descripcion: String, alPulsar: () -> Unit, habilitado: Boolean = true, tinte: androidx.compose.ui.graphics.Color = LocalTokens.current.textoSecundario) {
    IconButton(onClick = alPulsar, enabled = habilitado, modifier = Modifier.size(LocalDimensiones.current.alturaFila)) {
        Icon(icono, descripcion, tint = if (habilitado) tinte else LocalTokens.current.textoDeshabilitado, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun DialogoPantallaCompleta(titulo: String, alCerrar: () -> Unit, contenido: @Composable () -> Unit) {
    val t = LocalTokens.current
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(t.fondoAplicacion).padding(top = 24.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = alCerrar) { Icon(Icons.Outlined.Close, "Cerrar") }
            }
            HorizontalDivider(color = t.bordeSutil)
            Box(Modifier.weight(1f).fillMaxWidth()) { contenido() }
        }
    }
}

private fun iconoArchivo(l: Lenguaje): ImageVector = when (l) {
    Lenguaje.TEXTO -> Icons.Outlined.Description
    else -> Icons.Outlined.Code
}

@Composable
fun colorLenguajeArchivo(nombre: String): androidx.compose.ui.graphics.Color {
    val c = pe.redactcode.aplicacion.nucleo.diseno.LocalColoresEditor.current
    return when (Lenguaje.desdeNombreArchivo(nombre)) {
        Lenguaje.PYTHON -> c.funcion
        Lenguaje.JAVASCRIPT -> c.atributo
        Lenguaje.HTML -> c.numero
        Lenguaje.CSS -> c.palabraClave
        Lenguaje.JSON -> c.cadena
        Lenguaje.TEXTO -> c.comentario
    }
}

private data class FilaArbol(val nodo: NodoArchivo)

private fun aplanar(nodos: List<NodoArchivo>, expandidas: Set<String>): List<FilaArbol> =
    nodos.flatMap { n -> listOf(FilaArbol(n)) + if (n.esCarpeta && n.rutaRelativa in expandidas) aplanar(n.hijos, expandidas) else emptyList() }

@Composable
fun PanelExplorador(
    vm: ExploradorEspacioViewModel,
    nombreProyecto: String,
    rutaActiva: String?,
    alAbrir: (String) -> Unit,
    alCompararCon: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val prefs by vm.preferencias.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    var nuevo by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var renombrar by remember { mutableStateOf<String?>(null) }
    var mover by remember { mutableStateOf<String?>(null) }
    var eliminar by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(rutaActiva) { rutaActiva?.let(vm::expandirHasta) }
    val filas = remember(estado.nodos, estado.expandidas) { aplanar(estado.nodos, estado.expandidas) }
    Column(modifier.fillMaxSize()) {
        EncabezadoPanel(nombreProyecto) {
            BotonIcono(Icons.AutoMirrored.Outlined.NoteAdd, "Nuevo archivo", { nuevo = "" to false })
            BotonIcono(Icons.Outlined.CreateNewFolder, "Nueva carpeta", { nuevo = "" to true })
            BotonIcono(Icons.Outlined.Refresh, "Actualizar", vm::refrescar)
        }
        when {
            estado.cargando -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = t.acento)
            filas.isEmpty() -> EstadoVacio(Icons.Outlined.FolderOpen, "Carpeta vacía", "Crea un archivo para empezar.", accion = "Nuevo archivo", alPulsarAccion = { nuevo = "" to false })
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(filas, key = { it.nodo.rutaRelativa }) { fila ->
                    val n = fila.nodo
                    val activo = n.rutaRelativa == rutaActiva
                    var menu by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().height(LocalDimensiones.current.alturaFila)
                            .background(if (activo) t.seleccionLista else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { if (n.esCarpeta) vm.alternar(n.rutaRelativa) else alAbrir(n.rutaRelativa) }
                            .padding(start = (8 + n.profundidad * 14).dp, end = 0.dp)
                            .semantics { contentDescription = (if (n.esCarpeta) "Carpeta " else "Archivo ") + n.nombre },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (n.esCarpeta) Icon(if (n.rutaRelativa in estado.expandidas) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null, tint = t.textoTenue, modifier = Modifier.size(18.dp))
                        else Spacer(Modifier.width(18.dp))
                        Icon(if (n.esCarpeta) Icons.Outlined.Folder else iconoArchivo(n.lenguaje), null, tint = if (n.esCarpeta) t.acentoSecundario else colorLenguajeArchivo(n.nombre), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (prefs.mostrarExtensiones || n.esCarpeta) n.nombre else n.nombreSinExtension, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), fontWeight = if (activo) FontWeight.SemiBold else FontWeight.Normal)
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(LocalDimensiones.current.alturaFila)) { Icon(Icons.Outlined.MoreVert, "Opciones de ${n.nombre}", tint = t.textoTenue, modifier = Modifier.size(18.dp)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (n.esCarpeta) {
                                    DropdownMenuItem(text = { Text("Nuevo archivo") }, onClick = { menu = false; nuevo = n.rutaRelativa to false })
                                    DropdownMenuItem(text = { Text("Nueva carpeta") }, onClick = { menu = false; nuevo = n.rutaRelativa to true })
                                } else if (rutaActiva != null && rutaActiva != n.rutaRelativa) {
                                    DropdownMenuItem(text = { Text("Comparar con el archivo activo") }, onClick = { menu = false; alCompararCon(n.rutaRelativa) })
                                }
                                DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; renombrar = n.rutaRelativa })
                                DropdownMenuItem(text = { Text("Mover a…") }, onClick = { menu = false; mover = n.rutaRelativa })
                                DropdownMenuItem(text = { Text("Eliminar", color = t.error) }, onClick = { menu = false; if (prefs.confirmarEliminar) eliminar = n.rutaRelativa else vm.eliminar(n.rutaRelativa) })
                            }
                        }
                    }
                }
            }
        }
    }
    nuevo?.let { (padre, carpeta) ->
        DialogoEntradaTexto(
            if (carpeta) "Nueva carpeta" else "Nuevo archivo",
            if (carpeta) "Nombre de la carpeta" else "Nombre con extensión (por ejemplo main.py)",
            validar = { GestorArchivos.validarNombre(it, esArchivo = !carpeta) },
            alConfirmar = { vm.crear(padre, it, carpeta); nuevo = null },
            alCerrar = { nuevo = null }
        )
    }
    renombrar?.let { ruta ->
        DialogoEntradaTexto("Renombrar", "Nuevo nombre", ruta.substringAfterLast('/'), "Renombrar",
            validar = { GestorArchivos.validarNombre(it, esArchivo = filas.firstOrNull { f -> f.nodo.rutaRelativa == ruta }?.nodo?.esCarpeta == false) },
            alConfirmar = { vm.renombrar(ruta, it); renombrar = null }, alCerrar = { renombrar = null })
    }
    mover?.let { ruta ->
        DialogoPantallaCompleta("Mover «${ruta.substringAfterLast('/')}» a", { mover = null }) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.carpetas().filter { it != ruta && !it.startsWith("$ruta/") }) { carpeta ->
                    Text(if (carpeta.isEmpty()) "/ (raíz del proyecto)" else carpeta, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { vm.mover(ruta, carpeta); mover = null }.padding(horizontal = 20.dp, vertical = 14.dp))
                }
            }
        }
    }
    eliminar?.let { ruta ->
        DialogoConfirmacion("Eliminar", "Se eliminará «$ruta» de forma permanente. Esta acción no se puede deshacer.", "Eliminar", true, { vm.eliminar(ruta); eliminar = null }, { eliminar = null })
    }
}

@Composable
fun PanelBusqueda(vm: BusquedaEspacioViewModel, alAbrir: (String, Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    var filtros by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }
    var consultaLocal by remember(estado.opciones.consulta) { mutableStateOf(estado.opciones.consulta) }
    val o = estado.opciones
    Column(modifier.fillMaxSize()) {
        EncabezadoPanel("Buscar en el proyecto") { BotonIcono(Icons.Outlined.Refresh, "Repetir búsqueda", vm::refrescar) }
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CampoTexto(consultaLocal, { consultaLocal = it; vm.opciones(o.copy(consulta = it)) }, "Buscar")
            CampoTexto(estado.reemplazo, vm::reemplazo, "Reemplazar con")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Etiqueta("Aa", seleccionada = o.distinguirMayusculas, alPulsar = { vm.opciones(o.copy(distinguirMayusculas = !o.distinguirMayusculas)) })
                Etiqueta("Palabra", seleccionada = o.palabraCompleta, alPulsar = { vm.opciones(o.copy(palabraCompleta = !o.palabraCompleta)) })
                Etiqueta(".*", seleccionada = o.expresionRegular, alPulsar = { vm.opciones(o.copy(expresionRegular = !o.expresionRegular)) })
                Etiqueta("Filtros", seleccionada = filtros, alPulsar = { filtros = !filtros })
            }
            if (filtros) {
                CampoTexto(o.incluir, { vm.opciones(o.copy(incluir = it)) }, "Incluir (por ejemplo *.js, src/**)")
                CampoTexto(o.excluir, { vm.opciones(o.copy(excluir = it)) }, "Excluir")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val r = estado.resultado
                Text(
                    when {
                        estado.buscando -> "Buscando…"
                        r?.error != null -> r.error.orEmpty()
                        r == null -> ""
                        else -> "${r.total} resultado(s) en ${r.archivos.size} archivo(s)" + if (r.truncado) " (hay más)" else ""
                    },
                    style = MaterialTheme.typography.labelMedium, color = if (r?.error != null) t.error else t.textoTenue, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { confirmar = true }, enabled = (estado.resultado?.total ?: 0) > 0 && o.consulta.isNotEmpty()) { Text("Reemplazar todo") }
            }
        }
        HorizontalDivider(color = t.bordeSutil)
        val r = estado.resultado
        if (r != null && r.error == null) {
            LazyColumn(Modifier.fillMaxSize()) {
                r.archivos.forEach { archivo ->
                    item(key = "a:${archivo.ruta}") {
                        Row(Modifier.fillMaxWidth().background(t.superficie).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(archivo.ruta, style = MaterialTheme.typography.labelLarge, color = colorLenguajeArchivo(archivo.ruta), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${archivo.coincidencias.size}", style = MaterialTheme.typography.labelSmall, color = t.textoTenue)
                            TextButton(onClick = { vm.reemplazarEn(listOf(archivo.ruta)) }, enabled = o.consulta.isNotEmpty()) { Text("Reemplazar") }
                        }
                    }
                    items(archivo.coincidencias.take(100), key = { "m:${archivo.ruta}:${it.linea}:${it.columna}" }) { c ->
                        Row(Modifier.fillMaxWidth().clickable { alAbrir(archivo.ruta, c.linea, c.columna) }.padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
                            Text("${c.linea}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FuenteMonoJetBrains), color = t.textoTenue, modifier = Modifier.width(36.dp))
                            Text(c.textoLinea.trim(), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else if (r == null && o.consulta.isEmpty()) {
            EstadoVacio(Icons.Outlined.Folder, "Busca en todo el proyecto", "Admite mayúsculas, palabra completa, expresiones regulares y filtros de archivos.")
        }
    }
    if (confirmar) {
        val r = estado.resultado
        DialogoConfirmacion("Reemplazar todo", "Se reemplazarán ${r?.total ?: 0} coincidencia(s) en ${r?.archivos?.size ?: 0} archivo(s) y se guardarán en disco. Podrás deshacer solo en los archivos abiertos.", "Reemplazar", true, { confirmar = false; vm.reemplazarEn(null) }, { confirmar = false })
    }
}

@Composable
fun PanelControl(
    vm: ControlCodigoViewModel,
    publicarConfirmado: Boolean,
    alAbrirArchivo: (String) -> Unit,
    alConectarGitHub: () -> Unit,
    alGuardarTodo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    var mensaje by remember { mutableStateOf("") }
    val seleccion = remember { mutableStateMapOf<String, Boolean>() }
    var dialogoRama by remember { mutableStateOf(false) }
    var dialogoVinculo by remember { mutableStateOf(false) }
    var dialogoPublicar by remember { mutableStateOf(false) }
    var ramaAEliminar by remember { mutableStateOf<String?>(null) }
    var verHistorialRemoto by remember { mutableStateOf(false) }
    LaunchedEffect(estado.cambios) { seleccion.keys.retainAll(estado.cambios.map { it.ruta }.toSet()) }
    if (!estado.inicializado) {
        Column(modifier.fillMaxSize()) {
            EncabezadoPanel("Control de código")
            EstadoVacio(Icons.Outlined.AccountTree, "Sin control de versiones", "Inicializa este proyecto para ver cambios, confirmar commits, crear ramas y sincronizar con GitHub.", accion = "Inicializar", alPulsarAccion = vm::inicializar)
        }
        return
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EncabezadoPanel("Control de código") {
            BotonIcono(Icons.Outlined.Refresh, "Actualizar", { alGuardarTodo(); vm.refrescar() })
        }
        estado.ocupado?.let { Column { LinearProgressIndicator(Modifier.fillMaxWidth(), color = t.acento); Text(it + "…", style = MaterialTheme.typography.labelMedium, color = t.textoTenue, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) } }
        Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            estado.ramas.forEach { r -> Etiqueta(r, seleccionada = r == estado.rama, alPulsar = { if (r != estado.rama) vm.cambiarRama(r) }) }
            Etiqueta("+ Rama", alPulsar = { dialogoRama = true })
            if (estado.ramas.size > 1) Etiqueta("Eliminar rama", alPulsar = { ramaAEliminar = estado.ramas.firstOrNull { it != estado.rama } })
        }
        Spacer(Modifier.height(8.dp))
        EncabezadoPanel("Cambios (${estado.cambios.size})") {
            if (estado.cambios.isNotEmpty()) TextButton(onClick = { val todos = estado.cambios.all { seleccion[it.ruta] == true }; estado.cambios.forEach { seleccion[it.ruta] = !todos } }) { Text("Seleccionar todo") }
        }
        if (estado.cambios.isEmpty()) Text("No hay cambios sin confirmar.", style = MaterialTheme.typography.bodySmall, color = t.textoTenue, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        estado.cambios.forEach { c -> FilaCambio(c, seleccion[c.ruta] == true, { seleccion[c.ruta] = it }, { vm.verDiferencia(c.ruta) }, { vm.descartar(c.ruta) }) }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CampoTexto(mensaje, { mensaje = it }, "Mensaje del commit", unaLinea = false, lineasMinimas = 2)
            val rutas = seleccion.filterValues { it }.keys
            BotonPrincipal(if (rutas.isEmpty()) "Confirmar todo" else "Confirmar ${rutas.size} seleccionado(s)", { alGuardarTodo(); vm.confirmar(mensaje, rutas.takeIf { it.isNotEmpty() }); mensaje = "" }, Modifier.fillMaxWidth(), habilitado = estado.cambios.isNotEmpty() && mensaje.isNotBlank() && estado.ocupado == null)
        }
        HorizontalDivider(color = t.bordeSutil)
        EncabezadoPanel("GitHub")
        val v = estado.vinculo
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (estado.usuarioGitHub == null) {
                Text("Conecta tu cuenta con un token para sincronizar este proyecto.", style = MaterialTheme.typography.bodySmall, color = t.textoTenue)
                BotonSecundario("Conectar GitHub", alConectarGitHub, Modifier.fillMaxWidth())
            } else if (v == null) {
                Text("Sesión: ${estado.usuarioGitHub}. Vincula el proyecto con un repositorio existente (el contenido local no se reemplaza).", style = MaterialTheme.typography.bodySmall, color = t.textoTenue)
                BotonSecundario("Vincular con un repositorio", { dialogoVinculo = true }, Modifier.fillMaxWidth())
            } else {
                Text("${v.nombreCompleto} · ${v.rama} · ${v.shaCommit.take(7)}", style = MaterialTheme.typography.labelLarge)
                Text(if (estado.pendientes == 0) "Sin commits pendientes de publicar" else "${estado.pendientes} archivo(s) pendientes de publicar", style = MaterialTheme.typography.bodySmall, color = if (estado.pendientes == 0) t.textoTenue else t.advertencia)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonSecundario("Traer", { alGuardarTodo(); vm.traer() }, Modifier.weight(1f), habilitado = estado.ocupado == null, icono = Icons.Outlined.CloudDownload)
                    BotonPrincipal("Publicar", { dialogoPublicar = true }, Modifier.weight(1f), habilitado = estado.pendientes > 0 && estado.cambios.isEmpty() && estado.ocupado == null, icono = Icons.Outlined.CloudUpload)
                }
                if (estado.cambios.isNotEmpty() && estado.pendientes > 0) Text("Confirma tus cambios antes de publicar.", style = MaterialTheme.typography.bodySmall, color = t.textoTenue)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Etiqueta("Historial remoto", alPulsar = { verHistorialRemoto = true; vm.cargarHistorialRemoto() })
                    Etiqueta("Desvincular", alPulsar = vm::desvincular)
                }
            }
            if (estado.conflictos.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.fondoAdvertencia).padding(12.dp)) {
                    Text("Conflictos: no se aplicó ningún cambio", style = MaterialTheme.typography.labelLarge, color = t.advertencia)
                    Text("Estos archivos cambiaron en tu proyecto y en GitHub. Revisa las diferencias, ajusta tu versión, confirma y vuelve a traer.", style = MaterialTheme.typography.bodySmall)
                    estado.conflictos.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains), modifier = Modifier.clickable { alAbrirArchivo(it) }) }
                    TextButton(onClick = vm::limpiarConflictos) { Text("Entendido") }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = t.bordeSutil)
        EncabezadoPanel("Historial (${estado.historial.size})")
        if (estado.historial.isEmpty()) Text("Aún no hay commits.", style = MaterialTheme.typography.bodySmall, color = t.textoTenue, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        estado.historial.forEach { c ->
            Column(Modifier.fillMaxWidth().clickable { vm.verCommit(c) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(c.mensaje.lineSequence().first(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${c.id.take(7)} · ${c.autor} · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(c.fecha))}" + if (c.remoto != null) " · publicado" else "", style = MaterialTheme.typography.labelSmall, color = t.textoTenue)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    estado.diferencia?.let { d -> DialogoDiferenciaArchivo(d.ruta, listOf(d), vm::cerrarDiferencias) }
    estado.diferenciasCommit?.let { (c, lista) -> DialogoDiferenciaArchivo("${c.id.take(7)} · ${c.mensaje.lineSequence().first()}", lista, vm::cerrarDiferencias) }
    if (verHistorialRemoto) {
        DialogoPantallaCompleta("Historial remoto", { verHistorialRemoto = false }) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(estado.historialRemoto) { c ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(c.commit.message.lineSequence().first(), style = MaterialTheme.typography.bodyMedium)
                        Text("${c.sha.take(7)} · ${c.commit.author?.name.orEmpty()} · ${c.commit.author?.date.orEmpty().take(10)}", style = MaterialTheme.typography.labelSmall, color = t.textoTenue)
                    }
                }
            }
        }
    }
    if (dialogoRama) DialogoEntradaTexto("Nueva rama", "Nombre de la rama", validar = { ControlVersiones.validarRama(it) }, alConfirmar = { vm.crearRama(it); dialogoRama = false }, alCerrar = { dialogoRama = false })
    if (dialogoVinculo) DialogoVinculo(estado.rama, { repo, rama -> vm.vincular(repo, rama); dialogoVinculo = false }, { dialogoVinculo = false })
    ramaAEliminar?.let { r -> DialogoConfirmacion("Eliminar rama", "Se eliminará la rama «$r» solo en este dispositivo. Sus commits dejarán de ser accesibles desde el historial.", "Eliminar", true, { vm.eliminarRama(r); ramaAEliminar = null }, { ramaAEliminar = null }) }
    if (dialogoPublicar) DialogoPublicar(publicarConfirmado, estado.historial.firstOrNull()?.mensaje.orEmpty(), estado.vinculo?.rama.orEmpty(), { m, r -> vm.publicar(m, r); dialogoPublicar = false }, { dialogoPublicar = false })
}

@Composable
private fun FilaCambio(c: CambioTrabajo, marcado: Boolean, alMarcar: (Boolean) -> Unit, alVer: () -> Unit, alDescartar: () -> Unit) {
    val t = LocalTokens.current
    var confirmar by remember { mutableStateOf(false) }
    val color = when (c.estado) { EstadoCambio.AGREGADO -> t.agregado; EstadoCambio.MODIFICADO -> t.modificado; EstadoCambio.ELIMINADO -> t.eliminado }
    Row(Modifier.fillMaxWidth().clickable(onClick = alVer).padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(marcado, alMarcar, colors = CheckboxDefaults.colors(checkedColor = t.acento, checkmarkColor = t.textoSobreAcento), modifier = Modifier.semantics { contentDescription = "Seleccionar ${c.ruta}" })
        Text(c.ruta, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(c.estado.letra, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(horizontal = 6.dp).semantics { contentDescription = c.estado.nombreVisible })
        BotonIcono(Icons.Outlined.Undo, "Descartar cambios de ${c.ruta}", { confirmar = true })
    }
    if (confirmar) DialogoConfirmacion("Descartar cambios", "Se perderán los cambios de «${c.ruta}» y volverá a su última versión confirmada. Esta acción no se puede deshacer.", "Descartar", true, { confirmar = false; alDescartar() }, { confirmar = false })
}

@Composable
fun DialogoDiferenciaArchivo(titulo: String, lista: List<DiferenciaArchivo>, alCerrar: () -> Unit) {
    val t = LocalTokens.current
    DialogoPantallaCompleta(titulo, alCerrar) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(lista, key = { it.ruta }) { d ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().background(t.superficieAlta).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${d.estado.letra}  ${d.ruta}", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FuenteMonoJetBrains), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        d.diferencias?.let { ResumenDiferencia(it.agregadas, it.eliminadas) }
                    }
                    val resultado = d.diferencias
                    if (d.esBinario || resultado == null) Text("Archivo binario: no se puede mostrar la diferencia.", style = MaterialTheme.typography.bodySmall, color = t.textoTenue, modifier = Modifier.padding(16.dp))
                    else Box(Modifier.heightIn(max = 520.dp)) { VisorDiferencias(resultado) }
                }
            }
        }
    }
}

@Composable
private fun DialogoVinculo(ramaActual: String, alConfirmar: (String, String) -> Unit, alCerrar: () -> Unit) {
    var repo by remember { mutableStateOf("") }
    var rama by remember { mutableStateOf(ramaActual.ifBlank { "main" }) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Vincular con GitHub") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { CampoTexto(repo, { repo = it }, "Repositorio (dueño/nombre)"); CampoTexto(rama, { rama = it }, "Rama remota") } },
        confirmButton = { TextButton(onClick = { alConfirmar(repo, rama) }, enabled = repo.contains('/') && rama.isNotBlank()) { Text("Vincular") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun DialogoPublicar(pedirConfirmacion: Boolean, mensajeSugerido: String, rama: String, alConfirmar: (String, String?) -> Unit, alCerrar: () -> Unit) {
    var mensaje by remember { mutableStateOf(mensajeSugerido) }
    var nuevaRama by remember { mutableStateOf("") }
    var usarNueva by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Publicar en GitHub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (usarNueva) "Se creará la rama nueva y se publicará allí." else "Se publicará en la rama $rama como un único commit con todos los archivos modificados.", style = MaterialTheme.typography.bodySmall)
                CampoTexto(mensaje, { mensaje = it }, "Mensaje del commit en GitHub")
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Publicar en una rama nueva", Modifier.weight(1f)); Switch(usarNueva, { usarNueva = it }, colors = SwitchDefaults.colors(checkedTrackColor = LocalTokens.current.acento)) }
                if (usarNueva) CampoTexto(nuevaRama, { nuevaRama = it }, "Nombre de la rama nueva", error = nuevaRama.takeIf { it.isNotEmpty() }?.let { ControlVersiones.validarRama(it) })
                if (pedirConfirmacion) Text("Este contenido saldrá del dispositivo hacia GitHub.", style = MaterialTheme.typography.labelMedium, color = LocalTokens.current.advertencia)
            }
        },
        confirmButton = { TextButton(onClick = { alConfirmar(mensaje, if (usarNueva) nuevaRama else null) }, enabled = mensaje.isNotBlank() && (!usarNueva || (nuevaRama.isNotBlank() && ControlVersiones.validarRama(nuevaRama) == null))) { Text("Publicar") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
fun PanelAgente(
    vm: AgenteViewModel,
    archivoActivo: String?,
    textosAbiertos: () -> Map<String, String>,
    alAbrirAjustes: () -> Unit,
    alGuardarTodo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    var instruccion by remember { mutableStateOf("") }
    val bloques = remember { mutableStateMapOf<String, Set<Int>>() }
    var dialogoDiff by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EncabezadoPanel("Agente de código") {
            if (estado.conversacion.isNotEmpty() || estado.propuesta != null) BotonIcono(Icons.Outlined.Refresh, "Nueva conversación", vm::nuevaConversacion)
        }
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("El agente propone cambios en varios archivos. Tú revisas cada diferencia y decides qué se aplica. Nada se guarda sin tu aceptación.", style = MaterialTheme.typography.bodySmall, color = t.textoTenue)
            CampoTexto(instruccion, { instruccion = it }, "¿Qué quieres cambiar?", unaLinea = false, lineasMinimas = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (estado.trabajando) {
                    BotonSecundario("Cancelar", vm::cancelar, Modifier.weight(1f))
                } else {
                    BotonPrincipal("Enviar al agente", { alGuardarTodo(); vm.enviar(instruccion, archivoActivo, textosAbiertos()); }, Modifier.weight(1f), habilitado = instruccion.isNotBlank(), icono = Icons.Outlined.Send)
                }
                BotonSecundario("Permisos", alAbrirAjustes)
            }
            if (estado.trabajando) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = t.acento); Text("Esperando a ${estado.proveedor ?: "el proveedor"}…", style = MaterialTheme.typography.labelMedium, color = t.textoTenue) }
            estado.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.error, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.fondoError).padding(12.dp)) }
            estado.propuesta?.let { p ->
                Text(p.resumen, style = MaterialTheme.typography.bodyMedium)
                if (estado.omitidos.isNotEmpty()) Text("No se enviaron por límite de contexto: ${estado.omitidos.take(5).joinToString()}${if (estado.omitidos.size > 5) "…" else ""}", style = MaterialTheme.typography.labelSmall, color = t.textoTenue)
                p.rechazados.forEach { Text("Rechazado: $it", style = MaterialTheme.typography.labelSmall, color = t.advertencia) }
                p.cambios.forEach { c ->
                    val aceptado = estado.decisiones[c.ruta] == true
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.superficieElevada).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(aceptado, { vm.decidir(c.ruta, it) }, colors = CheckboxDefaults.colors(checkedColor = t.acento, checkmarkColor = t.textoSobreAcento), modifier = Modifier.semantics { contentDescription = "Aceptar cambio en ${c.ruta}" })
                            Column(Modifier.weight(1f)) {
                                Text(c.ruta, style = MaterialTheme.typography.labelLarge.copy(fontFamily = FuenteMonoJetBrains), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(c.accion.nombreVisible + if (c.motivo.isNotBlank()) " · ${c.motivo}" else "", style = MaterialTheme.typography.labelSmall, color = t.textoTenue)
                            }
                            ResumenDiferencia(c.diferencias.agregadas, c.diferencias.eliminadas)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Etiqueta("Revisar diferencias", alPulsar = { dialogoDiff = c.ruta; if (c.ruta !in bloques) bloques[c.ruta] = c.diferencias.bloques.indices.toSet() })
                            if (c.accion == AccionCambio.MODIFICAR && c.ruta in bloques && bloques.getValue(c.ruta).size != c.diferencias.bloques.size) Etiqueta("Parcial: ${bloques.getValue(c.ruta).size}/${c.diferencias.bloques.size} bloques")
                        }
                    }
                }
                p.comandos.filter { it.comando !in estado.comandosDescartados }.forEach { c ->
                    val resultado = estado.resultadosComandos[c.comando]
                    val colorRiesgo = when (c.riesgo.nivel) { NivelRiesgo.BAJO -> t.exito; NivelRiesgo.MEDIO -> t.informacion; else -> t.advertencia }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.superficieElevada).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$ ${c.comando}", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FuenteMonoJetBrains))
                        Text("Riesgo ${c.riesgo.nivel.name.lowercase()}: ${c.riesgo.motivo}", style = MaterialTheme.typography.labelSmall, color = colorRiesgo)
                        if (c.motivo.isNotBlank()) Text(c.motivo, style = MaterialTheme.typography.labelSmall, color = t.textoTenue)
                        if (resultado == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (estado.comandoEnCurso == c.comando) BotonSecundario("Detener", vm::cancelarComando, Modifier.weight(1f))
                                else BotonPrincipal("Ejecutar", { vm.ejecutarComando(c.comando) }, Modifier.weight(1f), habilitado = estado.comandoEnCurso == null, icono = Icons.Outlined.PlayArrow)
                                BotonSecundario("Descartar", { vm.descartarComando(c.comando) }, Modifier.weight(1f))
                            }
                        } else {
                            Text("Código de salida ${resultado.codigo}", style = MaterialTheme.typography.labelSmall, color = if (resultado.codigo == 0) t.exito else t.error)
                            Text(resultado.salida.take(2000), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains), color = t.texto)
                        }
                    }
                }
                if (p.cambios.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonPrincipal("Aplicar ${estado.decisiones.count { it.value }}", { vm.aplicar(bloques.toMap()); bloques.clear() }, Modifier.weight(1f), habilitado = estado.decisiones.any { it.value }, icono = Icons.Outlined.Check)
                        BotonSecundario("Rechazar todo", { vm.descartarPropuesta(); bloques.clear() }, Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    dialogoDiff?.let { ruta ->
        val c = estado.propuesta?.cambios?.firstOrNull { it.ruta == ruta }
        if (c == null) dialogoDiff = null else DialogoPantallaCompleta("${c.accion.nombreVisible} · $ruta", { dialogoDiff = null }) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (c.accion == AccionCambio.MODIFICAR) "Marca los bloques que quieres aceptar." else "Se aplicará completo.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Etiqueta(if (estado.decisiones[ruta] == true) "Aceptado" else "Rechazado", seleccionada = estado.decisiones[ruta] == true, alPulsar = { vm.decidir(ruta, estado.decisiones[ruta] != true) })
                }
                VisorDiferencias(
                    c.diferencias, Modifier.weight(1f),
                    bloquesAceptados = if (c.accion == AccionCambio.MODIFICAR) bloques[ruta].orEmpty() else null,
                    alAlternarBloque = if (c.accion == AccionCambio.MODIFICAR) { i -> val actual = bloques[ruta].orEmpty(); bloques[ruta] = if (i in actual) actual - i else actual + i } else null
                )
            }
        }
    }
}

@Composable
fun PanelExtensiones(vm: ExtensionesViewModel, modifier: Modifier = Modifier) {
    val lista by vm.extensiones.collectAsStateWithLifecycle()
    val prefs by vm.preferencias.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    var detalle by remember { mutableStateOf<ManifiestoExtension?>(null) }
    var quitar by remember { mutableStateOf<String?>(null) }
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::instalar) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EncabezadoPanel("Extensiones") { BotonIcono(Icons.Outlined.Add, "Instalar desde archivo", { selector.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/plain")) }) }
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Redact Code instala extensiones declarativas: fragmentos y temas de color. Acepta un manifiesto .json propio o un paquete .vsix de VS Code, del que solo se leen sus fragmentos y temas. El código de las extensiones nunca se ejecuta.", style = MaterialTheme.typography.bodySmall, color = t.textoTenue)
            BotonSecundario("Instalar desde archivo", { selector.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/plain")) }, Modifier.fillMaxWidth(), icono = Icons.Outlined.Extension)
        }
        Spacer(Modifier.height(8.dp))
        lista.forEach { e ->
            val m = e.manifiesto
            Row(Modifier.fillMaxWidth().clickable { detalle = m }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.nombre, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("v${m.version}${if (m.autor.isNotBlank()) " · ${m.autor}" else ""} · ${m.fragmentos.size} fragmentos · ${m.temas.size} temas", style = MaterialTheme.typography.labelSmall, color = t.textoTenue)
                }
                Switch(e.activa, { vm.activar(m.id, it) }, colors = SwitchDefaults.colors(checkedTrackColor = t.acento), modifier = Modifier.semantics { contentDescription = "Activar ${m.nombre}" })
            }
        }
        CatalogoOpenVsx(vm)
        val temas = vm.temas()
        if (temas.isNotEmpty()) {
            EncabezadoPanel("Tema del editor")
            Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Etiqueta("Predeterminado", seleccionada = prefs.temaEditor == pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario.TEMA_EDITOR_PREDETERMINADO, alPulsar = { vm.elegirTema(pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario.TEMA_EDITOR_PREDETERMINADO) })
                temas.forEach { (clave, tema) -> Etiqueta(tema.nombre, seleccionada = prefs.temaEditor == clave, alPulsar = { vm.elegirTema(clave) }) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    detalle?.let { m ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { detalle = null },
            title = { Text(m.nombre) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (m.descripcion.isNotBlank()) Text(m.descripcion, style = MaterialTheme.typography.bodyMedium)
                    Text("Identificador: ${m.id}\nVersión: ${m.version}\nOrigen: ${if (m.origen == ManifiestoExtension.ORIGEN_VSCODE) "paquete de VS Code (.vsix)" else "Redact Code"}\nLicencia: ${m.licencia.ifBlank { "no indicada" }}", style = MaterialTheme.typography.bodySmall)
                    if (m.omitido.isNotEmpty()) {
                        Text("No compatible (se omitió):", style = MaterialTheme.typography.labelLarge, color = t.advertencia)
                        m.omitido.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detalle = null }) { Text("Cerrar") } },
            dismissButton = { if (m.id != pe.redactcode.nucleo.extensiones.FragmentosBase.ID) TextButton(onClick = { quitar = m.id; detalle = null }) { Text("Desinstalar", color = t.error) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
    quitar?.let { id -> DialogoConfirmacion("Desinstalar extensión", "Se eliminarán sus fragmentos y temas. Puedes volver a instalarla desde el archivo.", "Desinstalar", true, { vm.desinstalar(id); quitar = null }, { quitar = null }) }
}

@Composable
private fun CatalogoOpenVsx(vm: ExtensionesViewModel) {
    val estado by vm.catalogo.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    var consulta by remember { mutableStateOf(estado.consulta) }
    var categoria by remember { mutableStateOf<String?>(null) }
    EncabezadoPanel("Catálogo Open VSX")
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Busca temas y fragmentos en el registro abierto open-vsx.org. Solo se instalan esos dos tipos de contenido; el código de las extensiones nunca se ejecuta. Cada autor define su licencia.", style = MaterialTheme.typography.bodySmall, color = t.textoTenue)
        CampoTexto(consulta, { consulta = it }, "Buscar extensiones")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Etiqueta("Todo", seleccionada = categoria == null, alPulsar = { categoria = null })
            Etiqueta("Temas", seleccionada = categoria == "Themes", alPulsar = { categoria = "Themes" })
            Etiqueta("Fragmentos", seleccionada = categoria == "Snippets", alPulsar = { categoria = "Snippets" })
            BotonPrincipal("Buscar", { vm.buscarCatalogo(consulta, categoria) }, habilitado = consulta.isNotBlank() && !estado.buscando)
        }
        if (estado.buscando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = t.acento)
        estado.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.error) }
        if (!estado.buscando && estado.error == null && estado.consulta.isNotBlank() && estado.resultados.isEmpty()) Text("Sin resultados", style = MaterialTheme.typography.bodySmall, color = t.textoTenue)
    }
    estado.resultados.forEach { e ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.titulo, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${e.identificador} · v${e.version} · ${e.downloadCount} descargas" + (e.license?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = t.textoTenue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                e.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.textoSecundario, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            BotonSecundario(if (estado.instalando == e.identificador) "Instalando…" else "Instalar", { vm.instalarDelCatalogo(e) }, habilitado = estado.instalando == null && e.files.download != null)
        }
    }
}
