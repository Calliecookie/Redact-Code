package pe.redactcode.aplicacion.nucleo.diseno

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.redactcode.aplicacion.R

object PaletaRedact {
    val NegroProfundo = Color(0xFF0D0D0F)
    val NegroSecundario = Color(0xFF151518)
    val GrisOscuro = Color(0xFF202024)
    val GrisBorde = Color(0xFF2A2A2F)
    val GrisIntermedio = Color(0xFF77777F)
    val Blanco = Color(0xFFF5F5F7)
    val BlancoSecundario = Color(0xFFB8B8BE)
    val AcentoCian = Color(0xFF3FD8C4)
    val AcentoAzul = Color(0xFF2E9BFF)
    val Exito = Color(0xFF4CC38A)
    val Advertencia = Color(0xFFE5B454)
    val Error = Color(0xFFF2676A)
    val Informacion = Color(0xFF7AA7FF)
    val ClaroFondo = Color(0xFFF7F7F8)
    val ClaroSuperficie = Color(0xFFFFFFFF)
    val ClaroSuperficieAlta = Color(0xFFEFEFF1)
    val ClaroBorde = Color(0xFFE1E1E5)
    val ClaroTexto = Color(0xFF111114)
    val ClaroTextoSecundario = Color(0xFF5C5C64)
    val ClaroAcento = Color(0xFF0B7A6F)
}

@Immutable
data class ColoresEditor(
    val fondo: Color,
    val fondoNumeros: Color,
    val numeroLinea: Color,
    val lineaActual: Color,
    val texto: Color,
    val palabraClave: Color,
    val cadena: Color,
    val numero: Color,
    val comentario: Color,
    val funcion: Color,
    val tipo: Color,
    val operador: Color,
    val etiqueta: Color,
    val atributo: Color,
    val seleccion: Color,
    val cursor: Color,
    val error: Color,
    val advertencia: Color
)

val ColoresEditorOscuro = ColoresEditor(
    fondo = PaletaRedact.NegroProfundo,
    fondoNumeros = PaletaRedact.NegroProfundo,
    numeroLinea = Color(0xFF4A4A52),
    lineaActual = Color(0xFF16161A),
    texto = Color(0xFFE6E6EA),
    palabraClave = Color(0xFFC792EA),
    cadena = Color(0xFF9CD59A),
    numero = Color(0xFFF2A65A),
    comentario = Color(0xFF6A6A73),
    funcion = Color(0xFF6CB6FF),
    tipo = Color(0xFF3FD8C4),
    operador = Color(0xFFB8B8BE),
    etiqueta = Color(0xFF6CB6FF),
    atributo = Color(0xFFE5C07B),
    seleccion = Color(0xFF26364D),
    cursor = PaletaRedact.AcentoCian,
    error = PaletaRedact.Error,
    advertencia = PaletaRedact.Advertencia
)

val ColoresEditorClaro = ColoresEditor(
    fondo = Color(0xFFFFFFFF),
    fondoNumeros = Color(0xFFFFFFFF),
    numeroLinea = Color(0xFFB0B0B8),
    lineaActual = Color(0xFFF4F4F6),
    texto = Color(0xFF1B1B20),
    palabraClave = Color(0xFF8A3FB8),
    cadena = Color(0xFF2E7D32),
    numero = Color(0xFFB45309),
    comentario = Color(0xFF8C8C94),
    funcion = Color(0xFF1D5FBF),
    tipo = Color(0xFF0B7A6F),
    operador = Color(0xFF55555D),
    etiqueta = Color(0xFF1D5FBF),
    atributo = Color(0xFF9A6700),
    seleccion = Color(0xFFCFE3FF),
    cursor = PaletaRedact.ClaroAcento,
    error = Color(0xFFD32F2F),
    advertencia = Color(0xFFB7791F)
)

@Immutable
data class ColoresExtendidos(
    val exito: Color,
    val advertencia: Color,
    val error: Color,
    val informacion: Color,
    val borde: Color,
    val textoTenue: Color,
    val acento: Color,
    val esOscuro: Boolean
)

val LocalColoresExtendidos = staticCompositionLocalOf {
    ColoresExtendidos(PaletaRedact.Exito, PaletaRedact.Advertencia, PaletaRedact.Error, PaletaRedact.Informacion, PaletaRedact.GrisBorde, PaletaRedact.GrisIntermedio, PaletaRedact.AcentoCian, true)
}
val LocalColoresEditor = staticCompositionLocalOf { ColoresEditorOscuro }
val LocalAnimacionesActivas = staticCompositionLocalOf { true }

val FuenteMonoJetBrains = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

private val esquemaOscuro: ColorScheme = darkColorScheme(
    primary = PaletaRedact.Blanco,
    onPrimary = PaletaRedact.NegroProfundo,
    primaryContainer = PaletaRedact.GrisOscuro,
    onPrimaryContainer = PaletaRedact.Blanco,
    secondary = PaletaRedact.AcentoCian,
    onSecondary = PaletaRedact.NegroProfundo,
    secondaryContainer = Color(0xFF14302C),
    onSecondaryContainer = PaletaRedact.AcentoCian,
    tertiary = PaletaRedact.AcentoAzul,
    background = PaletaRedact.NegroProfundo,
    onBackground = PaletaRedact.Blanco,
    surface = PaletaRedact.NegroProfundo,
    onSurface = PaletaRedact.Blanco,
    surfaceVariant = PaletaRedact.NegroSecundario,
    onSurfaceVariant = PaletaRedact.BlancoSecundario,
    surfaceContainerLowest = PaletaRedact.NegroProfundo,
    surfaceContainerLow = PaletaRedact.NegroSecundario,
    surfaceContainer = PaletaRedact.NegroSecundario,
    surfaceContainerHigh = PaletaRedact.GrisOscuro,
    surfaceContainerHighest = Color(0xFF26262B),
    outline = PaletaRedact.GrisBorde,
    outlineVariant = Color(0xFF1E1E22),
    error = PaletaRedact.Error,
    onError = PaletaRedact.NegroProfundo
)

