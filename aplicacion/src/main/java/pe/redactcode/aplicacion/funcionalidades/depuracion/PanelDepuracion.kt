package pe.redactcode.aplicacion.funcionalidades.depuracion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.funcionalidades.espacio.EntradaComando
import pe.redactcode.aplicacion.nucleo.diseno.ChipAccion
import pe.redactcode.aplicacion.nucleo.diseno.FuenteMonoJetBrains
import pe.redactcode.aplicacion.nucleo.diseno.LocalTokens
import pe.redactcode.nucleo.depuracion.EstadoDepuracion
import pe.redactcode.nucleo.depuracion.FaseSesion
import pe.redactcode.nucleo.modelo.Lenguaje

@Composable
fun PanelDepuracion(
    vm: DepuradorViewModel,
    rutaActiva: String?,
    lenguajeActivo: Lenguaje?,
    textoActivo: () -> String,
    lineaCursor: () -> Int,
    alIrALinea: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val puntos by vm.puntos.collectAsStateWithLifecycle()
    val archivoSesion by vm.archivo.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    val enSesion = estado.fase != FaseSesion.INACTIVA && estado.fase != FaseSesion.TERMINADA
    val pausada = estado.fase == FaseSesion.PAUSADA
    val scrollBotones = rememberScrollState()
    val alcance = rememberCoroutineScope()
    LaunchedEffect(estado.lineaActual, rutaActiva) { if (rutaActiva != null && rutaActiva == archivoSesion) estado.lineaActual?.let(alIrALinea) }

    Column(modifier.fillMaxSize().background(t.panelInferior)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (scrollBotones.canScrollBackward) {
                Box(
                    Modifier
                        .height(40.dp)
                        .clickable { alcance.launch { scrollBotones.animateScrollTo((scrollBotones.value - 240).coerceAtLeast(0)) } }
                        .padding(horizontal = 4.dp)
                        .semantics { contentDescription = "Botones anteriores del depurador"; role = Role.Button },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = t.acento, modifier = Modifier.size(20.dp))
                }
            }
            Row(Modifier.weight(1f).horizontalScroll(scrollBotones).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val puedeIniciar = !enSesion && rutaActiva != null && lenguajeActivo == Lenguaje.PYTHON
                ChipAccion("Ejecutar", { vm.iniciar(rutaActiva.orEmpty(), Lenguaje.PYTHON, textoActivo(), false) }, habilitado = puedeIniciar)
                ChipAccion("Depurar", { vm.iniciar(rutaActiva.orEmpty(), Lenguaje.PYTHON, textoActivo(), true) }, habilitado = puedeIniciar)
                ChipAccion("Continuar", { vm.comando("continuar") }, habilitado = pausada)
                ChipAccion("Paso", { vm.comando("paso") }, habilitado = pausada)
                ChipAccion("Entrar", { vm.comando("entrar") }, habilitado = pausada)
                ChipAccion("Salir", { vm.comando("salir") }, habilitado = pausada)
                ChipAccion("Detener", { vm.detener() }, habilitado = enSesion, color = t.error)
                ChipAccion("Limpiar", { vm.limpiar() }, habilitado = !enSesion)
            }
            if (scrollBotones.canScrollForward) {
                Box(
                    Modifier
                        .height(40.dp)
                        .clickable { alcance.launch { scrollBotones.animateScrollTo((scrollBotones.value + 240).coerceAtMost(scrollBotones.maxValue)) } }
                        .padding(horizontal = 4.dp)
                        .semantics { contentDescription = "Más botones del depurador"; role = Role.Button },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = t.acento, modifier = Modifier.size(20.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipAccion("Punto en la línea del cursor", { rutaActiva?.let { vm.alternarPunto(it, lineaCursor()) } }, habilitado = rutaActiva != null)
            rutaActiva?.let { ruta -> puntos[ruta].orEmpty().forEach { l -> ChipAccion("Quitar punto $l", { vm.alternarPunto(ruta, l) }, activo = true) } }
        }
        if (lenguajeActivo != null && lenguajeActivo != Lenguaje.PYTHON && !enSesion) {
            Text("La ejecución interactiva y la depuración solo funcionan con Python.", style = MaterialTheme.typography.labelSmall, color = t.textoTenue, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            estado.lineaActual?.let { Text("Pausado en ${archivoSesion ?: ""} línea $it", style = MaterialTheme.typography.labelLarge, color = t.advertencia) }
            if (estado.salida.isNotEmpty()) {
                SelectionContainer {
                    Text(salidaAnotada(estado, t.texto, t.error), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains))
                }
            }
            if (estado.fase == FaseSesion.TERMINADA) {
                val ok = estado.estadoFinal == "EXITO"
                Text(
                    (if (ok) "Terminó correctamente" else "${estado.estadoFinal ?: "Terminó"}: ${estado.mensajeFinal.orEmpty()}") + (estado.lineaError?.let { " (línea $it)" } ?: "") + " · ${estado.duracionMs} ms",
                    style = MaterialTheme.typography.labelMedium, color = if (ok) t.exito else t.error
                )
            }
            if (pausada) {
                Text("Pila de llamadas", style = MaterialTheme.typography.labelLarge)
                estado.pila.forEach { m -> Text("${m.funcion} · línea ${m.linea}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains), color = t.textoSecundario) }
                Text("Variables", style = MaterialTheme.typography.labelLarge)
                if (estado.variables.isEmpty()) Text("Sin variables en este ámbito", style = MaterialTheme.typography.labelSmall, color = t.textoTenue)
                estado.variables.forEach { v -> Text("${v.nombre}: ${v.tipo} = ${v.valor}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains)) }
                estado.evaluaciones.forEach { e -> Text("> ${e.expresion}\n${e.resultado}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains), color = if (e.error) t.error else t.texto) }
            }
        }
        if (estado.fase == FaseSesion.EJECUTANDO || estado.fase == FaseSesion.ESPERANDO_ENTRADA) {
            EntradaComando(
                "input>",
                estado.fase == FaseSesion.ESPERANDO_ENTRADA,
                13,
                vm::enviarEntrada,
                "Entrada del programa",
                solicitarFocoAlHabilitar = true
            )
        }
        if (pausada) EntradaComando("eval>", true, 13, vm::evaluar, "Evaluar expresión")
    }
}

private fun salidaAnotada(e: EstadoDepuracion, normal: androidx.compose.ui.graphics.Color, error: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    e.salida.forEach { s -> withStyle(SpanStyle(color = if (s.error) error else normal)) { append(s.texto) } }
}
