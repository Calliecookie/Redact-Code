package pe.redactcode.aplicacion.funcionalidades.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import pe.redactcode.aplicacion.nucleo.diseno.TokensColor
import java.util.Properties

data class PaletaTerminal(val fondo: Color, val frente: Color, val cursor: Color, val ansi: List<Color>)

object PaletaAnsi {
    fun crear(t: TokensColor): PaletaTerminal {
        val oscuro = t.esOscuro
        val ansi = if (oscuro) listOf(
            Color(0xFF6E7A90), Color(0xFFFF8A80), Color(0xFF8FD694), Color(0xFFE5C07B),
            Color(0xFF82AAFF), Color(0xFFD7A6F2), Color(0xFF7FDBCA), Color(0xFFD8DEE9),
            Color(0xFFA0A9BA), Color(0xFFFFADA6), Color(0xFFB4EBB8), Color(0xFFF2D79B),
            Color(0xFFA9C4FF), Color(0xFFE6C4F7), Color(0xFFA6EBDF), Color(0xFFFFFFFF)
        ) else listOf(
            Color(0xFF1F2430), Color(0xFFB3261E), Color(0xFF1B6E2D), Color(0xFF7A5200),
            Color(0xFF1F4FB5), Color(0xFF7B2D9B), Color(0xFF00695C), Color(0xFF4A5060),
            Color(0xFF3F4656), Color(0xFF9C1C14), Color(0xFF14571F), Color(0xFF6B4500),
            Color(0xFF173F94), Color(0xFF65237F), Color(0xFF00544A), Color(0xFF111418)
        )
        return PaletaTerminal(t.panelInferior, t.texto, t.acento, ansi)
    }

    fun aPropiedades(p: PaletaTerminal): Properties = Properties().apply {
        fun hex(c: Color) = "#%06x".format(c.toArgb() and 0xFFFFFF)
        setProperty("background", hex(p.fondo))
        setProperty("foreground", hex(p.frente))
        setProperty("cursor", hex(p.cursor))
        p.ansi.forEachIndexed { i, c -> setProperty("color$i", hex(c)) }
    }
}
