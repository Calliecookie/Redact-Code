package pe.redactcode.nucleo.validacion

import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.PosicionTexto
import pe.redactcode.nucleo.modelo.ResultadoValidacion
import pe.redactcode.nucleo.modelo.Severidad

enum class TipoTokenCss {
    IDENTIFICADOR, ARROBA, ALMOHADILLA, CADENA, NUMERO, FUNCION, DELIMITADOR,
    DOS_PUNTOS, PUNTO_Y_COMA, COMA, LLAVE_ABRE, LLAVE_CIERRA, PARENTESIS_ABRE, PARENTESIS_CIERRA,
    CORCHETE_ABRE, CORCHETE_CIERRA, ESPACIO, URL
}

data class TokenCss(val tipo: TipoTokenCss, val texto: String, val inicio: Int, val conSaltoLinea: Boolean = false)

data class DeclaracionCss(val propiedad: String, val valor: String, val desplazamiento: Int, val importante: Boolean)

data class ReglaCss(val selector: String, val declaraciones: List<DeclaracionCss>, val desplazamiento: Int, val contexto: String?)

data class HojaEstilosCss(val reglas: List<ReglaCss>)

data class ProblemaCss(val codigo: String, val desplazamiento: Int, val mensaje: String, val severidad: Severidad = Severidad.ERROR, val longitud: Int = 1)

class TokenizadorCss(private val texto: String) {
    val problemas = mutableListOf<ProblemaCss>()
    private var pos = 0

    fun tokenizar(): List<TokenCss> {
        val tokens = mutableListOf<TokenCss>()
        while (pos < texto.length) {
            val inicio = pos
            val c = texto[pos]
            when {
                c == '/' && siguiente(1) == '*' -> {
                    val fin = texto.indexOf("*/", pos + 2)
                    if (fin < 0) {
                        problemas += ProblemaCss("CSS006", inicio, "Comentario sin cerrar")
                        pos = texto.length
                    } else {
                        pos = fin + 2
                    }
                }
                c.isWhitespace() -> {
                    var salto = false
                    while (pos < texto.length && texto[pos].isWhitespace()) {
                        if (texto[pos] == '\n') salto = true
                        pos++
                    }
                    tokens += TokenCss(TipoTokenCss.ESPACIO, " ", inicio, salto)
                }
                c == '"' || c == '\'' -> tokens += leerCadena(c)
                c == '#' -> {
                    pos++
                    leerNombre()
                    tokens += TokenCss(TipoTokenCss.ALMOHADILLA, texto.substring(inicio, pos), inicio)
                }
                c == '@' -> {
                    pos++
                    leerNombre()
                    tokens += TokenCss(TipoTokenCss.ARROBA, texto.substring(inicio, pos), inicio)
                }
                c.isDigit() || (c == '.' && siguiente(1)?.isDigit() == true) || ((c == '-' || c == '+') && (siguiente(1)?.isDigit() == true || (siguiente(1) == '.' && siguiente(2)?.isDigit() == true))) -> {
                    pos++
                    while (pos < texto.length && (texto[pos].isDigit() || texto[pos] == '.')) pos++
                    if (pos < texto.length && (texto[pos] == '%')) pos++ else leerNombre()
                    tokens += TokenCss(TipoTokenCss.NUMERO, texto.substring(inicio, pos), inicio)
                }
                esInicioNombre(c) || (c == '-' && (siguiente(1)?.let { esInicioNombre(it) || it == '-' } == true)) -> {
                    leerNombre()
                    val nombre = texto.substring(inicio, pos)
                    if (pos < texto.length && texto[pos] == '(') {
                        pos++
                        if (nombre.equals("url", ignoreCase = true)) {
                            tokens += leerUrl(inicio)
                        } else {
                            tokens += TokenCss(TipoTokenCss.FUNCION, nombre, inicio)
                        }
                    } else {
                        tokens += TokenCss(TipoTokenCss.IDENTIFICADOR, nombre, inicio)
                    }
                }
                else -> {
                    pos++
                    val tipo = when (c) {
                        ':' -> TipoTokenCss.DOS_PUNTOS
                        ';' -> TipoTokenCss.PUNTO_Y_COMA
                        ',' -> TipoTokenCss.COMA
                        '{' -> TipoTokenCss.LLAVE_ABRE
                        '}' -> TipoTokenCss.LLAVE_CIERRA
                        '(' -> TipoTokenCss.PARENTESIS_ABRE
                        ')' -> TipoTokenCss.PARENTESIS_CIERRA
                        '[' -> TipoTokenCss.CORCHETE_ABRE
                        ']' -> TipoTokenCss.CORCHETE_CIERRA
                        else -> TipoTokenCss.DELIMITADOR
                    }
                    tokens += TokenCss(tipo, c.toString(), inicio)
                }
            }
        }
        return tokens
    }

