package pe.redactcode.aplicacion.funcionalidades.editor

import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.widget.CodeEditor
import pe.redactcode.nucleo.extensiones.FragmentoExtension
import pe.redactcode.nucleo.extensiones.LectorExtensiones

import android.os.Bundle
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import pe.redactcode.nucleo.modelo.Lenguaje

class AnalizadorResaltado(private val lenguaje: Lenguaje) : SimpleAnalyzeManager<Unit>() {
    override fun analyze(texto: StringBuilder, delegado: Delegate<Unit>): Styles {
        val constructor = MappedSpans.Builder(1024)
        val tramos = LexerResaltado(lenguaje).analizar(texto) { delegado.isCancelled }
        var ultimaLinea = 0
        tramos.forEach { t ->
            constructor.addIfNeeded(t.linea, t.columna, TextStyle.makeStyle(t.color))
            ultimaLinea = t.linea
        }
        val lineas = texto.count { it == '\n' }
        if (ultimaLinea < lineas) constructor.addIfNeeded(lineas, 0, TextStyle.makeStyle(IdColores.NORMAL))
        constructor.determine(lineas)
        return Styles(constructor.build())
    }
}

class ElementoFragmento(
    private val fragmento: FragmentoExtension,
    longitudPrefijo: Int,
    private val nombreArchivo: String
) : CompletionItem(fragmento.prefijo, fragmento.descripcion.ifBlank { fragmento.nombre }) {
    init {
        prefixLength = longitudPrefijo
        kind = CompletionItemKind.Snippet
    }

    override fun performCompletion(editor: CodeEditor, texto: Content, linea: Int, columna: Int) {
        val expandido = LectorExtensiones.expandir(fragmento.cuerpo, nombreArchivo)
        val sangria = texto.getLineString(linea).takeWhile { it == ' ' || it == '\t' }
        val insertar = expandido.texto.replace("\n", "\n" + sangria)
        val saltosAntes = expandido.texto.substring(0, expandido.cursor.coerceIn(0, expandido.texto.length)).count { it == '\n' }
        val desde = (columna - prefixLength).coerceAtLeast(0)
        texto.delete(linea, desde, linea, columna)
        val inicio = texto.getCharIndex(linea, desde)
        texto.insert(linea, desde, insertar)
        val destino = texto.indexer.getCharPosition((inicio + expandido.cursor + saltosAntes * sangria.length).coerceAtMost(texto.length))
        editor.setSelection(destino.line, destino.column)
    }
}

