package pe.redactcode.aplicacion.funcionalidades.espacio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.FuenteMonoJetBrains
import pe.redactcode.aplicacion.nucleo.diseno.LocalTokens
import pe.redactcode.nucleo.modelo.Lenguaje

@Composable
private fun SalidaLineas(lineas: List<LineaTerminal>, tamano: Int, modifier: Modifier = Modifier, pie: @Composable () -> Unit) {
    val t = LocalTokens.current
    val estado = rememberLazyListState()
    LaunchedEffect(lineas.size) { if (lineas.isNotEmpty()) estado.animateScrollToItem(lineas.size) }
    val estilo = TextStyle(fontFamily = FuenteMonoJetBrains, fontSize = tamano.sp, lineHeight = (tamano * 1.45f).sp)
    SelectionContainer {
        LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp), state = estado) {
            items(lineas.size) { i ->
                val l = lineas[i]
                Text(
                    l.texto,
                    style = estilo,
                    color = when (l.tipo) {
                        TipoLineaTerminal.COMANDO -> t.acentoSecundario
                        TipoLineaTerminal.SALIDA -> t.texto
                        TipoLineaTerminal.ERROR -> t.error
                        TipoLineaTerminal.INFO -> t.textoTenue
                    }
                )
            }
            item { pie() }
        }
    }
}

@Composable
private fun EntradaComando(
    prefijo: String,
    habilitado: Boolean,
    tamano: Int,
    alEnviar: (String) -> Unit,
    descripcion: String,
    modifier: Modifier = Modifier,
    alHistorial: ((Boolean) -> String?)? = null,
    alCompletar: ((String) -> String?)? = null
) {
    val t = LocalTokens.current
    var texto by remember { mutableStateOf("") }
    val teclado = LocalSoftwareKeyboardController.current
    val estilo = TextStyle(fontFamily = FuenteMonoJetBrains, fontSize = tamano.sp, color = t.texto)
    Row(modifier.fillMaxWidth().background(t.panelInferior).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(prefijo, style = estilo.copy(color = t.acentoSecundario), maxLines = 1)
        Box(Modifier.weight(1f).padding(start = 6.dp)) {
            BasicTextField(
                value = texto,
                onValueChange = { texto = it },
                enabled = habilitado,
                singleLine = true,
                textStyle = estilo,
                cursorBrush = SolidColor(t.acentoSecundario),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { val c = texto; texto = ""; alEnviar(c) }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = descripcion }
            )
        }
        if (alHistorial != null) {
            BotonIcono(Icons.Outlined.KeyboardArrowUp, "Comando anterior", { alHistorial(true)?.let { texto = it } })
            BotonIcono(Icons.Outlined.KeyboardArrowDown, "Comando siguiente", { alHistorial(false)?.let { texto = it } })
        }
        if (alCompletar != null) Etiqueta("Tab", alPulsar = { alCompletar(texto)?.let { texto = it } })
    }
}

@Composable
fun PanelTerminal(vm: TerminalViewModel, modifier: Modifier = Modifier) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val prefs by vm.preferencias.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    LaunchedEffect(Unit) { vm.iniciar() }
    Column(modifier.fillMaxSize().background(t.panelInferior)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Shell del sistema · ${estado.ruta}", style = MaterialTheme.typography.labelSmall, color = t.textoTenue, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
            if (estado.ejecutando) BotonIcono(Icons.Outlined.Stop, "Detener comando", vm::cancelar, tinte = t.error)
            BotonIcono(Icons.Outlined.CleaningServices, "Limpiar terminal", vm::limpiar)
        }
        Box(Modifier.weight(1f)) { SalidaLineas(estado.lineas, prefs.tamanoFuenteTerminal) {} }
        EntradaComando(
            prefijo = "${estado.ruta} $", habilitado = !estado.ejecutando, tamano = prefs.tamanoFuenteTerminal,
            alEnviar = vm::ejecutar, descripcion = "Línea de comandos", alHistorial = vm::historial, alCompletar = vm::autocompletar
        )
    }
}

@Composable
fun PanelConsola(vm: TerminalViewModel, modifier: Modifier = Modifier) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val prefs by vm.preferencias.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    val esPython = estado.lenguajeConsola == Lenguaje.PYTHON
    Column(modifier.fillMaxSize().background(t.panelInferior)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Etiqueta("JavaScript", seleccionada = !esPython, alPulsar = { vm.lenguajeConsola(Lenguaje.JAVASCRIPT) })
            Etiqueta("Python", seleccionada = esPython, alPulsar = { vm.lenguajeConsola(Lenguaje.PYTHON) })
            Box(Modifier.weight(1f))
            BotonIcono(Icons.Outlined.RestartAlt, "Reiniciar sesión", vm::reiniciarConsola)
        }
        Text(
            if (esPython) "Sesión de Python con variables persistentes. Se evalúa expresión por expresión."
            else "Sesión de JavaScript: se vuelven a ejecutar tus entradas correctas anteriores para conservar el estado.",
            style = MaterialTheme.typography.labelSmall, color = t.textoTenue, modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(Modifier.weight(1f)) { SalidaLineas(estado.consola, prefs.tamanoFuenteTerminal) {} }
        EntradaComando(if (esPython) ">>>" else ">", !estado.evaluando, prefs.tamanoFuenteTerminal, vm::evaluar, "Entrada de la consola ${if (esPython) "Python" else "JavaScript"}")
    }
}