    private fun siguiente(n: Int): Char? = texto.getOrNull(pos + n)

    private fun esInicioNombre(c: Char) = c.isLetter() || c == '_' || c.code > 127

    private fun leerNombre() {
        while (pos < texto.length) {
            val c = texto[pos]
            if (c.isLetterOrDigit() || c == '-' || c == '_' || c.code > 127) {
                pos++
            } else if (c == '\\' && pos + 1 < texto.length) {
                pos += 2
            } else {
                return
            }
        }
    }

    private fun leerCadena(comilla: Char): TokenCss {
        val inicio = pos
        pos++
        while (pos < texto.length) {
            val c = texto[pos]
            if (c == '\\') {
                pos += 2
                continue
            }
            if (c == comilla) {
                pos++
                return TokenCss(TipoTokenCss.CADENA, texto.substring(inicio, pos), inicio)
            }
            if (c == '\n') {
                problemas += ProblemaCss("CSS005", inicio, "Cadena sin cerrar")
                return TokenCss(TipoTokenCss.CADENA, texto.substring(inicio, pos), inicio)
            }
            pos++
        }
        problemas += ProblemaCss("CSS005", inicio, "Cadena sin cerrar")
        return TokenCss(TipoTokenCss.CADENA, texto.substring(inicio, pos), inicio)
    }

    private fun leerUrl(inicio: Int): TokenCss {
        while (pos < texto.length && texto[pos].isWhitespace()) pos++
        if (pos < texto.length && (texto[pos] == '"' || texto[pos] == '\'')) {
            leerCadena(texto[pos])
            while (pos < texto.length && texto[pos].isWhitespace()) pos++
        } else {
            while (pos < texto.length && texto[pos] != ')' && texto[pos] != '\n' && texto[pos] != ';' && texto[pos] != '}') pos++
        }
        if (pos < texto.length && texto[pos] == ')') {
            pos++
        } else {
            problemas += ProblemaCss("CSS010", inicio, "Paréntesis de url() sin cerrar")
        }
        return TokenCss(TipoTokenCss.URL, texto.substring(inicio, pos), inicio)
    }
}

class AnalizadorCss(private val texto: String) {
    private val tokenizador = TokenizadorCss(texto)
    private val tokens = tokenizador.tokenizar()
    private var i = 0
    private val problemas = mutableListOf<ProblemaCss>()
    private val reglas = mutableListOf<ReglaCss>()

    data class Resultado(val hoja: HojaEstilosCss, val problemas: List<ProblemaCss>)

    fun analizar(): Resultado {
        leerListaReglas(nivelSuperior = true, contexto = null)
        val todos = (tokenizador.problemas + problemas).distinctBy { it.codigo to it.desplazamiento }.sortedBy { it.desplazamiento }
        return Resultado(HojaEstilosCss(reglas), todos)
    }

    private fun actual(): TokenCss? = tokens.getOrNull(i)

    private fun saltarEspacios() {
        while (actual()?.tipo == TipoTokenCss.ESPACIO) i++
    }