class LenguajeEditor(
    private val lenguaje: Lenguaje,
    private val tamanoSangria: Int,
    private val autocierre: Boolean,
    private val autocompletadoActivo: Boolean = true,
    private val fragmentos: List<FragmentoExtension> = emptyList(),
    private val nombreArchivo: String = ""
) : Language {
    private val analizador = AnalizadorResaltado(lenguaje)
    private val autocompletado = IdentifierAutoComplete(PalabrasClave.para(lenguaje).toTypedArray())
    private val vacio = EmptyLanguage()

    private val pares: SymbolPairMatch = if (!autocierre) EmptyLanguage.EMPTY_SYMBOL_PAIRS else SymbolPairMatch().apply {
        putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
        putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
        putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
        putPair('"', SymbolPairMatch.SymbolPair("\"", "\""))
        putPair('\'', SymbolPairMatch.SymbolPair("'", "'"))
        if (lenguaje == Lenguaje.JAVASCRIPT) putPair('`', SymbolPairMatch.SymbolPair("`", "`"))
    }

    override fun getAnalyzeManager(): AnalyzeManager = analizador

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_STRONG

    override fun requireAutoComplete(contenido: ContentReference, posicion: CharPosition, publicador: CompletionPublisher, extras: Bundle) {
        if (!autocompletadoActivo) return
        val prefijo = CompletionHelper.computePrefix(contenido, posicion) { it.isLetterOrDigit() || it == '_' || it == '-' }
        if (prefijo.isEmpty()) return
        fragmentos.filter { (it.lenguaje == null || it.lenguaje == lenguaje.name) && it.prefijo.startsWith(prefijo, ignoreCase = true) }
            .take(20)
            .forEach { publicador.addItem(ElementoFragmento(it, prefijo.length, nombreArchivo)) }
        if (lenguaje == Lenguaje.TEXTO || prefijo.length < 2) return
        val identificadores = IdentifierAutoComplete.SyncIdentifiers()
        val vistos = HashSet<String>()
        val texto = StringBuilder()
        for (l in 0 until contenido.lineCount.coerceAtMost(3000)) {
            texto.setLength(0)
            contenido.appendLineTo(texto, l)
            var inicio = -1
            for (k in 0..texto.length) {
                val c = if (k < texto.length) texto[k] else ' '
                val parte = c.isLetterOrDigit() || c == '_'
                if (parte && inicio < 0) inicio = k
                if (!parte && inicio >= 0) {
                    val palabra = texto.substring(inicio, k)
                    if (palabra.length > 2 && !palabra[0].isDigit() && palabra != prefijo && vistos.add(palabra)) identificadores.identifierIncrease(palabra)
                    inicio = -1
                }
            }
        }
        autocompletado.requireAutoComplete(contenido, posicion, prefijo, publicador, identificadores)
    }

    override fun getIndentAdvance(contenido: ContentReference, linea: Int, columna: Int): Int {
        val antes = contenido.getLine(linea).substring(0, columna.coerceAtMost(contenido.getLine(linea).length)).trimEnd()
        if (antes.isEmpty()) return 0
        val ultimo = antes.last()
        return when (lenguaje) {
            Lenguaje.PYTHON -> if (ultimo == ':' || ultimo == '(' || ultimo == '[' || ultimo == '{') tamanoSangria else 0
            Lenguaje.JAVASCRIPT, Lenguaje.CSS, Lenguaje.JSON -> if (ultimo == '{' || ultimo == '[' || ultimo == '(') tamanoSangria else 0
            Lenguaje.HTML -> {
                val apertura = antes.lastIndexOf('<')
                val etiqueta = if (apertura >= 0) antes.substring(apertura) else ""
                val vacia = Regex("^<(area|base|br|col|embed|hr|img|input|link|meta|source|track|wbr)\\b", RegexOption.IGNORE_CASE).containsMatchIn(etiqueta)
                if (ultimo == '>' && etiqueta.startsWith("<") && !etiqueta.startsWith("</") && !etiqueta.startsWith("<!") && !etiqueta.endsWith("/>") && !vacia && !etiqueta.contains("</")) tamanoSangria else 0
            }
            Lenguaje.TEXTO -> 0
        }
    }

    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter = vacio.formatter

    override fun getSymbolPairs(): SymbolPairMatch = pares

    override fun getNewlineHandlers(): Array<NewlineHandler> = arrayOf(SeparadorLlaves(tamanoSangria))

    override fun destroy() {
        analizador.destroy()
    }
}

private class SeparadorLlaves(private val tamanoSangria: Int) : NewlineHandler {
    private val cierres = mapOf('{' to '}', '[' to ']', '(' to ')')

    override fun matchesRequirement(texto: Content, posicion: CharPosition, estilos: Styles?): Boolean {
        val linea = texto.getLineString(posicion.line)
        if (posicion.column <= 0 || posicion.column >= linea.length) return false
        val antes = linea[posicion.column - 1]
        val despues = linea[posicion.column]
        return cierres[antes] == despues || (antes == '>' && linea.substring(posicion.column).startsWith("</"))
    }

    override fun handleNewline(texto: Content, posicion: CharPosition, estilos: Styles?, tamanoTab: Int): NewlineHandleResult {
        val linea = texto.getLineString(posicion.line)
        val sangria = linea.takeWhile { it == ' ' || it == '\t' }
        val interior = sangria + " ".repeat(tamanoSangria)
        val insertado = "\n" + interior + "\n" + sangria
        return NewlineHandleResult(insertado, sangria.length + 1)
    }
}
