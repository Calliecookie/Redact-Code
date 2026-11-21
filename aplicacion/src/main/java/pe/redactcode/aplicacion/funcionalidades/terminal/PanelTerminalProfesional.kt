package pe.redactcode.aplicacion.funcionalidades.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.redactcode.aplicacion.funcionalidades.espacio.BotonIcono
import pe.redactcode.aplicacion.funcionalidades.espacio.PanelTerminal
import pe.redactcode.aplicacion.funcionalidades.espacio.TerminalViewModel
import pe.redactcode.aplicacion.nucleo.diseno.ChipAccion
import pe.redactcode.aplicacion.nucleo.diseno.FuenteMonoJetBrains
import pe.redactcode.aplicacion.nucleo.diseno.LocalTokens

object TeclasEspeciales {
    const val ESCAPE = "\u001b"
    const val TABULADOR = "\t"
    const val ARRIBA = "\u001b[A"
    const val ABAJO = "\u001b[B"
    const val DERECHA = "\u001b[C"
    const val IZQUIERDA = "\u001b[D"
    const val INICIO = "\u001b[H"
    const val FIN = "\u001b[F"
}

@Composable
fun PanelTerminalProfesional(vm: TerminalViewModel, modifier: Modifier = Modifier) {
    val sesiones by vm.sesionesPty.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    var seleccionada by remember { mutableIntStateOf(-1) }
    var segunda by remember { mutableIntStateOf(-1) }
    var dividido by remember { mutableStateOf(false) }
    var foco by remember { mutableIntStateOf(0) }
    var verTexto by remember { mutableStateOf(false) }
    var tamano by remember { mutableIntStateOf(13) }
    val modificadores = remember { ModificadoresTeclas() }

    LaunchedEffect(Unit) { vm.iniciar(); vm.asegurarSesion() }
    LaunchedEffect(sesiones.size) {
        if (sesiones.none { it.id == seleccionada }) seleccionada = sesiones.lastOrNull()?.id ?: -1
    }
    val panelA = sesiones.firstOrNull { it.id == seleccionada }
    val panelB = if (dividido) sesiones.firstOrNull { it.id == segunda && it.id != seleccionada } ?: sesiones.firstOrNull { it.id != seleccionada } else null
    LaunchedEffect(sesiones.size, seleccionada) {
        if (dividido && panelB == null) dividido = false
        if (panelB != null) segunda = panelB.id
        if (foco == 1 && panelB == null) foco = 0
    }
    val actual = if (foco == 1 && panelB != null) panelB else panelA
    val estado = actual?.estado?.collectAsStateWithLifecycle()?.value

    Column(modifier.fillMaxSize().background(t.panelInferior)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                sesiones.forEach { s ->
                    val esActual = s.id == actual?.id
                    ChipAccion(s.titulo, {
                        verTexto = false
                        if (foco == 1 && dividido && s.id != seleccionada) segunda = s.id
                        else if (dividido && s.id == segunda) { segunda = seleccionada; seleccionada = s.id }
                        else { seleccionada = s.id; if (foco == 1) foco = 0 }
                    }, activo = esActual)
                }
            }
            BotonIcono(Icons.Outlined.Add, "Nueva sesión de terminal", vm::nuevaSesion, habilitado = sesiones.size < GestorSesionesTerminal.LIMITE_SESIONES)
            BotonIcono(Icons.Outlined.ViewColumn, if (dividido) "Quitar la división" else "Dividir el panel", {
                if (dividido) { dividido = false; foco = 0 }
                else {
                    if (sesiones.size < 2) vm.nuevaSesion()
                    dividido = true
                }
            }, habilitado = sesiones.isNotEmpty())
            BotonIcono(Icons.Outlined.TextFields, "Ver como texto", { verTexto = !verTexto }, habilitado = actual?.sesion != null)
            BotonIcono(Icons.Outlined.ContentPaste, "Pegar", { actual?.alPegar?.invoke() }, habilitado = actual?.sesion != null)
            BotonIcono(Icons.Outlined.Stop, "Interrumpir con Ctrl+C", { actual?.interrumpir() }, habilitado = estado is EstadoSesion.Activa, tinte = t.error)
            BotonIcono(Icons.Outlined.Close, "Cerrar sesión", { actual?.let { vm.cerrarSesion(it.id) } }, habilitado = actual != null)
        }
        when {
            actual == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Sin sesiones. Pulsa + para abrir una.", color = t.textoTenue, style = MaterialTheme.typography.bodySmall)
            }
            actual.sesion == null -> Column(Modifier.weight(1f)) {
                Text(
                    "La terminal interactiva no está disponible en este dispositivo (${(estado as? EstadoSesion.Fallida)?.motivo.orEmpty()}). Se usa la terminal no interactiva.",
                    color = t.advertencia, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp)
                )
                PanelTerminal(vm, Modifier.weight(1f))
            }
            verTexto -> {
                val texto = actual.transcripcion()
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(Int.MAX_VALUE))) {
                    SelectionContainer {
                        Text(
                            texto.ifBlank { "(vacío)" }, color = t.texto, fontFamily = FuenteMonoJetBrains,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp).semantics { contentDescription = "Contenido de la terminal" }
                        )
                    }
                }
            }
            else -> {
                if (panelB != null && panelA != null) {
                    val ancho = pe.redactcode.aplicacion.nucleo.diseno.anchoVentanaDp()
                    val paneles = @Composable { i: Int, s: SesionPty, m: Modifier ->
                        Box(
                            m.border(2.dp, if (foco == i) t.bordeFoco else Color.Transparent).pointerInput(i) {
                                awaitPointerEventScope { while (true) { val e = awaitPointerEvent(PointerEventPass.Initial); if (e.type == PointerEventType.Press) foco = i } }
                            }
                        ) { VistaTerminal(s, modificadores, tamano, { tamano = LimitesFuente.acotar(it) }, Modifier.fillMaxSize()) }
                    }
                    if (ancho >= 600) Row(Modifier.weight(1f).fillMaxWidth()) { paneles(0, panelA, Modifier.weight(1f).fillMaxSize()); paneles(1, panelB, Modifier.weight(1f).fillMaxSize()) }
                    else Column(Modifier.weight(1f).fillMaxWidth()) { paneles(0, panelA, Modifier.weight(1f).fillMaxWidth()); paneles(1, panelB, Modifier.weight(1f).fillMaxWidth()) }
                } else {
                    VistaTerminal(actual, modificadores, tamano, { tamano = LimitesFuente.acotar(it) }, Modifier.weight(1f).fillMaxWidth())
                }
                if (estado is EstadoSesion.Terminada) {
                    Text("Sesión terminada con código ${estado.codigo}. Cierra la pestaña o abre otra.", color = t.textoTenue, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp))
                }
                TeclasExtra(modificadores, estado is EstadoSesion.Activa) { actual.escribir(it) }
            }
        }
    }
}

