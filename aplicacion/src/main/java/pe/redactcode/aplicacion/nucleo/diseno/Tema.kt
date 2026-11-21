package pe.redactcode.aplicacion.nucleo.diseno

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.redactcode.aplicacion.R

@Immutable
data class TokensColor(
    val esOscuro: Boolean,
    val fondoAplicacion: Color,
    val barraActividad: Color,
    val panelLateral: Color,
    val fondoEditor: Color,
    val panelInferior: Color,
    val barraTitulo: Color,
    val barraEstado: Color,
    val textoBarraEstado: Color,
    val superficie: Color,
    val superficieElevada: Color,
    val superficieAlta: Color,
    val superficieResaltada: Color,
    val borde: Color,
    val bordeSutil: Color,
    val bordeFoco: Color,
    val texto: Color,
    val textoSecundario: Color,
    val textoTenue: Color,
    val textoDeshabilitado: Color,
    val acento: Color,
    val textoSobreAcento: Color,
    val acentoSuave: Color,
    val acentoSecundario: Color,
    val exito: Color,
    val advertencia: Color,
    val error: Color,
    val informacion: Color,
    val fondoError: Color,
    val fondoAdvertencia: Color,
    val fondoExito: Color,
    val fondoInformacion: Color,
    val agregado: Color,
    val eliminado: Color,
    val fondoAgregado: Color,
    val fondoEliminado: Color,
    val modificado: Color,
    val seleccionLista: Color,
    val velo: Color
)

object TokensTema {
    val Oscuro = TokensColor(
        esOscuro = true,
        fondoAplicacion = Color(0xFF0F131A),
        barraActividad = Color(0xFF0A0D13),
        panelLateral = Color(0xFF131821),
        fondoEditor = Color(0xFF0F131A),
        panelInferior = Color(0xFF121720),
        barraTitulo = Color(0xFF0C1016),
        barraEstado = Color(0xFF0E2A4A),
        textoBarraEstado = Color(0xFFDCEBFF),
        superficie = Color(0xFF151B25),
        superficieElevada = Color(0xFF1A212D),
        superficieAlta = Color(0xFF222A38),
        superficieResaltada = Color(0xFF2A3445),
        borde = Color(0xFF2B3545),
        bordeSutil = Color(0xFF1D2430),
        bordeFoco = Color(0xFF3B9EFF),
        texto = Color(0xFFE6EDF3),
        textoSecundario = Color(0xFFB3BFCD),
        textoTenue = Color(0xFF94A1B3),
        textoDeshabilitado = Color(0xFF5A6575),
        acento = Color(0xFF4AA8FF),
        textoSobreAcento = Color(0xFF04111F),
        acentoSuave = Color(0xFF15304F),
        acentoSecundario = Color(0xFF45E0C0),
        exito = Color(0xFF4CC38A),
        advertencia = Color(0xFFE8B84A),
        error = Color(0xFFFF7B72),
        informacion = Color(0xFF79B8FF),
        fondoError = Color(0xFF3A1A1C),
        fondoAdvertencia = Color(0xFF362A10),
        fondoExito = Color(0xFF12301F),
        fondoInformacion = Color(0xFF112A45),
        agregado = Color(0xFF56D364),
        eliminado = Color(0xFFFF7B72),
        fondoAgregado = Color(0xFF12301F),
        fondoEliminado = Color(0xFF3A1A1C),
        modificado = Color(0xFFE8B84A),
        seleccionLista = Color(0xFF1C3350),
        velo = Color(0xB3000000)
    )