    private fun leerListaReglas(nivelSuperior: Boolean, contexto: String?): Boolean {
        while (true) {
            saltarEspacios()
            val t = actual() ?: return !nivelSuperior
            when (t.tipo) {
                TipoTokenCss.LLAVE_CIERRA -> {
                    if (nivelSuperior) {
                        problemas += ProblemaCss("CSS002", t.inicio, "Llave de cierre sin bloque abierto")
                        i++
                    } else {
                        i++
                        return true
                    }
                }
                TipoTokenCss.ARROBA -> leerReglaArroba()
                TipoTokenCss.PUNTO_Y_COMA -> i++
                else -> leerReglaCualificada(contexto)
            }
        }
    }

    private fun leerReglaArroba() {
        val arroba = tokens[i]
        i++
        val nombre = arroba.texto.lowercase()
        val prelude = StringBuilder()
        while (true) {
            val t = actual()
            if (t == null) return
            when (t.tipo) {
                TipoTokenCss.PUNTO_Y_COMA -> { i++; return }
                TipoTokenCss.LLAVE_ABRE -> {
                    i++
                    val contieneReglas = nombre in setOf("@media", "@supports", "@layer", "@container", "@document", "@scope", "@starting-style") ||
                        nombre.endsWith("keyframes")
                    if (contieneReglas) {
                        val cerrado = leerListaReglas(nivelSuperior = false, contexto = "${arroba.texto} ${prelude.toString().trim()}")
                        if (!cerrado) problemas += ProblemaCss("CSS001", t.inicio, "Falta '}' para cerrar ${arroba.texto}")
                    } else {
                        val declaraciones = leerBloqueDeclaraciones(t.inicio, permitirReglasAnidadas = false, selectorPadre = arroba.texto)
                        reglas += ReglaCss(arroba.texto, declaraciones, arroba.inicio, null)
                    }
                    return
                }
                TipoTokenCss.LLAVE_CIERRA -> return
                else -> { prelude.append(t.texto); i++ }
            }
        }
    }

    private fun leerReglaCualificada(contexto: String?) {
        val inicio = actual()!!.inicio
        val prelude = mutableListOf<TokenCss>()
        while (true) {
            val t = actual()
            if (t == null) {
                reportarPreludeHuerfano(prelude, inicio, alFinal = true)
                return
            }
            when (t.tipo) {
                TipoTokenCss.LLAVE_ABRE -> {
                    i++
                    val selector = prelude.joinToString("") { it.texto }.trim().replace(Regex("\\s+"), " ")
                    if (selector.isEmpty()) problemas += ProblemaCss("CSS009", t.inicio, "Bloque sin selector")
                    val declaraciones = leerBloqueDeclaraciones(t.inicio, permitirReglasAnidadas = true, selectorPadre = selector)
                    reglas += ReglaCss(selector, declaraciones, inicio, contexto)
                    return
                }
                TipoTokenCss.PUNTO_Y_COMA -> {
                    reportarPreludeHuerfano(prelude, inicio, alFinal = false)
                    i++
                    return
                }
                TipoTokenCss.LLAVE_CIERRA -> {
                    reportarPreludeHuerfano(prelude, inicio, alFinal = false)
                    return
                }
                else -> { prelude += t; i++ }
            }
        }
    }

    private fun reportarPreludeHuerfano(prelude: List<TokenCss>, inicio: Int, alFinal: Boolean) {
        val significativos = prelude.filter { it.tipo != TipoTokenCss.ESPACIO }
        if (significativos.isEmpty()) return
        val pareceDeclaracion = significativos.size >= 2 && significativos[0].tipo == TipoTokenCss.IDENTIFICADOR &&
            significativos[1].tipo == TipoTokenCss.DOS_PUNTOS && significativos.drop(2).none { it.tipo == TipoTokenCss.DOS_PUNTOS }
        if (pareceDeclaracion && !alFinal || pareceDeclaracion && significativos.size > 2) {
            problemas += ProblemaCss("CSS011", inicio, "Declaración fuera de una regla")
        } else if (alFinal) {
            problemas += ProblemaCss("CSS001", inicio, "Selector sin bloque de declaraciones")
        } else {
            problemas += ProblemaCss("CSS011", inicio, "Contenido fuera de una regla")
        }
    }

