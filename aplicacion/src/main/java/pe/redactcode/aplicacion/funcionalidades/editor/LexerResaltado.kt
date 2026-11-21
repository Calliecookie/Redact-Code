package pe.redactcode.aplicacion.funcionalidades.editor

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import pe.redactcode.nucleo.modelo.Lenguaje

object IdColores {
    const val NORMAL = EditorColorScheme.TEXT_NORMAL
    const val PALABRA_CLAVE = EditorColorScheme.KEYWORD
    const val CADENA = EditorColorScheme.LITERAL
    const val COMENTARIO = EditorColorScheme.COMMENT
    const val FUNCION = EditorColorScheme.FUNCTION_NAME
    const val OPERADOR = EditorColorScheme.OPERATOR
    const val ETIQUETA = EditorColorScheme.HTML_TAG
    const val ATRIBUTO = EditorColorScheme.ATTRIBUTE_NAME
    const val VALOR_ATRIBUTO = EditorColorScheme.ATTRIBUTE_VALUE
    const val ANOTACION = EditorColorScheme.ANNOTATION
    const val NUMERO = 300
    const val TIPO = 301
}

data class TramoColor(val linea: Int, val columna: Int, val color: Int)

object PalabrasClave {
    val python = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else",
        "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
        "return", "try", "while", "with", "yield", "match", "case", "self"
    )
    val incorporadasPython = setOf(
        "print", "input", "len", "range", "int", "float", "str", "list", "dict", "set", "tuple", "bool", "sum", "min", "max", "abs",
        "round", "sorted", "enumerate", "zip", "map", "filter", "open", "type", "isinstance", "any", "all", "reversed"
    )
    val javascript = setOf(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "export", "extends",
        "finally", "for", "function", "if", "import", "in", "instanceof", "let", "new", "return", "super", "switch", "this", "throw",
        "try", "typeof", "var", "void", "while", "with", "yield", "async", "await", "of", "static", "get", "set", "true", "false",
        "null", "undefined", "NaN", "Infinity"
    )
    val incorporadasJavaScript = setOf(
        "console", "document", "window", "Math", "JSON", "Array", "Object", "String", "Number", "Boolean", "Promise", "Map", "Set",
        "Date", "Error", "parseInt", "parseFloat", "setTimeout", "setInterval", "localStorage", "fetch"
    )
    val json = setOf("true", "false", "null")

    fun para(lenguaje: Lenguaje): Set<String> = when (lenguaje) {
        Lenguaje.PYTHON -> python + incorporadasPython
        Lenguaje.JAVASCRIPT -> javascript + incorporadasJavaScript
        Lenguaje.JSON -> json
        Lenguaje.CSS -> pe.redactcode.nucleo.validacion.PropiedadesCss.todas()
        Lenguaje.HTML -> setOf("html", "head", "body", "title", "meta", "link", "script", "style", "div", "span", "p", "a", "img", "ul", "ol", "li",
            "h1", "h2", "h3", "header", "main", "footer", "nav", "section", "article", "form", "input", "button", "label", "table", "tr", "td", "th")
        Lenguaje.TEXTO -> emptySet()
    }
}

class LexerResaltado(private val lenguaje: Lenguaje) {
    private lateinit var texto: CharSequence
    private var i = 0
    private var linea = 0
    private var columna = 0
    private val tramos = ArrayList<TramoColor>()
    private var colorActual = -1

    fun analizar(entrada: CharSequence, cancelado: () -> Boolean = { false }): List<TramoColor> {
        texto = entrada
        i = 0
        linea = 0
        columna = 0
        tramos.clear()
        colorActual = -1
        ultimaPosicion = -1
        iteraciones = 0
        marcar(IdColores.NORMAL)
        when (lenguaje) {
            Lenguaje.PYTHON -> lexPython(cancelado)
            Lenguaje.JAVASCRIPT, Lenguaje.JSON -> lexEstiloC(cancelado)
            Lenguaje.CSS -> lexCss(cancelado)
            Lenguaje.HTML -> lexHtml(cancelado)
            Lenguaje.TEXTO -> Unit
        }
        return tramos
    }

