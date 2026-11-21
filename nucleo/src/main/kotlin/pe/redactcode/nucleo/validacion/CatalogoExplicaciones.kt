package pe.redactcode.nucleo.validacion

import kotlinx.serialization.json.Json
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.ExplicacionError
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.Severidad

object LectorRecursos {
    val json = Json { ignoreUnknownKeys = true; isLenient = false; encodeDefaults = true }

    fun leerTexto(ruta: String): String =
        LectorRecursos::class.java.getResourceAsStream(ruta)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Recurso no encontrado: $ruta")
}

object CatalogoExplicaciones {
    private val entradas: Map<String, ExplicacionError> by lazy {
        LectorRecursos.json.decodeFromString<List<ExplicacionError>>(
            LectorRecursos.leerTexto("/pe/redactcode/nucleo/catalogos/explicaciones_errores.json")
        ).associateBy { it.codigo }
    }

    val todas: Collection<ExplicacionError> get() = entradas.values

    fun buscar(codigo: String): ExplicacionError? = entradas[codigo]

    fun diagnostico(
        lenguaje: Lenguaje,
        linea: Int,
        columna: Int,
        codigo: String,
        mensajeOriginal: String,
        severidad: Severidad = Severidad.ERROR,
        longitud: Int = 1
    ): Diagnostico {
        val explicacion = entradas[codigo] ?: entradas.getValue("GEN001")
        return Diagnostico(
            lenguaje = lenguaje,
            linea = linea.coerceAtLeast(1),
            columna = columna.coerceAtLeast(1),
            severidad = severidad,
            codigo = if (entradas.containsKey(codigo)) codigo else "GEN001",
            mensajeOriginal = mensajeOriginal,
            longitud = longitud.coerceAtLeast(1),
            explicacion = explicacion
        )
    }
}

object ClasificadorMensajes {
    fun python(tipo: String, mensaje: String): String {
        val m = mensaje.lowercase()
        return when {
            tipo == "IndentationError" && m.contains("unexpected indent") -> "PY002"
            m.contains("expected an indented block") -> "PY003"
            m.contains("unindent does not match") || m.contains("inconsistent use of tabs") || tipo == "TabError" -> "PY004"
            m.contains("expected ':'") -> "PY001"
            m.contains("was never closed") -> "PY005"
            m.contains("does not match opening") || m.contains("unmatched") -> "PY006"
            m.contains("unterminated string") || m.contains("unterminated triple-quoted") || m.contains("eol while scanning") -> "PY007"
            m.contains("missing parentheses in call to 'print'") -> "PY010"
            m.contains("perhaps you forgot a comma") -> "PY011"
            m.contains("'return' outside function") -> "PY012"
            m.contains("outside loop") || m.contains("not properly in loop") -> "PY013"
            m.contains("cannot assign to") || m.contains("maybe you meant '=='") || m.contains("cannot use assignment") -> "PY008"
            m.contains("invalid character") || m.contains("invalid non-printable") -> "PY015"
            m.contains("invalid decimal literal") || m.contains("invalid digit") || m.contains("leading zeros") -> "PY016"
            m.contains("unexpected eof") || m.contains("eof while") -> "PY017"
            tipo == "IndentationError" -> "PY004"
            else -> "PY009"
        }
    }

    fun excepcionPython(tipo: String): String = when (tipo) {
        "NameError", "UnboundLocalError" -> "PYR001"
        "TypeError" -> "PYR002"
        "IndexError" -> "PYR003"
        "KeyError" -> "PYR004"
        "ZeroDivisionError" -> "PYR005"
        "ValueError" -> "PYR006"
        "AttributeError" -> "PYR007"
        "RecursionError" -> "PYR008"
        "TiempoAgotado" -> "PYR009"
        else -> "PYR010"
    }

    fun javascript(mensaje: String, alFinal: Boolean): String {
        val m = mensaje.lowercase()
        return when {
            m.contains("unterminated string") -> "JS002"
            m.contains("unterminated template") -> "JS003"
            m.contains("unterminated comment") -> "JS007"
            m.contains("unterminated regular expression") -> "JS011"
            m.contains("has already been declared") -> "JS005"
            m.contains("assigning to rvalue") || m.contains("invalid left-hand side") -> "JS006"
            m.contains("'return' outside of function") -> "JS009"
            m.contains("unsyntactic break") || m.contains("unsyntactic continue") -> "JS010"
            m.contains("missing initializer") || m.contains("complex binding patterns require") -> "JS012"
            m.contains("identifier directly after number") || m.contains("invalid number") -> "JS013"
            m.contains("'import' and 'export'") -> "JS016"
            m.contains("unexpected keyword") || m.contains("reserved word") || m.contains("is reserved") -> "JS004"
            m.contains("unexpected character") -> "JS008"
            alFinal -> "JS015"
            else -> "JS001"
        }
    }

    fun excepcionJavaScript(nombre: String): String = when (nombre) {
        "ReferenceError" -> "JSR001"
        "TypeError" -> "JSR002"
        "RangeError", "InternalError" -> "JSR003"
        "TiempoAgotado" -> "JSR004"
        else -> "JSR002"
    }

    fun html(mensaje: String): Pair<String, Severidad> {
        val m = mensaje.lowercase()
        return when {
            m.contains("dropped duplicate attribute") -> "HTML007" to Severidad.ADVERTENCIA
            m.contains("unexpected endtag") || m.contains("unexpected end tag") -> "HTML001" to Severidad.ERROR
            m.contains("eof") || m.contains("end of file") -> "HTML002" to Severidad.ERROR
            m.contains("character reference") -> "HTML003" to Severidad.ADVERTENCIA
            m.contains("unexpected character") || m.contains("unexpected token") && m.contains("attribute") -> "HTML004" to Severidad.ERROR
            else -> "HTML008" to Severidad.ERROR
        }
    }
}