    val Claro = TokensColor(
        esOscuro = false,
        fondoAplicacion = Color(0xFFF6F8FA),
        barraActividad = Color(0xFFE9EDF2),
        panelLateral = Color(0xFFF0F3F7),
        fondoEditor = Color(0xFFFFFFFF),
        panelInferior = Color(0xFFF6F8FA),
        barraTitulo = Color(0xFFEDF1F5),
        barraEstado = Color(0xFF0A5BB5),
        textoBarraEstado = Color(0xFFFFFFFF),
        superficie = Color(0xFFFFFFFF),
        superficieElevada = Color(0xFFFFFFFF),
        superficieAlta = Color(0xFFEDF1F5),
        superficieResaltada = Color(0xFFE1E7EE),
        borde = Color(0xFFD0D7DE),
        bordeSutil = Color(0xFFE4E8ED),
        bordeFoco = Color(0xFF0A62C9),
        texto = Color(0xFF1F2328),
        textoSecundario = Color(0xFF424A53),
        textoTenue = Color(0xFF59636E),
        textoDeshabilitado = Color(0xFF9AA4AF),
        acento = Color(0xFF0A62C9),
        textoSobreAcento = Color(0xFFFFFFFF),
        acentoSuave = Color(0xFFDDEBFB),
        acentoSecundario = Color(0xFF0B7A6F),
        exito = Color(0xFF116329),
        advertencia = Color(0xFF8A5A00),
        error = Color(0xFFC62828),
        informacion = Color(0xFF0A62C9),
        fondoError = Color(0xFFFDECEC),
        fondoAdvertencia = Color(0xFFFFF4D6),
        fondoExito = Color(0xFFE6F6EA),
        fondoInformacion = Color(0xFFE5F0FD),
        agregado = Color(0xFF116329),
        eliminado = Color(0xFFC62828),
        fondoAgregado = Color(0xFFE6F6EA),
        fondoEliminado = Color(0xFFFDECEC),
        modificado = Color(0xFF8A5A00),
        seleccionLista = Color(0xFFDDEBFB),
        velo = Color(0x66000000)
    )

    fun altoContraste(t: TokensColor): TokensColor = if (t.esOscuro) t.copy(
        textoSecundario = Color(0xFFDDE5EE),
        textoTenue = Color(0xFFC0CBD8),
        borde = Color(0xFF5B6B80),
        bordeSutil = Color(0xFF3A4658),
        texto = Color(0xFFFFFFFF),
        acento = Color(0xFF7CC2FF)
    ) else t.copy(
        textoSecundario = Color(0xFF24292F),
        textoTenue = Color(0xFF3B434C),
        borde = Color(0xFF6E7781),
        bordeSutil = Color(0xFF9AA4AF),
        texto = Color(0xFF000000),
        acento = Color(0xFF0550AE)
    )
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
    fondo = TokensTema.Oscuro.fondoEditor,
    fondoNumeros = TokensTema.Oscuro.fondoEditor,
    numeroLinea = Color(0xFF8794A6),
    lineaActual = Color(0xFF161C26),
    texto = Color(0xFFE6EDF3),
    palabraClave = Color(0xFFD2A8FF),
    cadena = Color(0xFFA5D6A7),
    numero = Color(0xFFFFB86C),
    comentario = Color(0xFF94A1B3),
    funcion = Color(0xFF79C0FF),
    tipo = Color(0xFF45E0C0),
    operador = Color(0xFFC9D1D9),
    etiqueta = Color(0xFF7EE787),
    atributo = Color(0xFFE5C07B),
    seleccion = Color(0xFF233B5C),
    cursor = Color(0xFF45E0C0),
    error = TokensTema.Oscuro.error,
    advertencia = TokensTema.Oscuro.advertencia
)

