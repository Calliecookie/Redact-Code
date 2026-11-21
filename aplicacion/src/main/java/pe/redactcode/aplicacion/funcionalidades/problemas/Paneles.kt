package pe.redactcode.aplicacion.funcionalidades.problemas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.redactcode.aplicacion.nucleo.diseno.BloqueCodigo
import pe.redactcode.aplicacion.nucleo.diseno.Cargando
import pe.redactcode.aplicacion.nucleo.diseno.CampoTexto
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.EstadoVacio
import pe.redactcode.aplicacion.nucleo.diseno.FuenteMonoJetBrains
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.Separador
import pe.redactcode.aplicacion.nucleo.diseno.colorSeveridad
import pe.redactcode.nucleo.mentor.PropuestaCorreccion
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.EstadoEjecucion
import pe.redactcode.nucleo.modelo.ResultadoEjecucion
import pe.redactcode.nucleo.modelo.Severidad
import pe.redactcode.nucleo.revision.InformeRevision
import pe.redactcode.nucleo.revision.TipoHallazgo

@Composable
fun DetalleDiagnostico(d: Diagnostico, propuesta: PropuestaCorreccion?, alCorregir: (() -> Unit)?, alPreguntarMentor: () -> Unit) {
    val e = d.explicacion
    Column(Modifier.fillMaxWidth().padding(start = 36.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Etiqueta(d.lenguaje.nombreVisible)
            Etiqueta("Línea ${d.linea}, col. ${d.columna}")
            Etiqueta(d.codigo)
        }
        Text(d.tipo, style = MaterialTheme.typography.labelMedium, color = colorSeveridad(d.severidad))
        if (e != null) {
            Text(e.explicacion, style = MaterialTheme.typography.bodyMedium)
            CampoInforme("Causa probable", e.causaProbable)
            if (e.ejemploIncorrecto.isNotBlank()) {
                Text("Ejemplo incorrecto", style = MaterialTheme.typography.labelMedium, color = LocalColoresExtendidos.current.error)
                BloqueCodigo(e.ejemploIncorrecto)
            }
            if (e.ejemploCorregido.isNotBlank()) {
                Text("Ejemplo corregido", style = MaterialTheme.typography.labelMedium, color = LocalColoresExtendidos.current.exito)
                BloqueCodigo(e.ejemploCorregido)
            }
            CampoInforme("Recomendación", e.recomendacion)
        }
        CampoInforme("Mensaje del analizador", d.mensajeOriginal)
        if (propuesta != null) {
            Text("Corrección sugerida en la línea ${propuesta.linea}: ${propuesta.descripcion}", style = MaterialTheme.typography.bodySmall)
            BloqueCodigo(propuesta.corregida.trim())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (propuesta != null && alCorregir != null) {
                TextButton(onClick = alCorregir) {
                    Icon(Icons.Outlined.AutoFixHigh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aplicar corrección")
                }
            }
            TextButton(onClick = alPreguntarMentor) {
                Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Preguntar al mentor")
            }
        }
    }
}

@Composable
private fun CampoInforme(titulo: String, texto: String) {
    Column {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = LocalColoresExtendidos.current.textoTenue)
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PanelProblemas(
    diagnosticos: List<Diagnostico>,
    motor: String,
    duracionMs: Long,
    propuestaPara: (Diagnostico) -> PropuestaCorreccion?,
    alIrALinea: (Diagnostico) -> Unit,
    alCorregir: (PropuestaCorreccion) -> Unit,
    alPreguntarMentor: (Diagnostico) -> Unit,
    modifier: Modifier = Modifier
) {
    if (diagnosticos.isEmpty()) {
        EstadoVacio(Icons.Outlined.CheckCircle, "Sin problemas", "El analizador no encontró errores en este archivo.\n$motor · $duracionMs ms", modifier)
        return
    }
    var expandido by remember { mutableStateOf<Int?>(null) }
    LazyColumn(modifier) {
        item {
            Text("$motor · $duracionMs ms", style = MaterialTheme.typography.labelSmall, color = LocalColoresExtendidos.current.textoTenue, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        items(diagnosticos.size) { indice ->
            val d = diagnosticos[indice]
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { expandido = if (expandido == indice) null else indice; alIrALinea(d) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        when (d.severidad) {
                            Severidad.ERROR -> Icons.Outlined.ErrorOutline
                            Severidad.ADVERTENCIA -> Icons.Outlined.WarningAmber
                            Severidad.INFORMACION -> Icons.Outlined.Info
                        },
                        contentDescription = d.severidad.name.lowercase(), tint = colorSeveridad(d.severidad), modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Línea ${d.linea}, columna ${d.columna} · ${d.codigo}", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                    }
                }
                AnimatedVisibility(expandido == indice) {
                    val propuesta = propuestaPara(d)
                    DetalleDiagnostico(d, propuesta, propuesta?.let { { alCorregir(it) } }) { alPreguntarMentor(d) }
                }
                Separador()
            }
        }
    }
}

@Composable
fun PanelEjecucion(
    resultado: ResultadoEjecucion?,
    ejecutando: Boolean,
    entrada: String,
    mostrarEntrada: Boolean,
    alCambiarEntrada: (String) -> Unit,
    alIrALinea: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (mostrarEntrada) {
            CampoTexto(entrada, alCambiarEntrada, "Entrada (una línea por cada input())", unaLinea = false, monoespaciado = true, lineasMinimas = 2)
        }
        val extendidos = LocalColoresExtendidos.current
        if (resultado != null) {
            val (texto, color) = when (resultado.estado) {
                EstadoEjecucion.EN_CURSO -> "Ejecutando…" to extendidos.informacion
                EstadoEjecucion.EXITO -> "Finalizado correctamente" to extendidos.exito
                EstadoEjecucion.ERROR -> "Terminó con error" to extendidos.error
                EstadoEjecucion.TIEMPO_AGOTADO -> "Tiempo agotado" to extendidos.advertencia
                EstadoEjecucion.CANCELADO -> "Cancelado" to extendidos.textoTenue
                EstadoEjecucion.NO_DISPONIBLE -> "Motor no disponible" to extendidos.advertencia
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(texto, style = MaterialTheme.typography.labelLarge, color = color)
                if (resultado.estado != EstadoEjecucion.EN_CURSO) Text("${resultado.duracionMs} ms", style = MaterialTheme.typography.labelMedium, color = extendidos.textoTenue)
            }
            if (ejecutando) Cargando(Modifier.height(48.dp))
            Text("Salida", style = MaterialTheme.typography.labelMedium, color = extendidos.textoTenue)
            Consola(resultado.salida.ifEmpty { if (ejecutando) "" else "(sin salida)" }, MaterialTheme.colorScheme.onSurface)
            if (resultado.errores.isNotBlank()) {
                Text("Errores", style = MaterialTheme.typography.labelMedium, color = extendidos.error)
                Consola(resultado.errores, extendidos.error)
            }
            resultado.lineaExcepcion?.let { linea ->
                TextButton(onClick = { alIrALinea(linea) }) { Text("Ir a la línea $linea") }
            }
        } else {
            Text("Pulsa Ejecutar para correr el programa. La ejecución ocurre en tu dispositivo con un límite de tiempo.", style = MaterialTheme.typography.bodyMedium, color = extendidos.textoTenue)
        }
    }
}

@Composable
fun Consola(texto: String, color: Color, modifier: Modifier = Modifier) {
    SelectionContainer {
        Text(
            texto,
            modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).horizontalScroll(rememberScrollState()).padding(12.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains, fontSize = 12.5.sp, lineHeight = 18.sp),
            color = color,
            softWrap = false
        )
    }
}

@Composable
fun PanelRevision(informe: InformeRevision?, revisando: Boolean, alIrALinea: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (revisando) {
        Cargando(modifier)
        return
    }
    if (informe == null) {
        EstadoVacio(Icons.Outlined.Shield, "Revisión de calidad", "Ejecuta la revisión para analizar este archivo.", modifier)
        return
    }
    LazyColumn(modifier) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Etiqueta("${informe.confirmados.size} errores confirmados", color = LocalColoresExtendidos.current.error)
                    Etiqueta("${informe.advertencias.size} advertencias", color = LocalColoresExtendidos.current.advertencia)
                }
                Text(informe.avisoAlcance, style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                if (informe.herramientas.isNotEmpty()) Text("Herramientas: ${informe.herramientas.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
            }
            Separador()
        }
        if (informe.hallazgos.isEmpty()) {
            item { EstadoVacio(Icons.Outlined.CheckCircle, "Sin observaciones", "No se detectaron problemas con las reglas disponibles.") }
        }
        items(informe.hallazgos) { h ->
            var abierto by remember { mutableStateOf(false) }
            val color = when (h.tipo) {
                TipoHallazgo.CONFIRMADO -> LocalColoresExtendidos.current.error
                TipoHallazgo.SEGURIDAD -> LocalColoresExtendidos.current.error
                TipoHallazgo.ADVERTENCIA -> LocalColoresExtendidos.current.advertencia
                TipoHallazgo.SUGERENCIA -> LocalColoresExtendidos.current.informacion
            }
            Column(Modifier.fillMaxWidth().clickable { abierto = !abierto; alIrALinea(h.linea) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(h.tipo.nombreVisible.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
                Text(h.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("Línea ${h.linea}", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                AnimatedVisibility(abierto) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(h.detalle, style = MaterialTheme.typography.bodyMedium)
                        Text("Recomendación: ${h.recomendacion}", style = MaterialTheme.typography.bodyMedium)
                        if (h.ejemploAntes.isNotBlank()) { Text("Antes", style = MaterialTheme.typography.labelMedium); BloqueCodigo(h.ejemploAntes) }
                        if (h.ejemploDespues.isNotBlank()) { Text("Después", style = MaterialTheme.typography.labelMedium); BloqueCodigo(h.ejemploDespues) }
                    }
                }
            }
            Separador()
        }
    }
}

@Composable
fun PanelConsolaWeb(lineas: List<pe.redactcode.aplicacion.funcionalidades.editor.LineaConsola>, modifier: Modifier = Modifier) {
    if (lineas.isEmpty()) {
        Text("La consola está vacía. Los mensajes de console.log y los errores de la página aparecerán aquí.", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue, modifier = modifier.padding(12.dp))
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(lineas) { l ->
            val color = when (l.nivel) {
                "error" -> LocalColoresExtendidos.current.error
                "aviso" -> LocalColoresExtendidos.current.advertencia
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                (if (l.linea > 0) "[${l.linea}] " else "") + l.mensaje,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains),
                color = color,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
