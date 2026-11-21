package pe.redactcode.aplicacion.funcionalidades.editor

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import pe.redactcode.nucleo.extensiones.FragmentoExtension
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import pe.redactcode.aplicacion.R
import pe.redactcode.aplicacion.nucleo.diseno.ColoresEditor
import pe.redactcode.aplicacion.nucleo.diseno.FuenteMonoJetBrains
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresEditor
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.preferencias.FuenteEditor
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.Severidad

data class PosicionCursor(val linea: Int, val columna: Int, val seleccion: String)

class ControladorEditores(private val contexto: Context) {
    private val editores = LinkedHashMap<String, CodeEditor>()
    private var activo: String? = null
    var alCambiarTexto: (String, String) -> Unit = { _, _ -> }
    var alMoverCursor: (PosicionCursor) -> Unit = {}
    var fragmentos: List<FragmentoExtension> = emptyList()

    val editorActivo: CodeEditor? get() = activo?.let { editores[it] }

    fun obtener(clave: String, texto: String, lenguaje: Lenguaje, preferencias: PreferenciasUsuario, colores: ColoresEditor): CodeEditor {
        val existente = editores[clave]
        if (existente != null) {
            activo = clave
            return existente
        }
        val editor = CodeEditor(contexto)
        configurar(editor, lenguaje, preferencias, colores, clave.substringAfterLast('/'))
        editor.setText(texto)
        editor.subscribeAlways(ContentChangeEvent::class.java) { evento ->
            if (evento.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) alCambiarTexto(clave, editor.text.toString())
        }
        editor.subscribeAlways(SelectionChangeEvent::class.java) {
            val cursor = editor.cursor
            val seleccion = if (cursor.isSelected) editor.text.subSequence(cursor.left, cursor.right).toString() else ""
            alMoverCursor(PosicionCursor(cursor.leftLine + 1, cursor.leftColumn + 1, seleccion))
        }
        editores[clave] = editor
        activo = clave
        return editor
    }