@Composable
fun TeclasExtra(modificadores: ModificadoresTeclas, habilitado: Boolean, alEnviar: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChipAccion("Esc", { alEnviar(TeclasEspeciales.ESCAPE) }, habilitado = habilitado)
        ChipAccion("Tab", { alEnviar(TeclasEspeciales.TABULADOR) }, habilitado = habilitado)
        ChipAccion("Ctrl", { modificadores.control = !modificadores.control }, habilitado = habilitado, activo = modificadores.control)
        ChipAccion("Alt", { modificadores.alternativa = !modificadores.alternativa }, habilitado = habilitado, activo = modificadores.alternativa)
        listOf(
            "←" to TeclasEspeciales.IZQUIERDA, "↑" to TeclasEspeciales.ARRIBA, "↓" to TeclasEspeciales.ABAJO, "→" to TeclasEspeciales.DERECHA,
            "Inicio" to TeclasEspeciales.INICIO, "Fin" to TeclasEspeciales.FIN
        ).forEach { (nombre, secuencia) -> ChipAccion(nombre, { alEnviar(secuencia) }, habilitado = habilitado) }
        listOf("-", "/", "|", "~", "_", "$", "\\", "*").forEach { simbolo -> ChipAccion(simbolo, { alEnviar(simbolo) }, habilitado = habilitado) }
    }
}
