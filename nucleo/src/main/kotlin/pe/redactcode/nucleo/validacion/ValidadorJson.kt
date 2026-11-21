package pe.redactcode.nucleo.validacion

import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.PosicionTexto
import pe.redactcode.nucleo.modelo.ResultadoValidacion
import pe.redactcode.nucleo.modelo.Severidad

interface Validador {
    val lenguaje: Lenguaje
    suspend fun validar(codigo: String): ResultadoValidacion
}

sealed interface NodoJson {
    val inicio: Int
    data class Objeto(val miembros: List<Pair<String, NodoJson>>, override val inicio: Int) : NodoJson
    data class Arreglo(val elementos: List<NodoJson>, override val inicio: Int) : NodoJson
    data class Cadena(val valor: String, override val inicio: Int) : NodoJson
    data class Numero(val texto: String, override val inicio: Int) : NodoJson
    data class Logico(val valor: Boolean, override val inicio: Int) : NodoJson
    data class Nulo(override val inicio: Int) : NodoJson
}

class ErrorAnalisisJson(val codigo: String, val desplazamiento: Int, mensaje: String) : Exception(mensaje)

class AnalizadorJson(private val texto: String) {
    private var pos = 0
    val advertencias = mutableListOf<Pair<Int, String>>()

    fun analizar(): NodoJson {
        saltarEspacios()
        if (pos >= texto.length) throw ErrorAnalisisJson("JSON014", 0, "Documento vacío")
        val valor = leerValor()
        saltarEspacios()
        if (pos < texto.length) {
            if (texto[pos] == '}' || texto[pos] == ']') throw ErrorAnalisisJson("JSON007", pos, "Cierre inesperado '${texto[pos]}'")
            throw ErrorAnalisisJson("JSON010", pos, "Contenido inesperado después del valor principal")
        }
        return valor
    }

