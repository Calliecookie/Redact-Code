package pe.redactcode.aplicacion

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.redactcode.aplicacion.nucleo.diseno.ColoresEditor
import pe.redactcode.aplicacion.nucleo.diseno.TokensColor
import pe.redactcode.aplicacion.nucleo.diseno.coloresEditorPara
import pe.redactcode.aplicacion.nucleo.diseno.tokensPara

class TemaContrasteTest {
    private fun contraste(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private val variantes = listOf(true to false, false to false, true to true, false to true)

    private fun fondos(t: TokensColor) = mapOf(
        "fondoAplicacion" to t.fondoAplicacion, "panelLateral" to t.panelLateral, "fondoEditor" to t.fondoEditor,
        "panelInferior" to t.panelInferior, "barraTitulo" to t.barraTitulo, "barraActividad" to t.barraActividad,
        "superficie" to t.superficie, "superficieElevada" to t.superficieElevada, "superficieAlta" to t.superficieAlta,
        "seleccionLista" to t.seleccionLista
    )

    private fun verificar(fallos: MutableList<String>, nombre: String, frente: Color, fondo: Color, minimo: Double) {
        val c = contraste(frente, fondo)
        if (c < minimo) fallos += "$nombre: ${"%.2f".format(c)} < $minimo"
    }

    @Test
    fun textosCumplenAA() {
        val fallos = mutableListOf<String>()
        variantes.forEach { (oscuro, alto) ->
            val t = tokensPara(oscuro, alto)
            val prefijo = "${if (oscuro) "oscuro" else "claro"}${if (alto) "+alto" else ""}"
            fondos(t).forEach { (nombreFondo, fondo) ->
                listOf("texto" to t.texto, "textoSecundario" to t.textoSecundario, "textoTenue" to t.textoTenue, "acento" to t.acento,
                    "exito" to t.exito, "advertencia" to t.advertencia, "error" to t.error, "informacion" to t.informacion,
                    "agregado" to t.agregado, "eliminado" to t.eliminado, "modificado" to t.modificado
                ).forEach { (nombre, color) -> verificar(fallos, "$prefijo $nombre/$nombreFondo", color, fondo, 4.5) }
                verificar(fallos, "$prefijo borde/$nombreFondo", t.bordeFoco, fondo, 3.0)
            }
            verificar(fallos, "$prefijo textoSobreAcento", t.textoSobreAcento, t.acento, 4.5)
            verificar(fallos, "$prefijo barraEstado", t.textoBarraEstado, t.barraEstado, 4.5)
            verificar(fallos, "$prefijo error/fondoError", t.error, t.fondoError, 4.5)
            verificar(fallos, "$prefijo advertencia/fondoAdvertencia", t.advertencia, t.fondoAdvertencia, 4.5)
            verificar(fallos, "$prefijo exito/fondoExito", t.exito, t.fondoExito, 4.5)
            verificar(fallos, "$prefijo informacion/fondoInformacion", t.informacion, t.fondoInformacion, 4.5)
            verificar(fallos, "$prefijo agregado/fondoAgregado", t.agregado, t.fondoAgregado, 4.5)
            verificar(fallos, "$prefijo eliminado/fondoEliminado", t.eliminado, t.fondoEliminado, 4.5)
            verificar(fallos, "$prefijo texto/fondoAgregado", t.texto, t.fondoAgregado, 4.5)
            verificar(fallos, "$prefijo texto/fondoEliminado", t.texto, t.fondoEliminado, 4.5)
        }
        assertTrue(fallos.joinToString("\n"), fallos.isEmpty())
    }

    private fun sintaxis(c: ColoresEditor) = mapOf(
        "texto" to c.texto, "palabraClave" to c.palabraClave, "cadena" to c.cadena, "numero" to c.numero, "comentario" to c.comentario,
        "funcion" to c.funcion, "tipo" to c.tipo, "operador" to c.operador, "etiqueta" to c.etiqueta, "atributo" to c.atributo,
        "numeroLinea" to c.numeroLinea, "error" to c.error, "advertencia" to c.advertencia
    )

    @Test
    fun sintaxisCumpleAASobreFondoLineaYSeleccion() {
        val fallos = mutableListOf<String>()
        variantes.forEach { (oscuro, alto) ->
            val c = coloresEditorPara(oscuro, alto)
            val prefijo = "${if (oscuro) "oscuro" else "claro"}${if (alto) "+alto" else ""}"
            sintaxis(c).forEach { (nombre, color) ->
                verificar(fallos, "$prefijo $nombre/fondo", color, c.fondo, 4.5)
                verificar(fallos, "$prefijo $nombre/lineaActual", color, c.lineaActual, 4.5)
                verificar(fallos, "$prefijo $nombre/seleccion", color, c.seleccion, 3.0)
            }
            verificar(fallos, "$prefijo cursor", c.cursor, c.fondo, 3.0)
        }
        assertTrue(fallos.joinToString("\n"), fallos.isEmpty())
    }

    @Test
    fun coloresDeSintaxisSeDistinguenEntreSi() {
        variantes.forEach { (oscuro, alto) ->
            val c = coloresEditorPara(oscuro, alto)
            val principales = listOf(c.palabraClave, c.cadena, c.numero, c.funcion, c.tipo, c.atributo)
            assertTrue(principales.toSet().size == principales.size)
        }
    }
}