    private fun leerBloqueDeclaraciones(inicioBloque: Int, permitirReglasAnidadas: Boolean, selectorPadre: String): List<DeclaracionCss> {
        val declaraciones = mutableListOf<DeclaracionCss>()
        var segmento = mutableListOf<TokenCss>()
        var profundidadParentesis = 0
        var inicioParentesis = -1
        while (true) {
            val t = actual()
            if (t == null) {
                procesarDeclaracion(segmento, declaraciones)
                if (profundidadParentesis > 0) problemas += ProblemaCss("CSS010", inicioParentesis, "Paréntesis sin cerrar")
                problemas += ProblemaCss("CSS001", inicioBloque, "Falta '}' para cerrar el bloque de $selectorPadre")
                return declaraciones
            }
            when (t.tipo) {
                TipoTokenCss.FUNCION, TipoTokenCss.PARENTESIS_ABRE -> {
                    if (profundidadParentesis == 0) inicioParentesis = t.inicio
                    profundidadParentesis++
                    segmento += t
                    i++
                }
                TipoTokenCss.PARENTESIS_CIERRA -> {
                    if (profundidadParentesis > 0) profundidadParentesis--
                    segmento += t
                    i++
                }
                TipoTokenCss.PUNTO_Y_COMA -> {
                    if (profundidadParentesis > 0) {
                        problemas += ProblemaCss("CSS010", inicioParentesis, "Paréntesis sin cerrar")
                        profundidadParentesis = 0
                    }
                    procesarDeclaracion(segmento, declaraciones)
                    segmento = mutableListOf()
                    i++
                }
                TipoTokenCss.LLAVE_CIERRA -> {
                    if (profundidadParentesis > 0) problemas += ProblemaCss("CSS010", inicioParentesis, "Paréntesis sin cerrar")
                    procesarDeclaracion(segmento, declaraciones)
                    i++
                    return declaraciones
                }
                TipoTokenCss.LLAVE_ABRE -> {
                    if (permitirReglasAnidadas) {
                        val selector = segmento.joinToString("") { it.texto }.trim()
                        val inicioRegla = segmento.firstOrNull { it.tipo != TipoTokenCss.ESPACIO }?.inicio ?: t.inicio
                        if (selector.isEmpty()) problemas += ProblemaCss("CSS009", t.inicio, "Bloque sin selector")
                        segmento = mutableListOf()
                        i++
                        val anidadas = leerBloqueDeclaraciones(t.inicio, true, selector)
                        reglas += ReglaCss(if (selector.contains('&')) selector.replace("&", selectorPadre) else "$selectorPadre $selector", anidadas, inicioRegla, null)
                    } else {
                        problemas += ProblemaCss("CSS002", t.inicio, "Llave inesperada dentro de las declaraciones")
                        i++
                    }
                }
                TipoTokenCss.ARROBA -> {
                    if (permitirReglasAnidadas) leerReglaArroba() else { segmento += t; i++ }
                }
                else -> { segmento += t; i++ }
            }
        }
    }

