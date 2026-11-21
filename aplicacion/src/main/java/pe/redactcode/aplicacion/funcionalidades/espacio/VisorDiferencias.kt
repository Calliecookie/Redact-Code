package pe.redactcode.aplicacion.funcionalidades.espacio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.redactcode.aplicacion.nucleo.diseno.FuenteMonoJetBrains
import pe.redactcode.aplicacion.nucleo.diseno.LocalTokens
import pe.redactcode.nucleo.ide.BloqueDiferencia
import pe.redactcode.nucleo.ide.LineaDiferencia
import pe.redactcode.nucleo.ide.ResultadoDiferencias
import pe.redactcode.nucleo.ide.TipoCambioLinea

private sealed interface ElementoDiferencia {
    data class Encabezado(val indice: Int, val bloque: BloqueDiferencia) : ElementoDiferencia
    data class Linea(val linea: LineaDiferencia) : ElementoDiferencia
}

@Composable
fun VisorDiferencias(
    resultado: ResultadoDiferencias,
    modifier: Modifier = Modifier,
    bloquesAceptados: Set<Int>? = null,
    alAlternarBloque: ((Int) -> Unit)? = null
) {
    val t = LocalTokens.current
    val elementos = remember(resultado) {
        buildList {
            resultado.bloques.forEachIndexed { i, b ->
                add(ElementoDiferencia.Encabezado(i, b))
                b.lineas.forEach { add(ElementoDiferencia.Linea(it)) }
            }
        }
    }
    if (resultado.sinCambios) {
        Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Sin diferencias", style = MaterialTheme.typography.bodyMedium, color = t.textoTenue)
        }
        return
    }
    val desplazamiento = rememberScrollState()
    LazyColumn(modifier.fillMaxWidth()) {
        itemsIndexed(elementos) { _, e ->
            when (e) {
                is ElementoDiferencia.Encabezado -> Row(
                    Modifier.fillMaxWidth().background(t.fondoInformacion)
                        .then(if (alAlternarBloque != null) Modifier.clickable { alAlternarBloque(e.indice) } else Modifier)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bloquesAceptados != null && alAlternarBloque != null) {
                        Checkbox(
                            checked = e.indice in bloquesAceptados,
                            onCheckedChange = { alAlternarBloque(e.indice) },
                            colors = CheckboxDefaults.colors(checkedColor = t.acento, checkmarkColor = t.textoSobreAcento),
                            modifier = Modifier.semantics { contentDescription = "Aceptar bloque ${e.indice + 1}" }
                        )
                    }
                    Text(e.bloque.encabezado, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FuenteMonoJetBrains), color = t.informacion)
                }
                is ElementoDiferencia.Linea -> {
                    val l = e.linea
                    val (fondo, marca, color) = when (l.tipo) {
                        TipoCambioLinea.AGREGADA -> Triple(t.fondoAgregado, "+", t.agregado)
                        TipoCambioLinea.ELIMINADA -> Triple(t.fondoEliminado, "-", t.eliminado)
                        TipoCambioLinea.IGUAL -> Triple(androidx.compose.ui.graphics.Color.Transparent, " ", t.textoTenue)
                    }
                    Row(Modifier.fillMaxWidth().background(fondo).semantics {
                        contentDescription = when (l.tipo) {
                            TipoCambioLinea.AGREGADA -> "Línea agregada: ${l.texto}"
                            TipoCambioLinea.ELIMINADA -> "Línea eliminada: ${l.texto}"
                            TipoCambioLinea.IGUAL -> "Línea sin cambios: ${l.texto}"
                        }
                    }, verticalAlignment = Alignment.Top) {
                        Text(l.lineaAnterior?.toString().orEmpty(), style = estiloNumero(), color = t.textoTenue, modifier = Modifier.width(36.dp).padding(end = 4.dp), maxLines = 1)
                        Text(l.lineaNueva?.toString().orEmpty(), style = estiloNumero(), color = t.textoTenue, modifier = Modifier.width(36.dp).padding(end = 4.dp), maxLines = 1)
                        Text(marca, style = estiloNumero(), color = color, modifier = Modifier.width(14.dp))
                        Box(Modifier.weight(1f).horizontalScroll(desplazamiento)) {
                            Text(l.texto.ifEmpty { " " }, style = estiloCodigo(), color = if (l.tipo == TipoCambioLinea.IGUAL) t.texto else color, softWrap = false, overflow = TextOverflow.Clip)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun estiloNumero() = MaterialTheme.typography.labelSmall.copy(fontFamily = FuenteMonoJetBrains, fontSize = 11.sp, lineHeight = 17.sp)

@Composable
private fun estiloCodigo() = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains, fontSize = 12.sp, lineHeight = 17.sp)

@Composable
fun ResumenDiferencia(agregadas: Int, eliminadas: Int) {
    val t = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("+$agregadas", style = MaterialTheme.typography.labelMedium, color = t.agregado)
        Text("−$eliminadas", style = MaterialTheme.typography.labelMedium, color = t.eliminado)
    }
}