val ColoresEditorClaro = ColoresEditor(
    fondo = TokensTema.Claro.fondoEditor,
    fondoNumeros = TokensTema.Claro.fondoEditor,
    numeroLinea = Color(0xFF626B75),
    lineaActual = Color(0xFFF3F6FA),
    texto = Color(0xFF1F2328),
    palabraClave = Color(0xFF8250DF),
    cadena = Color(0xFF116329),
    numero = Color(0xFF953800),
    comentario = Color(0xFF626B75),
    funcion = Color(0xFF0550AE),
    tipo = Color(0xFF0B6E66),
    operador = Color(0xFF24292F),
    etiqueta = Color(0xFF116329),
    atributo = Color(0xFF8A4600),
    seleccion = Color(0xFFCCE1FA),
    cursor = Color(0xFF0A62C9),
    error = TokensTema.Claro.error,
    advertencia = TokensTema.Claro.advertencia
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

@Immutable
data class Dimensiones(
    val objetivoTactil: Dp,
    val alturaFila: Dp,
    val anchoBarraActividad: Dp,
    val anchoPanelLateral: Dp,
    val margenPantalla: Dp
)

fun extendidosDesde(t: TokensColor) = ColoresExtendidos(t.exito, t.advertencia, t.error, t.informacion, t.borde, t.textoTenue, t.acento, t.esOscuro)

val LocalTokens = staticCompositionLocalOf { TokensTema.Oscuro }
val LocalColoresExtendidos = staticCompositionLocalOf { extendidosDesde(TokensTema.Oscuro) }
val LocalColoresEditor = staticCompositionLocalOf { ColoresEditorOscuro }
val LocalAnimacionesActivas = staticCompositionLocalOf { true }
val LocalDimensiones = staticCompositionLocalOf { Dimensiones(48.dp, 40.dp, 52.dp, 300.dp, 20.dp) }

val FuenteMonoJetBrains = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

fun esquemaMaterial(t: TokensColor): ColorScheme {
    val base = if (t.esOscuro) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = t.acento,
        onPrimary = t.textoSobreAcento,
        primaryContainer = t.acentoSuave,
        onPrimaryContainer = t.texto,
        secondary = t.acentoSecundario,
        onSecondary = t.textoSobreAcento,
        secondaryContainer = t.acentoSuave,
        onSecondaryContainer = t.texto,
        tertiary = t.informacion,
        onTertiary = t.textoSobreAcento,
        background = t.fondoAplicacion,
        onBackground = t.texto,
        surface = t.fondoAplicacion,
        onSurface = t.texto,
        surfaceVariant = t.superficie,
        onSurfaceVariant = t.textoSecundario,
        surfaceTint = t.acento,
        inverseSurface = t.texto,
        inverseOnSurface = t.fondoAplicacion,
        inversePrimary = t.acento,
        surfaceContainerLowest = t.fondoEditor,
        surfaceContainerLow = t.panelLateral,
        surfaceContainer = t.superficie,
        surfaceContainerHigh = t.superficieElevada,
        surfaceContainerHighest = t.superficieAlta,
        surfaceBright = t.superficieAlta,
        surfaceDim = t.barraActividad,
        outline = t.borde,
        outlineVariant = t.bordeSutil,
        error = t.error,
        onError = t.textoSobreAcento,
        errorContainer = t.fondoError,
        onErrorContainer = t.texto,
        scrim = Color.Black
    )
}

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
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

enum class ModoTema { OSCURO, CLARO, SISTEMA }

fun tokensPara(oscuro: Boolean, altoContraste: Boolean): TokensColor {
    val base = if (oscuro) TokensTema.Oscuro else TokensTema.Claro
    return if (altoContraste) TokensTema.altoContraste(base) else base
}

fun coloresEditorPara(oscuro: Boolean, altoContraste: Boolean): ColoresEditor {
    val base = if (oscuro) ColoresEditorOscuro else ColoresEditorClaro
    if (!altoContraste) return base
    val t = tokensPara(oscuro, true)
    return base.copy(texto = t.texto, comentario = t.textoTenue, numeroLinea = t.textoTenue, operador = t.texto)
}

@Composable
fun TemaRedactCode(
    modo: ModoTema = ModoTema.OSCURO,
    escalaTexto: Float = 1f,
    altoContraste: Boolean = false,
    animaciones: Boolean = true,
    objetivosGrandes: Boolean = false,
    contenido: @Composable () -> Unit
) {
    val oscuro = when (modo) {
        ModoTema.OSCURO -> true
        ModoTema.CLARO -> false
        ModoTema.SISTEMA -> isSystemInDarkTheme()
    }
    val tokens = tokensPara(oscuro, altoContraste)
    val dimensiones = if (objetivosGrandes) Dimensiones(56.dp, 48.dp, 60.dp, 320.dp, 20.dp) else Dimensiones(48.dp, 40.dp, 52.dp, 300.dp, 20.dp)
    CompositionLocalProvider(
        LocalTokens provides tokens,
        LocalColoresExtendidos provides extendidosDesde(tokens),
        LocalColoresEditor provides coloresEditorPara(oscuro, altoContraste),
        LocalAnimacionesActivas provides animaciones,
        LocalDimensiones provides dimensiones
    ) {
        MaterialTheme(colorScheme = esquemaMaterial(tokens), typography = tipografia(escalaTexto), shapes = formas, content = contenido)
    }
}

@Composable
fun anchoVentanaDp(): Int {
    val densidad = androidx.compose.ui.platform.LocalDensity.current
    val ancho = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    return with(densidad) { ancho.toDp().value.toInt() }
}
