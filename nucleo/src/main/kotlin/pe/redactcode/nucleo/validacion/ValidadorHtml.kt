package pe.redactcode.nucleo.validacion

import org.jsoup.nodes.Document
import org.jsoup.nodes.DocumentType
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.PosicionTexto
import pe.redactcode.nucleo.modelo.ResultadoValidacion
import pe.redactcode.nucleo.modelo.Severidad

object AnalizadorHtml {
    data class Resultado(val documento: Document, val errores: List<Pair<Int, String>>)

    fun analizar(codigo: String): Resultado {
        val parser = Parser.htmlParser().setTrackErrors(200).setTrackPosition(true)
        val documento = parser.parseInput(codigo, "")
        val errores = parser.errors.map { it.position to it.errorMessage }
        return Resultado(documento, errores)
    }
}

class ValidadorHtml : Validador {
    override val lenguaje = Lenguaje.HTML

    private val etiquetasVacias = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr", "param", "keygen"
    )
    private val cierreOpcional = setOf(
        "html", "head", "body", "p", "li", "dt", "dd", "option", "optgroup", "tr", "td", "th", "thead", "tbody", "tfoot",
        "colgroup", "rt", "rp", "caption"
    )
    private val obsoletas = setOf("center", "font", "marquee", "blink", "big", "strike", "tt", "frame", "frameset", "acronym", "applet", "basefont", "dir")

    fun validarSincrono(codigo: String): ResultadoValidacion {
        val inicio = System.nanoTime()
        val diagnosticos = mutableListOf<Diagnostico>()
        if (codigo.isBlank()) {
            return ResultadoValidacion(lenguaje, emptyList(), 0, "jsoup (HTML5)")
        }
        val resultado = AnalizadorHtml.analizar(codigo)
        resultado.errores.forEach { (desplazamiento, mensaje) ->
            val (codigoError, severidad) = ClasificadorMensajes.html(mensaje)
            val (l, c) = PosicionTexto.desdeDesplazamiento(codigo, desplazamiento)
            diagnosticos += CatalogoExplicaciones.diagnostico(lenguaje, l, c, codigoError, traducirMensaje(mensaje), severidad)
        }
        val lineasConError = diagnosticos.map { it.linea }.toSet()
        resultado.documento.allElements.forEach { elemento ->
            revisarCierre(elemento, codigo, lineasConError)?.let { diagnosticos += it }
            if (elemento.normalName() in obsoletas && elemento.sourceRange().isTracked) {
                val (l, c) = PosicionTexto.desdeDesplazamiento(codigo, elemento.sourceRange().startPos())
                diagnosticos += CatalogoExplicaciones.diagnostico(lenguaje, l, c, "HTML009", "Elemento obsoleto <${elemento.tagName()}>", Severidad.ADVERTENCIA, elemento.tagName().length + 1)
            }
        }
        val esDocumentoCompleto = codigo.contains("<html", ignoreCase = true) || codigo.contains("<head", ignoreCase = true)
        val tieneDoctype = resultado.documento.childNodes().any { it is DocumentType }
        if (esDocumentoCompleto && !tieneDoctype) {
            diagnosticos += CatalogoExplicaciones.diagnostico(lenguaje, 1, 1, "HTML006", "Falta <!DOCTYPE html>", Severidad.ADVERTENCIA)
        }
        val ordenados = diagnosticos.distinctBy { Triple(it.linea, it.columna, it.codigo) }.sortedWith(compareBy({ it.linea }, { it.columna }))
        return ResultadoValidacion(lenguaje, ordenados, (System.nanoTime() - inicio) / 1_000_000, "jsoup (HTML5)")
    }

    private fun revisarCierre(elemento: Element, codigo: String, lineasConError: Set<Int>): Diagnostico? {
        if (elemento is Document) return null
        val nombre = elemento.normalName()
        if (nombre in etiquetasVacias || nombre in cierreOpcional) return null
        val rangoInicio = elemento.sourceRange()
        if (!rangoInicio.isTracked || rangoInicio.isImplicit) return null
        val rangoFin = elemento.endSourceRange()
        if (rangoFin.isTracked && !rangoFin.isImplicit) return null
        val (l, c) = PosicionTexto.desdeDesplazamiento(codigo, rangoInicio.startPos())
        if (l in lineasConError) return null
        return CatalogoExplicaciones.diagnostico(lenguaje, l, c, "HTML005", "El elemento <${elemento.tagName()}> no tiene etiqueta de cierre", Severidad.ADVERTENCIA, nombre.length + 1)
    }

    private fun traducirMensaje(mensaje: String): String = mensaje
        .replace("Unexpected EndTag token", "Etiqueta de cierre inesperada")
        .replace("Unexpected StartTag token", "Etiqueta de apertura inesperada")
        .replace("when in state", "en el estado")
        .replace("Unexpectedly reached end of file (EOF) in input state", "Fin de archivo inesperado en el estado")
        .replace("Invalid character reference", "Referencia de carácter no válida")
        .replace("Unexpected character", "Carácter inesperado")
        .replace("Dropped duplicate attribute(s) in tag", "Se descartó un atributo duplicado en la etiqueta")

    override suspend fun validar(codigo: String): ResultadoValidacion = validarSincrono(codigo)
}
