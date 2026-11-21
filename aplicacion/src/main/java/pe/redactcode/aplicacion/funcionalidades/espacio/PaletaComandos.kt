package pe.redactcode.aplicacion.funcionalidades.espacio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pe.redactcode.aplicacion.nucleo.diseno.CampoTexto
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.FuenteMonoJetBrains
import pe.redactcode.aplicacion.nucleo.diseno.LocalTokens
import pe.redactcode.nucleo.ide.CoincidenciaDifusa
import pe.redactcode.nucleo.ide.SimboloCodigo

data class ComandoIde(
    val id: String,
    val titulo: String,
    val categoria: String,
    val icono: ImageVector? = null,
    val enMenu: Boolean = false,
    val habilitado: Boolean = true,
    val ejecutar: () -> Unit
)

enum class ModoPaleta(val prefijo: String, val etiqueta: String, val sugerencia: String) {
    ARCHIVOS("", "Archivos", "Buscar archivo por nombre"),
    COMANDOS(">", "Comandos", "Escribe un comando"),
    SIMBOLOS("@", "Símbolos", "Ir a un símbolo del archivo"),
    LINEA(":", "Línea", "Número de línea")
}

private sealed interface Elemento {
    val titulo: String
    val detalle: String
    data class Archivo(val ruta: String, val posiciones: List<Int>) : Elemento { override val titulo get() = ruta.substringAfterLast('/'); override val detalle get() = ruta.substringBeforeLast('/', "") }
    data class Comando(val comando: ComandoIde, val posiciones: List<Int>) : Elemento { override val titulo get() = comando.titulo; override val detalle get() = comando.categoria }
    data class Simbolo(val simbolo: SimboloCodigo) : Elemento { override val titulo get() = simbolo.nombre; override val detalle get() = "${simbolo.tipo.nombreVisible} · línea ${simbolo.linea}" }
}

@Composable
fun PaletaComandos(
    modoInicial: ModoPaleta,
    archivos: List<String>,
    comandos: List<ComandoIde>,
    simbolos: () -> List<SimboloCodigo>,
    alAbrirArchivo: (String) -> Unit,
    alIrALinea: (Int) -> Unit,
    alIrASimbolo: (SimboloCodigo) -> Unit,
    alCerrar: () -> Unit
) {
    val t = LocalTokens.current
    var modo by remember { mutableStateOf(modoInicial) }
    var consulta by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    val listaSimbolos = remember { simbolos() }
    val elementos: List<Elemento> = remember(modo, consulta, archivos, comandos, listaSimbolos) {
        when (modo) {
            ModoPaleta.ARCHIVOS -> CoincidenciaDifusa.filtrar(consulta, archivos, { it }, 80).map { Elemento.Archivo(it.elemento, it.posiciones) }
            ModoPaleta.COMANDOS -> CoincidenciaDifusa.filtrar(consulta, comandos.filter { it.habilitado }, { "${it.categoria}: ${it.titulo}" }, 80).map { Elemento.Comando(it.elemento, emptyList()) }
            ModoPaleta.SIMBOLOS -> CoincidenciaDifusa.filtrar(consulta, listaSimbolos, { it.nombre }, 120).map { Elemento.Simbolo(it.elemento) }
            ModoPaleta.LINEA -> emptyList()
        }
    }
    fun cambiarEntrada(nuevo: String) {
        val m = ModoPaleta.entries.firstOrNull { it.prefijo.isNotEmpty() && nuevo.startsWith(it.prefijo) }
        if (m != null && m != modo) { modo = m; consulta = nuevo.removePrefix(m.prefijo) } else consulta = nuevo
    }
    fun elegir(e: Elemento) {
        alCerrar()
        when (e) {
            is Elemento.Archivo -> alAbrirArchivo(e.ruta)
            is Elemento.Comando -> e.comando.ejecutar()
            is Elemento.Simbolo -> alIrASimbolo(e.simbolo)
        }
    }
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = alCerrar).statusBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).clip(RoundedCornerShape(14.dp)).background(t.superficieElevada).clickable(enabled = false) {}.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModoPaleta.entries.forEach { m -> Etiqueta(m.etiqueta, seleccionada = m == modo, alPulsar = { modo = m; consulta = "" }) }
                }
                CampoTexto(
                    valor = consulta, alCambiar = ::cambiarEntrada, etiqueta = modo.sugerencia, modifier = Modifier.focusRequester(foco),
                    opcionesTeclado = if (modo == ModoPaleta.LINEA) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
                )
                if (modo == ModoPaleta.LINEA) {
                    val n = consulta.toIntOrNull()
                    Text(if (n == null) "Escribe un número de línea" else "Ir a la línea $n", style = MaterialTheme.typography.bodyMedium, color = if (n == null) t.textoTenue else t.acento,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = n != null) { alCerrar(); n?.let(alIrALinea) }.padding(12.dp))
                } else if (elementos.isEmpty()) {
                    Text(if (modo == ModoPaleta.SIMBOLOS && listaSimbolos.isEmpty()) "Este archivo no tiene símbolos reconocidos" else "Sin resultados", style = MaterialTheme.typography.bodyMedium, color = t.textoTenue, modifier = Modifier.padding(12.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(elementos, key = { it.hashCode().toString() + it.titulo + it.detalle }) { e ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { elegir(e) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.titulo, style = MaterialTheme.typography.bodyLarge.let { if (e is Elemento.Simbolo) it.copy(fontFamily = FuenteMonoJetBrains) else it }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (e.detalle.isNotEmpty()) Text(e.detalle, style = MaterialTheme.typography.labelSmall, color = t.textoTenue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