    private fun marcar(color: Int) {
        if (color != colorActual) {
            if (tramos.isNotEmpty() && tramos.last().linea == linea && tramos.last().columna == columna) tramos.removeAt(tramos.size - 1)
            tramos += TramoColor(linea, columna, color)
            colorActual = color
        }
    }

    private var ultimaPosicion = -1
    private var iteraciones = 0

    private fun continuar(cancelado: () -> Boolean): Boolean {
        if (i == ultimaPosicion) {
            avanzar()
            if (fin()) return false
        }
        ultimaPosicion = i
        iteraciones++
        return !(iteraciones % 512 == 0 && cancelado())
    }

    private fun actual(): Char = texto[i]
    private fun ver(n: Int): Char = if (i + n < texto.length) texto[i + n] else '\u0000'
    private fun fin() = i >= texto.length

    private fun avanzar() {
        if (fin()) return
        if (texto[i] == '\n') {
            linea++
            columna = 0
            val color = colorActual
            colorActual = -1
            marcar(color)
        } else {
            columna++
        }
        i++
    }

    private fun avanzar(n: Int) = repeat(n) { if (!fin()) avanzar() }

    private fun esInicioIdentificador(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun esIdentificador(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun leerPalabra(): String {
        val inicio = i
        while (!fin() && esIdentificador(actual())) avanzar()
        return texto.subSequence(inicio, i).toString()
    }

    private fun siguienteNoEspacio(): Char {
        var k = i
        while (k < texto.length && (texto[k] == ' ' || texto[k] == '\t')) k++
        return if (k < texto.length) texto[k] else '\u0000'
    }

    private fun leerNumero() {
        marcar(IdColores.NUMERO)
        avanzar()
        while (!fin() && (actual().isLetterOrDigit() || actual() == '.' || actual() == '_')) avanzar()
        marcar(IdColores.NORMAL)
    }

    private fun leerCadena(comilla: Char, triple: Boolean = false, multilinea: Boolean = false) {
        marcar(IdColores.CADENA)
        avanzar(if (triple) 3 else 1)
        while (!fin()) {
            val c = actual()
            if (c == '\\') { avanzar(2); continue }
            if (triple && c == comilla && ver(1) == comilla && ver(2) == comilla) { avanzar(3); break }
            if (!triple && c == comilla) { avanzar(); break }
            if (!triple && !multilinea && c == '\n') break
            avanzar()
        }
        marcar(IdColores.NORMAL)
    }

    private fun lexPython(cancelado: () -> Boolean) {
        var anterior = ""
        while (!fin()) {
            if (!continuar(cancelado)) return
            val c = actual()
            when {
                c == '#' -> {
                    marcar(IdColores.COMENTARIO)
                    while (!fin() && actual() != '\n') avanzar()
                    marcar(IdColores.NORMAL)
                }
                c == '"' || c == '\'' -> leerCadena(c, triple = ver(1) == c && ver(2) == c)
                (c == 'f' || c == 'r' || c == 'b' || c == 'F' || c == 'R' || c == 'B') && (ver(1) == '"' || ver(1) == '\'') -> {
                    marcar(IdColores.CADENA)
                    avanzar()
                    val q = actual()
                    leerCadena(q, triple = ver(1) == q && ver(2) == q)
                }
                c == '@' && esInicioIdentificador(ver(1)) -> {
                    marcar(IdColores.ANOTACION)
                    avanzar()
                    leerPalabra()
                    marcar(IdColores.NORMAL)
                }
                c.isDigit() -> leerNumero()
                esInicioIdentificador(c) -> {
                    val inicioLinea = linea
                    val inicioColumna = columna
                    val palabra = leerPalabra()
                    val color = when {
                        palabra in PalabrasClave.python -> IdColores.PALABRA_CLAVE
                        anterior == "def" -> IdColores.FUNCION
                        anterior == "class" -> IdColores.TIPO
                        palabra in PalabrasClave.incorporadasPython -> IdColores.FUNCION
                        siguienteNoEspacio() == '(' -> IdColores.FUNCION
                        palabra.first().isUpperCase() -> IdColores.TIPO
                        else -> IdColores.NORMAL
                    }
                    colorearPalabra(inicioLinea, inicioColumna, color)
                    anterior = palabra
                }
                c in "+-*/%=<>!&|^~:" -> {
                    marcar(IdColores.OPERADOR)
                    avanzar()
                    marcar(IdColores.NORMAL)
                }
                else -> avanzar()
            }
            if (c == '\n') anterior = ""
        }
    }

    private fun colorearPalabra(lineaInicio: Int, columnaInicio: Int, color: Int) {
        if (color == IdColores.NORMAL) return
        val lineaFin = linea
        val columnaFin = columna
        if (tramos.isNotEmpty() && tramos.last().linea == lineaInicio && tramos.last().columna == columnaInicio) tramos.removeAt(tramos.size - 1)
        tramos += TramoColor(lineaInicio, columnaInicio, color)
        tramos += TramoColor(lineaFin, columnaFin, IdColores.NORMAL)
        colorActual = IdColores.NORMAL
    }

    private fun lexEstiloC(cancelado: () -> Boolean) {
        val esJson = lenguaje == Lenguaje.JSON
        var anterior = ""
        while (!fin()) {
            if (!continuar(cancelado)) return
            val c = actual()
            when {
                c == '/' && ver(1) == '/' -> {
                    marcar(IdColores.COMENTARIO)
                    while (!fin() && actual() != '\n') avanzar()
                    marcar(IdColores.NORMAL)
                }
                c == '/' && ver(1) == '*' -> {
                    marcar(IdColores.COMENTARIO)
                    avanzar(2)
                    while (!fin() && !(actual() == '*' && ver(1) == '/')) avanzar()
                    avanzar(2)
                    marcar(IdColores.NORMAL)
                }
                c == '"' && esJson -> {
                    val inicioLinea = linea
                    val inicioColumna = columna
                    leerCadena('"')
                    if (siguienteNoEspacio() == ':') colorearPalabra(inicioLinea, inicioColumna, IdColores.ATRIBUTO)
                }
                c == '"' || c == '\'' -> leerCadena(c)
                c == '`' -> leerCadena('`', multilinea = true)
                c.isDigit() || (c == '-' && esJson && ver(1).isDigit()) -> leerNumero()
                esInicioIdentificador(c) -> {
                    val inicioLinea = linea
                    val inicioColumna = columna
                    val palabra = leerPalabra()
                    val color = when {
                        esJson -> if (palabra in PalabrasClave.json) IdColores.PALABRA_CLAVE else IdColores.NORMAL
                        palabra in PalabrasClave.javascript -> IdColores.PALABRA_CLAVE
                        anterior == "function" || siguienteNoEspacio() == '(' -> IdColores.FUNCION
                        anterior == "class" || anterior == "new" || palabra.first().isUpperCase() -> IdColores.TIPO
                        palabra in PalabrasClave.incorporadasJavaScript -> IdColores.TIPO
                        else -> IdColores.NORMAL
                    }
                    colorearPalabra(inicioLinea, inicioColumna, color)
                    anterior = palabra
                }
                c in "+-*/%=<>!&|^~?:" -> {
                    marcar(IdColores.OPERADOR)
                    avanzar()
                    marcar(IdColores.NORMAL)
                }
                else -> avanzar()
            }
        }
    }

    private fun lexCss(cancelado: () -> Boolean) {
        var profundidad = 0
        var enValor = false
        while (!fin()) {
            if (!continuar(cancelado)) return
            val c = actual()
            when {
                c == '/' && ver(1) == '*' -> {
                    marcar(IdColores.COMENTARIO)
                    avanzar(2)
                    while (!fin() && !(actual() == '*' && ver(1) == '/')) avanzar()
                    avanzar(2)
                    marcar(IdColores.NORMAL)
                }
                c == '"' || c == '\'' -> leerCadena(c)
                c == '{' -> { profundidad++; enValor = false; avanzar() }
                c == '}' -> { profundidad = (profundidad - 1).coerceAtLeast(0); enValor = false; marcar(IdColores.NORMAL); avanzar() }
                c == ';' -> { enValor = false; marcar(IdColores.NORMAL); avanzar() }
                c == '@' -> {
                    marcar(IdColores.PALABRA_CLAVE)
                    avanzar()
                    leerPalabra()
                    marcar(IdColores.NORMAL)
                }
                c == ':' && profundidad > 0 && !enValor -> { enValor = true; marcar(IdColores.OPERADOR); avanzar(); marcar(IdColores.NORMAL) }
                profundidad > 0 && !enValor && (c.isLetter() || c == '-') -> {
                    marcar(IdColores.ATRIBUTO)
                    while (!fin() && (actual().isLetterOrDigit() || actual() == '-')) avanzar()
                    marcar(IdColores.NORMAL)
                }
                enValor && (c.isDigit() || c == '#' || (c == '.' && ver(1).isDigit())) -> leerNumero()
                enValor && c.isLetter() -> {
                    val inicioLinea = linea
                    val inicioColumna = columna
                    leerPalabra()
                    if (!fin() && actual() == '(') colorearPalabra(inicioLinea, inicioColumna, IdColores.FUNCION)
                    else colorearPalabra(inicioLinea, inicioColumna, IdColores.CADENA)
                }
                profundidad == 0 && (c.isLetter() || c == '.' || c == '#' || c == '*') -> {
                    marcar(if (c == '.' || c == '#') IdColores.TIPO else IdColores.ETIQUETA)
                    avanzar()
                    while (!fin() && (esIdentificador(actual()) || actual() == '-')) avanzar()
                    marcar(IdColores.NORMAL)
                }
                else -> avanzar()
            }
        }
    }

    private fun lexHtml(cancelado: () -> Boolean) {
        while (!fin()) {
            if (!continuar(cancelado)) return
            val c = actual()
            when {
                c == '<' && ver(1) == '!' && ver(2) == '-' && ver(3) == '-' -> {
                    marcar(IdColores.COMENTARIO)
                    while (!fin() && !(actual() == '-' && ver(1) == '-' && ver(2) == '>')) avanzar()
                    avanzar(3)
                    marcar(IdColores.NORMAL)
                }
                c == '<' && (ver(1).isLetter() || ver(1) == '/' || ver(1) == '!') -> {
                    marcar(IdColores.OPERADOR)
                    avanzar()
                    if (!fin() && (actual() == '/' || actual() == '!')) avanzar()
                    marcar(IdColores.ETIQUETA)
                    while (!fin() && (actual().isLetterOrDigit() || actual() == '-')) avanzar()
                    marcar(IdColores.NORMAL)
                    while (!fin() && actual() != '>' && actual() != '<') {
                        val d = actual()
                        when {
                            d == '"' || d == '\'' -> {
                                marcar(IdColores.VALOR_ATRIBUTO)
                                avanzar()
                                while (!fin() && actual() != d) avanzar()
                                avanzar()
                                marcar(IdColores.NORMAL)
                            }
                            d.isLetter() -> {
                                marcar(IdColores.ATRIBUTO)
                                while (!fin() && (actual().isLetterOrDigit() || actual() == '-' || actual() == ':')) avanzar()
                                marcar(IdColores.NORMAL)
                            }
                            else -> avanzar()
                        }
                    }
                    if (!fin() && actual() == '>') {
                        marcar(IdColores.OPERADOR)
                        avanzar()
                        marcar(IdColores.NORMAL)
                    }
                }
                c == '&' -> {
                    marcar(IdColores.NUMERO)
                    avanzar()
                    while (!fin() && actual().isLetterOrDigit() || (!fin() && actual() == '#')) avanzar()
                    if (!fin() && actual() == ';') avanzar()
                    marcar(IdColores.NORMAL)
                }
                else -> avanzar()
            }
        }
    }
}