private val esquemaClaro: ColorScheme = lightColorScheme(
    primary = PaletaRedact.ClaroTexto,
    onPrimary = Color.White,
    primaryContainer = PaletaRedact.ClaroSuperficieAlta,
    onPrimaryContainer = PaletaRedact.ClaroTexto,
    secondary = PaletaRedact.ClaroAcento,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F2EE),
    onSecondaryContainer = PaletaRedact.ClaroAcento,
    tertiary = Color(0xFF1D5FBF),
    background = PaletaRedact.ClaroFondo,
    onBackground = PaletaRedact.ClaroTexto,
    surface = PaletaRedact.ClaroFondo,
    onSurface = PaletaRedact.ClaroTexto,
    surfaceVariant = PaletaRedact.ClaroSuperficie,
    onSurfaceVariant = PaletaRedact.ClaroTextoSecundario,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaletaRedact.ClaroSuperficie,
    surfaceContainer = PaletaRedact.ClaroSuperficie,
    surfaceContainerHigh = PaletaRedact.ClaroSuperficieAlta,
    surfaceContainerHighest = Color(0xFFE6E6EA),
    outline = PaletaRedact.ClaroBorde,
    outlineVariant = Color(0xFFEDEDF0),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

private fun tipografia(escala: Float): Typography {
    val base = FontFamily.Default
    fun estilo(tamano: Int, peso: FontWeight, alto: Int, espaciado: Double = 0.0) =
        TextStyle(fontFamily = base, fontWeight = peso, fontSize = (tamano * escala).sp, lineHeight = (alto * escala).sp, letterSpacing = espaciado.sp)
    return Typography(
        displaySmall = estilo(32, FontWeight.SemiBold, 40, -0.5),
        headlineLarge = estilo(28, FontWeight.SemiBold, 34, -0.4),
        headlineMedium = estilo(24, FontWeight.SemiBold, 30, -0.3),
        headlineSmall = estilo(20, FontWeight.SemiBold, 26, -0.2),
        titleLarge = estilo(18, FontWeight.SemiBold, 24, -0.1),
        titleMedium = estilo(16, FontWeight.Medium, 22),
        titleSmall = estilo(14, FontWeight.Medium, 20),
        bodyLarge = estilo(16, FontWeight.Normal, 24),
        bodyMedium = estilo(14, FontWeight.Normal, 20),
        bodySmall = estilo(12, FontWeight.Normal, 16),
        labelLarge = estilo(14, FontWeight.Medium, 20, 0.1),
        labelMedium = estilo(12, FontWeight.Medium, 16, 0.2),
        labelSmall = estilo(11, FontWeight.Medium, 14, 0.3)
    )
}

private val formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

enum class ModoTema { OSCURO, CLARO, SISTEMA }

@Composable
fun TemaRedactCode(
    modo: ModoTema = ModoTema.OSCURO,
    escalaTexto: Float = 1f,
    altoContraste: Boolean = false,
    animaciones: Boolean = true,
    contenido: @Composable () -> Unit
) {
    val oscuro = when (modo) {
        ModoTema.OSCURO -> true
        ModoTema.CLARO -> false
        ModoTema.SISTEMA -> isSystemInDarkTheme()
    }
    var esquema = if (oscuro) esquemaOscuro else esquemaClaro
    if (altoContraste) {
        esquema = if (oscuro) esquema.copy(onSurfaceVariant = PaletaRedact.Blanco, outline = PaletaRedact.GrisIntermedio)
        else esquema.copy(onSurfaceVariant = PaletaRedact.ClaroTexto, outline = PaletaRedact.ClaroTextoSecundario)
    }
    val extendidos = if (oscuro) {
        ColoresExtendidos(PaletaRedact.Exito, PaletaRedact.Advertencia, PaletaRedact.Error, PaletaRedact.Informacion,
            esquema.outline, if (altoContraste) PaletaRedact.BlancoSecundario else PaletaRedact.GrisIntermedio, PaletaRedact.AcentoCian, true)
    } else {
        ColoresExtendidos(Color(0xFF1E8E5A), Color(0xFFB7791F), Color(0xFFD32F2F), Color(0xFF1D5FBF),
            esquema.outline, if (altoContraste) PaletaRedact.ClaroTextoSecundario else Color(0xFF8C8C94), PaletaRedact.ClaroAcento, false)
    }
    CompositionLocalProvider(
        LocalColoresExtendidos provides extendidos,
        LocalColoresEditor provides if (oscuro) ColoresEditorOscuro else ColoresEditorClaro,
        LocalAnimacionesActivas provides animaciones
    ) {
        MaterialTheme(colorScheme = esquema, typography = tipografia(escalaTexto), shapes = formas, content = contenido)
    }
}