    private fun procesarDeclaracion(segmento: List<TokenCss>, destino: MutableList<DeclaracionCss>) {
        val significativos = segmento.dropWhile { it.tipo == TipoTokenCss.ESPACIO }.dropLastWhile { it.tipo == TipoTokenCss.ESPACIO }
        if (significativos.isEmpty()) return
        val primero = significativos.first()
        if (primero.tipo != TipoTokenCss.IDENTIFICADOR) {
            problemas += ProblemaCss("CSS003", primero.inicio, "Se esperaba el nombre de una propiedad")
            return
        }
        val resto = significativos.drop(1).dropWhile { it.tipo == TipoTokenCss.ESPACIO }
        if (resto.isEmpty() || resto.first().tipo != TipoTokenCss.DOS_PUNTOS) {
            problemas += ProblemaCss("CSS003", primero.inicio, "Faltan ':' después de '${primero.texto}'", longitud = primero.texto.length)
            return
        }
        val valorTokens = resto.drop(1)
        val valorSignificativo = valorTokens.filter { it.tipo != TipoTokenCss.ESPACIO }
        if (valorSignificativo.isEmpty()) {
            problemas += ProblemaCss("CSS008", primero.inicio, "La propiedad '${primero.texto}' no tiene valor", longitud = primero.texto.length)
            return
        }
        for (k in valorTokens.indices) {
            val t = valorTokens[k]
            if (t.tipo == TipoTokenCss.ESPACIO && t.conSaltoLinea) {
                val siguientes = valorTokens.drop(k + 1).filter { it.tipo != TipoTokenCss.ESPACIO }
                if (siguientes.size >= 2 && siguientes[0].tipo == TipoTokenCss.IDENTIFICADOR && siguientes[1].tipo == TipoTokenCss.DOS_PUNTOS) {
                    val anterior = valorTokens.take(k).lastOrNull { it.tipo != TipoTokenCss.ESPACIO }
                    problemas += ProblemaCss("CSS004", anterior?.let { it.inicio + it.texto.length - 1 } ?: t.inicio, "Falta ';' antes de '${siguientes[0].texto}'")
                    return
                }
            }
        }
        val valor = valorTokens.joinToString("") { it.texto }.trim()
        val importante = valor.replace(" ", "").endsWith("!important", ignoreCase = true)
        val propiedad = primero.texto.lowercase()
        if (!PropiedadesCss.esConocida(propiedad)) {
            problemas += ProblemaCss("CSS007", primero.inicio, "Propiedad desconocida: '${primero.texto}'", Severidad.ADVERTENCIA, primero.texto.length)
        }
        destino += DeclaracionCss(propiedad, valor, primero.inicio, importante)
    }
}

class ValidadorCss : Validador {
    override val lenguaje = Lenguaje.CSS

    fun validarSincrono(codigo: String): ResultadoValidacion {
        val inicio = System.nanoTime()
        val resultado = AnalizadorCss(codigo).analizar()
        val diagnosticos = resultado.problemas.map { p ->
            val (l, c) = PosicionTexto.desdeDesplazamiento(codigo, p.desplazamiento)
            CatalogoExplicaciones.diagnostico(lenguaje, l, c, p.codigo, p.mensaje, p.severidad, p.longitud)
        }
        return ResultadoValidacion(lenguaje, diagnosticos, (System.nanoTime() - inicio) / 1_000_000, "Analizador CSS de Redact Code (CSS Syntax Level 3)")
    }

    override suspend fun validar(codigo: String): ResultadoValidacion = validarSincrono(codigo)
}

