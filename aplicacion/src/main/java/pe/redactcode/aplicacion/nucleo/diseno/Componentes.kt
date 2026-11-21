package pe.redactcode.aplicacion.nucleo.diseno

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.redactcode.nucleo.modelo.Severidad

@Composable
fun EncabezadoPantalla(
    titulo: String,
    subtitulo: String? = null,
    alVolver: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (alVolver != null) 4.dp else 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (alVolver != null) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (subtitulo != null) {
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        acciones()
    }
}

@Composable
fun Superficie(
    modifier: Modifier = Modifier,
    alPulsar: (() -> Unit)? = null,
    relleno: PaddingValues = PaddingValues(16.dp),
    contenido: @Composable ColumnScope.() -> Unit
) {
    val forma = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(forma)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma)
            .then(if (alPulsar != null) Modifier.clickable(role = Role.Button, onClick = alPulsar) else Modifier)
            .padding(relleno),
        content = contenido
    )
}

@Composable
fun TituloSeccion(titulo: String, modifier: Modifier = Modifier, accion: String? = null, alPulsarAccion: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            titulo.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
            color = LocalColoresExtendidos.current.textoTenue,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (accion != null && alPulsarAccion != null) {
            Text(accion, style = MaterialTheme.typography.labelLarge, color = LocalColoresExtendidos.current.acento,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = alPulsarAccion).padding(horizontal = 6.dp, vertical = 4.dp))
        }
    }
}

@Composable
fun BotonPrincipal(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, icono: ImageVector? = null) {
    Button(
        onClick = alPulsar,
        enabled = habilitado,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BotonSecundario(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, icono: ImageVector? = null) {
    OutlinedButton(
        onClick = alPulsar,
        enabled = habilitado,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    unaLinea: Boolean = true,
    error: String? = null,
    monoespaciado: Boolean = false,
    opcionesTeclado: KeyboardOptions = KeyboardOptions.Default,
    lineasMinimas: Int = 1,
    transformacion: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        modifier = modifier.fillMaxWidth(),
        singleLine = unaLinea,
        minLines = lineasMinimas,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = opcionesTeclado,
        visualTransformation = transformacion,
        textStyle = if (monoespaciado) MaterialTheme.typography.bodyMedium.copy(fontFamily = FuenteMonoJetBrains) else MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LocalColoresExtendidos.current.acento,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = LocalColoresExtendidos.current.acento
        )
    )
}

@Composable
fun EstadoVacio(icono: ImageVector, titulo: String, descripcion: String, modifier: Modifier = Modifier, accion: String? = null, alPulsarAccion: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icono, contentDescription = null, tint = LocalColoresExtendidos.current.textoTenue)
        }
        Spacer(Modifier.height(16.dp))
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(descripcion, style = MaterialTheme.typography.bodyMedium, color = LocalColoresExtendidos.current.textoTenue, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (accion != null && alPulsarAccion != null) {
            Spacer(Modifier.height(20.dp))
            BotonSecundario(accion, alPulsarAccion)
        }
    }
}

@Composable
fun Etiqueta(texto: String, modifier: Modifier = Modifier, color: Color = LocalColoresExtendidos.current.textoTenue, seleccionada: Boolean = false, alPulsar: (() -> Unit)? = null) {
    val forma = RoundedCornerShape(8.dp)
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        color = if (seleccionada) MaterialTheme.colorScheme.onPrimary else color,
        maxLines = 1,
        modifier = modifier
            .clip(forma)
            .background(if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (alPulsar != null) Modifier.clickable(onClick = alPulsar) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
fun colorSeveridad(severidad: Severidad): Color = when (severidad) {
    Severidad.ERROR -> LocalColoresExtendidos.current.error
    Severidad.ADVERTENCIA -> LocalColoresExtendidos.current.advertencia
    Severidad.INFORMACION -> LocalColoresExtendidos.current.informacion
}

@Composable
fun PuntoEstado(color: Color, tamano: Dp = 8.dp) {
    Box(Modifier.size(tamano).clip(CircleShape).background(color))
}

@Composable
fun BarraProgreso(valor: Float, modifier: Modifier = Modifier, color: Color = LocalColoresExtendidos.current.acento) {
    LinearProgressIndicator(
        progress = { valor.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {}
    )
}

@Composable
fun BloqueCodigo(codigo: String, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(10.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .background(LocalColoresEditor.current.fondo)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma)
            .horizontalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(codigo, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains, fontSize = 12.5.sp, lineHeight = 18.sp), color = LocalColoresEditor.current.texto, softWrap = false)
    }
}

@Composable
fun FilaAjuste(titulo: String, descripcion: String? = null, icono: ImageVector? = null, alPulsar: (() -> Unit)? = null, final: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (alPulsar != null) Modifier.clickable(onClick = alPulsar) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, tint = LocalColoresExtendidos.current.textoTenue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            if (descripcion != null) Text(descripcion, style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
        }
        final()
    }
}

@Composable
fun FilaInterruptor(titulo: String, descripcion: String?, activo: Boolean, alCambiar: (Boolean) -> Unit, icono: ImageVector? = null, habilitado: Boolean = true) {
    FilaAjuste(titulo, descripcion, icono, alPulsar = if (habilitado) ({ alCambiar(!activo) }) else null) {
        Switch(
            checked = activo,
            onCheckedChange = alCambiar,
            enabled = habilitado,
            colors = SwitchDefaults.colors(checkedTrackColor = LocalColoresExtendidos.current.acento, checkedThumbColor = MaterialTheme.colorScheme.surface)
        )
    }
}

@Composable
fun Separador(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun DialogoEntradaTexto(
    titulo: String,
    etiqueta: String,
    valorInicial: String = "",
    textoConfirmar: String = "Aceptar",
    validar: (String) -> String? = { null },
    alConfirmar: (String) -> Unit,
    alCerrar: () -> Unit
) {
    var valor by remember { mutableStateOf(valorInicial) }
    val error = if (valor.isEmpty()) null else validar(valor)
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = { CampoTexto(valor, { valor = it }, etiqueta, error = error) },
        confirmButton = {
            TextButton(onClick = { alConfirmar(valor.trim()) }, enabled = valor.isNotBlank() && error == null) { Text(textoConfirmar) }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
fun DialogoConfirmacion(titulo: String, mensaje: String, textoConfirmar: String, destructivo: Boolean = false, alConfirmar: () -> Unit, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = { Text(mensaje) },
        confirmButton = {
            TextButton(onClick = alConfirmar) {
                Text(textoConfirmar, color = if (destructivo) LocalColoresExtendidos.current.error else LocalColoresExtendidos.current.acento, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
fun Cargando(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = LocalColoresExtendidos.current.acento, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun Metrica(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(valor, style = MaterialTheme.typography.headlineSmall)
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
    }
}

@Composable
fun FilaEspaciada(modifier: Modifier = Modifier, contenido: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = contenido)
}