    fun configurar(editor: CodeEditor, lenguaje: Lenguaje, preferencias: PreferenciasUsuario, colores: ColoresEditor, nombreArchivo: String = "") {
        val fuente = if (preferencias.fuenteEditor == FuenteEditor.JETBRAINS_MONO) {
            ResourcesCompat.getFont(contexto, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
        } else Typeface.MONOSPACE
        editor.typefaceText = fuente
        editor.typefaceLineNumber = fuente
        editor.setTextSize(preferencias.tamanoFuenteEditor.toFloat())
        editor.tabWidth = preferencias.sangriaEspacios
        editor.setWordwrap(preferencias.ajusteLinea)
        editor.setPinLineNumber(true)
        editor.setLineNumberEnabled(preferencias.numerosLinea)
        editor.setHighlightCurrentLine(preferencias.resaltarLineaActual)
        editor.setLineSpacing(0f, preferencias.interlineadoEditor)
        editor.setLigatureEnabled(preferencias.ligaduras)
        editor.nonPrintablePaintingFlags = if (preferencias.mostrarInvisibles) {
            CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_WHITESPACE_INNER or CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING
        } else 0
        runCatching { editor.getComponent(EditorAutoCompletion::class.java).isEnabled = preferencias.autocompletado }
        editor.setCursorAnimationEnabled(preferencias.animaciones)
        editor.setScalable(true)
        editor.setBlockLineEnabled(true)
        editor.props.symbolPairAutoCompletion = preferencias.autocierre
        editor.props.autoIndent = true
        editor.props.deleteEmptyLineFast = false
        editor.props.highlightMatchingDelimiters = true
        editor.props.stickyScroll = preferencias.stickyScroll && (lenguaje == Lenguaje.PYTHON || lenguaje == Lenguaje.JAVASCRIPT)
        editor.setEditorLanguage(LenguajeEditor(lenguaje, preferencias.sangriaEspacios, preferencias.autocierre, preferencias.autocompletado, fragmentos, nombreArchivo))
        editor.colorScheme = esquema(colores)
        editor.contentDescription = "Editor de código ${lenguaje.nombreVisible}"
    }

    fun reconfigurarTodos(lenguajes: Map<String, Lenguaje>, preferencias: PreferenciasUsuario, colores: ColoresEditor) {
        editores.forEach { (clave, editor) -> configurar(editor, lenguajes[clave] ?: Lenguaje.TEXTO, preferencias, colores, clave.substringAfterLast('/')) }
    }

    fun texto(clave: String): String? = editores[clave]?.text?.toString()

    fun reemplazarTexto(clave: String, texto: String) {
        editores[clave]?.setText(texto)
    }

    fun reemplazarDocumento(clave: String, texto: String) {
        val editor = editores[clave] ?: return
        val contenido = editor.text
        if (contenido.toString() == texto) return
        val ultima = contenido.lineCount - 1
        contenido.replace(0, 0, ultima, contenido.getColumnCount(ultima), texto)
    }

    fun claves(): Set<String> = editores.keys.toSet()

    fun renombrarClave(anterior: String, nueva: String) {
        val editor = editores.remove(anterior) ?: return
        editores[nueva] = editor
        if (activo == anterior) activo = nueva
    }

    fun duplicarLinea() {
        val editor = editorActivo ?: return
        val c = editor.cursor
        val texto = editor.text
        if (c.isSelected) {
            val seleccion = texto.subSequence(c.left, c.right).toString()
            texto.insert(c.rightLine, c.rightColumn, seleccion)
        } else {
            val linea = c.leftLine
            texto.insert(linea, texto.getColumnCount(linea), "\n" + texto.getLineString(linea))
        }
    }

    fun moverLinea(arriba: Boolean) {
        val editor = editorActivo ?: return
        val texto = editor.text
        val linea = editor.cursor.leftLine
        val columna = editor.cursor.leftColumn
        val otra = if (arriba) linea - 1 else linea + 1
        if (otra < 0 || otra >= texto.lineCount) return
        val actual = texto.getLineString(linea)
        val vecina = texto.getLineString(otra)
        val primera = minOf(linea, otra)
        val segunda = maxOf(linea, otra)
        val nuevo = if (arriba) actual + "\n" + vecina else vecina + "\n" + actual
        texto.replace(primera, 0, segunda, texto.getColumnCount(segunda), nuevo)
        editor.setSelection(otra, columna.coerceAtMost(actual.length))
    }

    fun alternarComentario(lenguaje: Lenguaje) {
        val editor = editorActivo ?: return
        val texto = editor.text
        val c = editor.cursor
        val (inicio, fin) = when (lenguaje) {
            Lenguaje.PYTHON -> "# " to ""
            Lenguaje.JAVASCRIPT -> "// " to ""
            Lenguaje.CSS -> "/* " to " */"
            Lenguaje.HTML -> "<!-- " to " -->"
            else -> return
        }
        val desde = c.leftLine
        val hasta = c.rightLine
        val lineas = (desde..hasta).map { texto.getLineString(it) }
        val comentadas = lineas.filter { it.isNotBlank() }.all { it.trimStart().startsWith(inicio.trim()) }
        val resultado = lineas.map { l ->
            if (l.isBlank()) l
            else if (comentadas) {
                val sangria = l.takeWhile { it == ' ' || it == '\t' }
                var cuerpo = l.drop(sangria.length).removePrefix(inicio).removePrefix(inicio.trim())
                if (fin.isNotEmpty()) cuerpo = cuerpo.removeSuffix(fin).removeSuffix(fin.trim())
                sangria + cuerpo
            } else {
                val sangria = l.takeWhile { it == ' ' || it == '\t' }
                sangria + inicio + l.drop(sangria.length) + fin
            }
        }
        texto.replace(desde, 0, hasta, texto.getColumnCount(hasta), resultado.joinToString("\n"))
    }

    fun seleccionarTodo() = editorActivo?.selectAll()

    fun cursorActivo(): Pair<Int, Int>? = editorActivo?.cursor?.let { it.leftLine + 1 to it.leftColumn + 1 }

    fun cerrar(clave: String) {
        editores.remove(clave)?.release()
        if (activo == clave) activo = null
    }

    fun liberarTodo() {
        editores.values.forEach { it.release() }
        editores.clear()
        activo = null
    }

    fun insertar(texto: String) {
        val editor = editorActivo ?: return
        editor.commitText(texto)
    }

    fun tabulacion() {
        val editor = editorActivo ?: return
        editor.insertText(" ".repeat(editor.tabWidth), editor.tabWidth)
    }

    fun deshacer() = editorActivo?.takeIf { it.canUndo() }?.undo()
    fun rehacer() = editorActivo?.takeIf { it.canRedo() }?.redo()
    fun puedeDeshacer() = editorActivo?.canUndo() == true
    fun puedeRehacer() = editorActivo?.canRedo() == true

    fun irA(linea: Int, columna: Int) {
        val editor = editorActivo ?: return
        val l = (linea - 1).coerceIn(0, editor.text.lineCount - 1)
        val c = (columna - 1).coerceIn(0, editor.text.getColumnCount(l))
        editor.setSelection(l, c)
        editor.ensureSelectionVisible()
    }

    fun buscar(consulta: String, distinguirMayusculas: Boolean, palabraCompleta: Boolean): Int {
        val editor = editorActivo ?: return 0
        if (consulta.isEmpty()) {
            editor.searcher.stopSearch()
            return 0
        }
        val tipo = if (palabraCompleta) EditorSearcher.SearchOptions.TYPE_WHOLE_WORD else EditorSearcher.SearchOptions.TYPE_NORMAL
        editor.searcher.search(consulta, EditorSearcher.SearchOptions(tipo, !distinguirMayusculas))
        return 0
    }

    fun coincidencias(): Int = runCatching { editorActivo?.searcher?.matchedPositionCount ?: 0 }.getOrDefault(0)
    fun siguiente() = runCatching { editorActivo?.searcher?.gotoNext() }
    fun anterior() = runCatching { editorActivo?.searcher?.gotoPrevious() }
    fun reemplazarActual(texto: String) = runCatching { editorActivo?.searcher?.let { if (it.isMatchedPositionSelected) it.replaceCurrentMatch(texto) else it.gotoNext() } }
    fun reemplazarTodo(texto: String) = runCatching { editorActivo?.searcher?.replaceAll(texto) }
    fun detenerBusqueda() = runCatching { editorActivo?.searcher?.stopSearch() }

    fun aplicarDiagnosticos(clave: String, diagnosticos: List<Diagnostico>) {
        val editor = editores[clave] ?: return
        val contenido = editor.text
        val regiones = diagnosticos.mapIndexedNotNull { indice, d ->
            runCatching {
                val linea = (d.linea - 1).coerceIn(0, contenido.lineCount - 1)
                val columnas = contenido.getColumnCount(linea)
                val columna = (d.columna - 1).coerceIn(0, columnas)
                var inicio = contenido.getCharIndex(linea, columna)
                var fin = (inicio + d.longitud).coerceAtMost(contenido.getCharIndex(linea, columnas))
                if (fin <= inicio) {
                    inicio = contenido.getCharIndex(linea, 0)
                    fin = contenido.getCharIndex(linea, columnas).coerceAtLeast(inicio + 1).coerceAtMost(contenido.length)
                }
                val severidad = when (d.severidad) {
                    Severidad.ERROR -> DiagnosticRegion.SEVERITY_ERROR
                    Severidad.ADVERTENCIA -> DiagnosticRegion.SEVERITY_WARNING
                    Severidad.INFORMACION -> DiagnosticRegion.SEVERITY_TYPO
                }
                DiagnosticRegion(inicio, fin.coerceAtLeast(inicio), severidad, indice.toLong())
            }.getOrNull()
        }
        editor.diagnostics?.let {
            it.reset()
            it.addDiagnostics(regiones)
        }
        editor.invalidate()
    }

    private fun esquema(c: ColoresEditor) = EditorColorScheme().apply {
        setColor(EditorColorScheme.WHOLE_BACKGROUND, c.fondo.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, c.fondoNumeros.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER, c.numeroLinea.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, c.texto.toArgb())
        setColor(EditorColorScheme.LINE_DIVIDER, c.fondo.toArgb())
        setColor(EditorColorScheme.CURRENT_LINE, c.lineaActual.toArgb())
        setColor(EditorColorScheme.TEXT_NORMAL, c.texto.toArgb())
        setColor(EditorColorScheme.KEYWORD, c.palabraClave.toArgb())
        setColor(EditorColorScheme.LITERAL, c.cadena.toArgb())
        setColor(EditorColorScheme.COMMENT, c.comentario.toArgb())
        setColor(EditorColorScheme.FUNCTION_NAME, c.funcion.toArgb())
        setColor(EditorColorScheme.OPERATOR, c.operador.toArgb())
        setColor(EditorColorScheme.HTML_TAG, c.etiqueta.toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_NAME, c.atributo.toArgb())
        setColor(EditorColorScheme.ATTRIBUTE_VALUE, c.cadena.toArgb())
        setColor(EditorColorScheme.ANNOTATION, c.tipo.toArgb())
        setColor(IdColores.NUMERO, c.numero.toArgb())
        setColor(IdColores.TIPO, c.tipo.toArgb())
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, c.seleccion.toArgb())
        setColor(EditorColorScheme.SELECTION_INSERT, c.cursor.toArgb())
        setColor(EditorColorScheme.SELECTION_HANDLE, c.cursor.toArgb())
        setColor(EditorColorScheme.PROBLEM_ERROR, c.error.toArgb())
        setColor(EditorColorScheme.PROBLEM_WARNING, c.advertencia.toArgb())
        setColor(EditorColorScheme.PROBLEM_TYPO, c.comentario.toArgb())
        setColor(EditorColorScheme.BLOCK_LINE, c.lineaActual.toArgb())
        setColor(EditorColorScheme.BLOCK_LINE_CURRENT, c.numeroLinea.toArgb())
        setColor(EditorColorScheme.SIDE_BLOCK_LINE, c.numeroLinea.toArgb())
        setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, c.seleccion.toArgb())
        setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, c.cursor.toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB, c.numeroLinea.toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, c.texto.toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, c.lineaActual.toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_CORNER, c.numeroLinea.toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, c.texto.toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, c.comentario.toArgb())
        setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, c.seleccion.toArgb())
        setColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, c.lineaActual.toArgb())
        setColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, c.texto.toArgb())
        setColor(EditorColorScheme.NON_PRINTABLE_CHAR, c.numeroLinea.toArgb())
    }
}