    private fun saltarEspacios() {
        while (pos < texto.length) {
            val c = texto[pos]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos++
            } else if (c == '/' && pos + 1 < texto.length && (texto[pos + 1] == '/' || texto[pos + 1] == '*')) {
                throw ErrorAnalisisJson("JSON011", pos, "Los comentarios no están permitidos en JSON")
            } else {
                return
            }
        }
    }

    private fun leerValor(): NodoJson {
        if (pos >= texto.length) throw ErrorAnalisisJson("JSON006", texto.length, "Se esperaba un valor pero terminó el documento")
        return when (val c = texto[pos]) {
            '{' -> leerObjeto()
            '[' -> leerArreglo()
            '"' -> NodoJson.Cadena(leerCadena(), pos)
            '\'' -> throw ErrorAnalisisJson("JSON002", pos, "Las cadenas deben usar comillas dobles")
            't' -> leerLiteral("true") { NodoJson.Logico(true, it) }
            'f' -> leerLiteral("false") { NodoJson.Logico(false, it) }
            'n' -> leerLiteral("null") { NodoJson.Nulo(it) }
            '}', ']' -> throw ErrorAnalisisJson("JSON007", pos, "Cierre inesperado '$c'")
            ',' -> throw ErrorAnalisisJson("JSON001", pos, "Coma inesperada")
            else -> if (c == '-' || c == '+' || c == '.' || c.isDigit()) leerNumero() else {
                val palabra = leerPalabra()
                throw ErrorAnalisisJson("JSON008", pos - palabra.length, "Valor no válido: '$palabra'")
            }
        }
    }

    private fun leerPalabra(): String {
        val inicio = pos
        while (pos < texto.length && (texto[pos].isLetterOrDigit() || texto[pos] == '_' || texto[pos] == '$')) pos++
        if (pos == inicio) pos++
        return texto.substring(inicio, pos)
    }

    private fun leerLiteral(esperado: String, crear: (Int) -> NodoJson): NodoJson {
        val inicio = pos
        val palabra = leerPalabra()
        if (palabra != esperado) throw ErrorAnalisisJson("JSON008", inicio, "Valor no válido: '$palabra'")
        return crear(inicio)
    }

    private fun leerNumero(): NodoJson {
        val inicio = pos
        if (texto[pos] == '+' || texto[pos] == '.') throw ErrorAnalisisJson("JSON012", pos, "Número con formato no válido")
        if (texto[pos] == '-') pos++
        if (pos >= texto.length || !texto[pos].isDigit()) throw ErrorAnalisisJson("JSON012", inicio, "Número con formato no válido")
        if (texto[pos] == '0' && pos + 1 < texto.length && texto[pos + 1].isDigit()) {
            throw ErrorAnalisisJson("JSON012", inicio, "Los números no pueden tener ceros a la izquierda")
        }
        if (texto[pos] == '0' && pos + 1 < texto.length && (texto[pos + 1] == 'x' || texto[pos + 1] == 'X')) {
            throw ErrorAnalisisJson("JSON012", inicio, "JSON no admite números hexadecimales")
        }
        while (pos < texto.length && texto[pos].isDigit()) pos++
        if (pos < texto.length && texto[pos] == '.') {
            pos++
            if (pos >= texto.length || !texto[pos].isDigit()) throw ErrorAnalisisJson("JSON012", inicio, "Falta la parte decimal")
            while (pos < texto.length && texto[pos].isDigit()) pos++
        }
        if (pos < texto.length && (texto[pos] == 'e' || texto[pos] == 'E')) {
            pos++
            if (pos < texto.length && (texto[pos] == '+' || texto[pos] == '-')) pos++
            if (pos >= texto.length || !texto[pos].isDigit()) throw ErrorAnalisisJson("JSON012", inicio, "Exponente incompleto")
            while (pos < texto.length && texto[pos].isDigit()) pos++
        }
        if (pos < texto.length && (texto[pos].isLetter() || texto[pos] == '_')) {
            throw ErrorAnalisisJson("JSON012", inicio, "Número seguido de letras")
        }
        return NodoJson.Numero(texto.substring(inicio, pos), inicio)
    }

    private fun leerCadena(): String {
        val inicio = pos
        pos++
        val sb = StringBuilder()
        while (true) {
            if (pos >= texto.length) throw ErrorAnalisisJson("JSON009", inicio, "Cadena sin cerrar")
            val c = texto[pos]
            when {
                c == '"' -> { pos++; return sb.toString() }
                c == '\n' || c == '\r' -> throw ErrorAnalisisJson("JSON009", inicio, "Cadena sin cerrar antes del salto de línea")
                c < ' ' -> throw ErrorAnalisisJson("JSON013", pos, "Carácter de control dentro de la cadena")
                c == '\\' -> {
                    if (pos + 1 >= texto.length) throw ErrorAnalisisJson("JSON009", inicio, "Cadena sin cerrar")
                    when (val e = texto[pos + 1]) {
                        '"', '\\', '/' -> { sb.append(e); pos += 2 }
                        'b' -> { sb.append('\b'); pos += 2 }
                        'f' -> { sb.append('\u000C'); pos += 2 }
                        'n' -> { sb.append('\n'); pos += 2 }
                        'r' -> { sb.append('\r'); pos += 2 }
                        't' -> { sb.append('\t'); pos += 2 }
                        'u' -> {
                            val hex = texto.substring(pos + 2, minOf(pos + 6, texto.length))
                            if (hex.length < 4 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                                throw ErrorAnalisisJson("JSON013", pos, "Escape unicode incompleto")
                            }
                            sb.append(hex.toInt(16).toChar())
                            pos += 6
                        }
                        else -> throw ErrorAnalisisJson("JSON013", pos, "Secuencia de escape no válida '\\$e'")
                    }
                }
                else -> { sb.append(c); pos++ }
            }
        }
    }

    private fun leerObjeto(): NodoJson {
        val inicio = pos
        pos++
        val miembros = mutableListOf<Pair<String, NodoJson>>()
        val claves = mutableSetOf<String>()
        saltarEspacios()
        if (pos < texto.length && texto[pos] == '}') { pos++; return NodoJson.Objeto(miembros, inicio) }
        while (true) {
            saltarEspacios()
            if (pos >= texto.length) throw ErrorAnalisisJson("JSON006", texto.length, "Falta cerrar el objeto abierto")
            val c = texto[pos]
            val inicioClave = pos
            val clave = when {
                c == '"' -> leerCadena()
                c == '\'' -> throw ErrorAnalisisJson("JSON002", pos, "Las claves deben usar comillas dobles")
                c == '}' -> throw ErrorAnalisisJson("JSON001", ultimaComaAntes(pos), "Coma sobrante antes de '}'")
                c.isLetter() || c == '_' || c == '$' -> throw ErrorAnalisisJson("JSON003", pos, "La clave debe ir entre comillas dobles")
                else -> throw ErrorAnalisisJson("JSON003", pos, "Se esperaba una clave entre comillas dobles")
            }
            if (!claves.add(clave)) advertencias += inicioClave to "Clave duplicada: \"$clave\""
            saltarEspacios()
            if (pos >= texto.length) throw ErrorAnalisisJson("JSON006", texto.length, "Falta cerrar el objeto abierto")
            if (texto[pos] != ':') throw ErrorAnalisisJson("JSON005", pos, "Se esperaban ':' después de la clave")
            pos++
            saltarEspacios()
            val valor = leerValor()
            miembros += clave to valor
            saltarEspacios()
            if (pos >= texto.length) throw ErrorAnalisisJson("JSON006", texto.length, "Falta '}' para cerrar el objeto")
            when (texto[pos]) {
                ',' -> pos++
                '}' -> { pos++; return NodoJson.Objeto(miembros, inicio) }
                ']' -> throw ErrorAnalisisJson("JSON007", pos, "Se esperaba '}' pero se encontró ']'")
                else -> throw ErrorAnalisisJson("JSON004", pos, "Falta una coma entre los miembros del objeto")
            }
        }
    }

    private fun leerArreglo(): NodoJson {
        val inicio = pos
        pos++
        val elementos = mutableListOf<NodoJson>()
        saltarEspacios()
        if (pos < texto.length && texto[pos] == ']') { pos++; return NodoJson.Arreglo(elementos, inicio) }
        while (true) {
            saltarEspacios()
            if (pos < texto.length && texto[pos] == ']') throw ErrorAnalisisJson("JSON001", ultimaComaAntes(pos), "Coma sobrante antes de ']'")
            elementos += leerValor()
            saltarEspacios()
            if (pos >= texto.length) throw ErrorAnalisisJson("JSON006", texto.length, "Falta ']' para cerrar el arreglo")
            when (texto[pos]) {
                ',' -> pos++
                ']' -> { pos++; return NodoJson.Arreglo(elementos, inicio) }
                '}' -> throw ErrorAnalisisJson("JSON007", pos, "Se esperaba ']' pero se encontró '}'")
                else -> throw ErrorAnalisisJson("JSON004", pos, "Falta una coma entre los elementos del arreglo")
            }
        }
    }

    private fun ultimaComaAntes(limite: Int): Int {
        var i = limite - 1
        while (i >= 0 && texto[i] != ',') i--
        return if (i >= 0) i else limite
    }
}

class ValidadorJson : Validador {
    override val lenguaje = Lenguaje.JSON

    fun validarSincrono(codigo: String): ResultadoValidacion {
        val inicio = System.nanoTime()
        val diagnosticos = mutableListOf<Diagnostico>()
        val analizador = AnalizadorJson(codigo)
        try {
            analizador.analizar()
            analizador.advertencias.forEach { (desplazamiento, mensaje) ->
                val (l, c) = PosicionTexto.desdeDesplazamiento(codigo, desplazamiento)
                diagnosticos += CatalogoExplicaciones.diagnostico(lenguaje, l, c, "JSON015", mensaje, Severidad.ADVERTENCIA)
            }
        } catch (e: ErrorAnalisisJson) {
            val (l, c) = PosicionTexto.desdeDesplazamiento(codigo, e.desplazamiento)
            diagnosticos += CatalogoExplicaciones.diagnostico(lenguaje, l, c, e.codigo, e.message ?: "Error de JSON")
        }
        return ResultadoValidacion(lenguaje, diagnosticos, (System.nanoTime() - inicio) / 1_000_000, "Analizador JSON de Redact Code (RFC 8259)")
    }

    override suspend fun validar(codigo: String): ResultadoValidacion = validarSincrono(codigo)
}
