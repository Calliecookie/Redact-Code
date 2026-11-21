package pe.redactcode.nucleo.ide

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.validacion.AnalizadorJson
import pe.redactcode.nucleo.validacion.ErrorAnalisisJson
import pe.redactcode.nucleo.validacion.NodoJson

data class ResultadoFormato(val texto: String, val cambiado: Boolean, val metodo: String, val error: String? = null)

object Formateador {
    fun formatear(lenguaje: Lenguaje, texto: String, sangria: Int = 4): ResultadoFormato {
        val resultado = runCatching {
            when (lenguaje) {
                Lenguaje.JSON -> json(texto, sangria) to "Formato JSON (analizador RFC 8259)"
                Lenguaje.CSS -> css(texto, sangria) to "Formato CSS por llaves y declaraciones"
                Lenguaje.HTML -> html(texto, sangria) to "Formato HTML (jsoup)"
                else -> limpiarEspacios(texto, sangria) to "Limpieza de espacios finales, tabulaciones iniciales y salto final"
            }
        }
        return resultado.fold(
            { (nuevo, metodo) -> ResultadoFormato(nuevo, nuevo != texto, metodo) },
            { e ->
                val mensaje = if (e is ErrorAnalisisJson) "No se puede formatear: ${e.message}" else "No se puede formatear: ${e.message ?: "error desconocido"}"
                ResultadoFormato(texto, false, "", mensaje)
            }
        )
    }

    fun limpiarEspacios(texto: String, sangria: Int): String {
        val espacios = " ".repeat(sangria)
        val lineas = texto.replace("\r\n", "\n").split("\n").map { linea ->
            val inicio = linea.takeWhile { it == ' ' || it == '\t' }.replace("\t", espacios)
            (inicio + linea.drop(linea.takeWhile { it == ' ' || it == '\t' }.length)).trimEnd()
        }
        val sinFinales = lineas.dropLastWhile { it.isEmpty() }
        return if (sinFinales.isEmpty()) "" else sinFinales.joinToString("\n") + "\n"
    }

    fun json(texto: String, sangria: Int): String {
        val nodo = AnalizadorJson(texto).analizar()
        val sb = StringBuilder()
        escribirJson(nodo, sb, 0, " ".repeat(sangria))
        return sb.append('\n').toString()
    }

    private fun escaparJson(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private fun escribirJson(nodo: NodoJson, sb: StringBuilder, nivel: Int, unidad: String) {
        val interior = unidad.repeat(nivel + 1)
        when (nodo) {
            is NodoJson.Objeto -> if (nodo.miembros.isEmpty()) sb.append("{}") else {
                sb.append("{\n")
                nodo.miembros.forEachIndexed { i, (clave, valor) ->
                    sb.append(interior).append(escaparJson(clave)).append(": ")
                    escribirJson(valor, sb, nivel + 1, unidad)
                    if (i < nodo.miembros.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                sb.append(unidad.repeat(nivel)).append('}')
            }
            is NodoJson.Arreglo -> if (nodo.elementos.isEmpty()) sb.append("[]") else {
                sb.append("[\n")
                nodo.elementos.forEachIndexed { i, valor ->
                    sb.append(interior)
                    escribirJson(valor, sb, nivel + 1, unidad)
                    if (i < nodo.elementos.lastIndex) sb.append(',')
                    sb.append('\n')
                }
                sb.append(unidad.repeat(nivel)).append(']')
            }
            is NodoJson.Cadena -> sb.append(escaparJson(nodo.valor))
            is NodoJson.Numero -> sb.append(nodo.texto)
            is NodoJson.Logico -> sb.append(nodo.valor)
            is NodoJson.Nulo -> sb.append("null")
        }
    }

    fun css(texto: String, sangria: Int): String {
        val unidad = " ".repeat(sangria)
        val sb = StringBuilder()
        var nivel = 0
        var i = 0
        val actual = StringBuilder()
        fun vaciar(sufijo: String) {
            val contenido = actual.toString().trim().replace(Regex("\\s+"), " ")
            actual.clear()
            if (contenido.isEmpty() && sufijo.isEmpty()) return
            sb.append(unidad.repeat(nivel)).append(contenido).append(sufijo).append('\n')
        }
        while (i < texto.length) {
            val c = texto[i]
            when {
                c == '/' && texto.getOrNull(i + 1) == '*' -> {
                    vaciar("")
                    val fin = texto.indexOf("*/", i + 2).let { if (it < 0) texto.length else it + 2 }
                    sb.append(unidad.repeat(nivel)).append(texto.substring(i, fin).trim()).append('\n')
                    i = fin
                    continue
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < texto.length && texto[j] != c) { if (texto[j] == '\\') j++; j++ }
                    actual.append(texto, i, (j + 1).coerceAtMost(texto.length))
                    i = j + 1
                    continue
                }
                c == '{' -> { val sel = actual.toString().trim().replace(Regex("\\s+"), " "); actual.clear(); sb.append(unidad.repeat(nivel)).append(sel).append(" {\n"); nivel++ }
                c == ';' -> {
                    val decl = actual.toString().trim().replace(Regex("\\s+"), " ").replace(Regex("\\s*:\\s*"), ": ").replace(": //", "://")
                    actual.clear()
                    if (decl.isNotEmpty()) sb.append(unidad.repeat(nivel)).append(decl).append(";\n")
                }
                c == '}' -> {
                    val resto = actual.toString().trim()
                    actual.clear()
                    if (resto.isNotEmpty()) sb.append(unidad.repeat(nivel)).append(resto.replace(Regex("\\s*:\\s*"), ": ")).append(";\n")
                    nivel = (nivel - 1).coerceAtLeast(0)
                    sb.append(unidad.repeat(nivel)).append("}\n")
                    if (nivel == 0) sb.append('\n')
                }
                else -> actual.append(c)
            }
            i++
        }
        vaciar("")
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trimEnd() + "\n"
    }

    fun html(texto: String, sangria: Int): String {
        val completo = texto.contains("<html", ignoreCase = true) || texto.contains("<!doctype", ignoreCase = true)
        return if (completo) {
            val doc = Jsoup.parse(texto)
            doc.outputSettings(Document.OutputSettings().indentAmount(sangria).prettyPrint(true).outline(false))
            doc.outerHtml().trimEnd() + "\n"
        } else {
            val doc = Jsoup.parseBodyFragment(texto)
            doc.outputSettings(Document.OutputSettings().indentAmount(sangria).prettyPrint(true))
            doc.body().html().trimEnd() + "\n"
        }
    }
}