@Composable
fun rememberControladorEditores(): ControladorEditores {
    val contexto = LocalContext.current
    val controlador = remember { ControladorEditores(contexto) }
    DisposableEffect(controlador) { onDispose { controlador.liberarTodo() } }
    return controlador
}

@Composable
fun VistaEditor(
    controlador: ControladorEditores,
    clave: String,
    textoInicial: String,
    lenguaje: Lenguaje,
    preferencias: PreferenciasUsuario,
    modifier: Modifier = Modifier
) {
    val colores = LocalColoresEditor.current
    AndroidView(
        modifier = modifier,
        factory = { ctx -> FrameLayout(ctx) },
        update = { contenedor ->
            val editor = controlador.obtener(clave, textoInicial, lenguaje, preferencias, colores)
            if (contenedor.childCount != 1 || contenedor.getChildAt(0) !== editor) {
                contenedor.removeAllViews()
                (editor.parent as? ViewGroup)?.removeView(editor)
                contenedor.addView(editor, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        }
    )
}

@Composable
fun BarraSimbolos(controlador: ControladorEditores, lenguaje: Lenguaje, modifier: Modifier = Modifier) {
    val simbolos = buildList {
        add("Tab" to "Tab")
        addAll(listOf("{", "}", "(", ")", "[", "]", "<", ">", ";", ":", "=", "\"", "'", ".", ",", "_").map { it to it })
        when (lenguaje) {
            Lenguaje.PYTHON -> addAll(listOf("#", "+", "-", "*", "/", "%").map { it to it })
            Lenguaje.JAVASCRIPT -> addAll(listOf("=>", "`", "$", "!", "&", "|", "+", "-").map { it to it })
            Lenguaje.HTML -> addAll(listOf("/", "!", "-", "#").map { it to it })
            Lenguaje.CSS -> addAll(listOf("#", "-", "%", "!").map { it to it })
            else -> Unit
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        simbolos.forEach { (etiqueta, valor) ->
            Box(
                Modifier
                    .height(36.dp)
                    .widthIn(min = 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { if (valor == "Tab") controlador.tabulacion() else controlador.insertar(valor) }
                    .padding(horizontal = 10.dp)
                    .semantics { contentDescription = if (valor == "Tab") "Insertar sangría" else "Insertar $valor" },
                contentAlignment = Alignment.Center
            ) {
                Text(etiqueta, style = MaterialTheme.typography.labelLarge.copy(fontFamily = FuenteMonoJetBrains), color = if (valor == "Tab") LocalColoresExtendidos.current.acento else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