object PropiedadesCss {
    private val conocidas: Set<String> = """
        accent-color align-content align-items align-self all animation animation-delay animation-direction animation-duration
        animation-fill-mode animation-iteration-count animation-name animation-play-state animation-timing-function animation-composition
        appearance aspect-ratio backdrop-filter backface-visibility background background-attachment background-blend-mode background-clip
        background-color background-image background-origin background-position background-position-x background-position-y background-repeat
        background-size block-size border border-block border-block-color border-block-end border-block-start border-block-style border-block-width
        border-bottom border-bottom-color border-bottom-left-radius border-bottom-right-radius border-bottom-style border-bottom-width border-collapse
        border-color border-end-end-radius border-end-start-radius border-image border-image-outset border-image-repeat border-image-slice
        border-image-source border-image-width border-inline border-inline-color border-inline-end border-inline-start border-inline-style
        border-inline-width border-left border-left-color border-left-style border-left-width border-radius border-right border-right-color
        border-right-style border-right-width border-spacing border-start-end-radius border-start-start-radius border-style border-top
        border-top-color border-top-left-radius border-top-right-radius border-top-style border-top-width border-width bottom box-decoration-break
        box-shadow box-sizing break-after break-before break-inside caption-side caret-color clear clip clip-path color color-scheme column-count
        column-fill column-gap column-rule column-rule-color column-rule-style column-rule-width column-span column-width columns contain
        container container-name container-type content content-visibility counter-increment counter-reset counter-set cursor direction display
        empty-cells filter flex flex-basis flex-direction flex-flow flex-grow flex-shrink flex-wrap float font font-display font-family
        font-feature-settings font-kerning font-optical-sizing font-size font-size-adjust font-stretch font-style font-synthesis font-variant
        font-variant-caps font-variant-ligatures font-variant-numeric font-variation-settings font-weight gap grid grid-area grid-auto-columns
        grid-auto-flow grid-auto-rows grid-column grid-column-end grid-column-gap grid-column-start grid-gap grid-row grid-row-end grid-row-gap
        grid-row-start grid-template grid-template-areas grid-template-columns grid-template-rows hanging-punctuation height hyphens
        image-rendering inline-size inset inset-block inset-block-end inset-block-start inset-inline inset-inline-end inset-inline-start isolation
        justify-content justify-items justify-self left letter-spacing line-break line-clamp line-height list-style list-style-image
        list-style-position list-style-type margin margin-block margin-block-end margin-block-start margin-bottom margin-inline margin-inline-end
        margin-inline-start margin-left margin-right margin-top mask mask-image mask-position mask-repeat mask-size max-block-size max-height
        max-inline-size max-width min-block-size min-height min-inline-size min-width mix-blend-mode object-fit object-position offset opacity
        order orphans outline outline-color outline-offset outline-style outline-width overflow overflow-anchor overflow-wrap overflow-x
        overflow-y overscroll-behavior overscroll-behavior-x overscroll-behavior-y padding padding-block padding-block-end padding-block-start
        padding-bottom padding-inline padding-inline-end padding-inline-start padding-left padding-right padding-top page-break-after
        page-break-before page-break-inside perspective perspective-origin place-content place-items place-self pointer-events position
        print-color-adjust quotes resize right rotate row-gap scale scroll-behavior scroll-margin scroll-margin-block scroll-margin-bottom
        scroll-margin-inline scroll-margin-left scroll-margin-right scroll-margin-top scroll-padding scroll-padding-block scroll-padding-bottom
        scroll-padding-inline scroll-padding-left scroll-padding-right scroll-padding-top scroll-snap-align scroll-snap-stop scroll-snap-type
        scrollbar-color scrollbar-gutter scrollbar-width shape-outside src tab-size table-layout text-align text-align-last text-decoration
        text-decoration-color text-decoration-line text-decoration-style text-decoration-thickness text-emphasis text-indent text-justify
        text-orientation text-overflow text-rendering text-shadow text-transform text-underline-offset text-underline-position text-wrap top
        touch-action transform transform-box transform-origin transform-style transition transition-behavior transition-delay transition-duration
        transition-property transition-timing-function translate unicode-bidi unicode-range user-select vertical-align view-transition-name
        visibility white-space white-space-collapse widows width will-change word-break word-spacing word-wrap writing-mode z-index zoom
        size marks bleed font-named-instance ascent-override descent-override line-gap-override
    """.trim().split(Regex("\\s+")).toSet()

    fun esConocida(propiedad: String): Boolean {
        val p = propiedad.lowercase()
        if (p.startsWith("--")) return true
        if (p.startsWith("-webkit-") || p.startsWith("-moz-") || p.startsWith("-ms-") || p.startsWith("-o-")) return true
        return p in conocidas
    }

    fun todas(): Set<String> = conocidas
}
