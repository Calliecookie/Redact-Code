package pe.redactcode.nucleo.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Lenguaje(val nombreVisible: String, val extensiones: List<String>) {
    @SerialName("python") PYTHON("Python", listOf("py")),
    @SerialName("javascript") JAVASCRIPT("JavaScript", listOf("js", "mjs", "cjs")),
    @SerialName("html") HTML("HTML", listOf("html", "htm")),
    @SerialName("css") CSS("CSS", listOf("css")),
    @SerialName("json") JSON("JSON", listOf("json")),
    @SerialName("texto") TEXTO("Texto plano", listOf("txt"));

    val extensionPrincipal: String get() = extensiones.first()

    companion object {
        val extensionesPermitidas: Set<String> = entries.flatMap { it.extensiones }.toSet()
        val extensionesTextoAdicionales: Set<String> = setOf(
            "md", "markdown", "xml", "svg", "csv", "tsv", "yml", "yaml", "toml", "ini", "cfg", "conf", "properties", "env", "log",
            "kt", "kts", "java", "gradle", "c", "h", "cpp", "hpp", "cc", "cs", "go", "rs", "rb", "php", "swift", "dart", "lua", "r",
            "sql", "sh", "bash", "bat", "ps1", "ts", "tsx", "jsx", "mjs", "cjs", "vue", "svelte", "scss", "sass", "less", "jsonc", "lock", "gitignore"
        )
        val extensionesEditables: Set<String> = extensionesPermitidas + extensionesTextoAdicionales

        fun desdeNombreArchivo(nombre: String): Lenguaje {
            val extension = nombre.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { extension in it.extensiones } ?: TEXTO
        }

        fun desdeClave(clave: String): Lenguaje =
            entries.firstOrNull { it.name.equals(clave, ignoreCase = true) || it.nombreVisible.equals(clave, ignoreCase = true) } ?: TEXTO
    }
}

@Serializable
enum class Severidad { ERROR, ADVERTENCIA, INFORMACION }

@Serializable
enum class CategoriaConcepto(val nombreVisible: String) {
    FUNDAMENTOS("Fundamentos"),
    VARIABLES("Variables"),
    CONDICIONALES("Condicionales"),
    BUCLES("Bucles"),
    FUNCIONES("Funciones"),
    TIPOS_DATOS("Tipos de datos"),
    SINTAXIS("Sintaxis"),
    LISTAS("Listas"),
    CADENAS("Cadenas"),
    OBJETOS("Objetos"),
    ESTRUCTURAS_DATOS("Estructuras de datos"),
    ALGORITMOS("Algoritmos"),
    EXCEPCIONES("Manejo de excepciones"),
    HTML_ESTRUCTURA("Estructura HTML"),
    CSS_ESTILOS("Estilos CSS")
}

@Serializable
enum class NivelExperiencia(val nombreVisible: String) {
    PRINCIPIANTE("Principiante"),
    INTERMEDIO("Intermedio"),
    AVANZADO("Avanzado")
}

@Serializable
data class ExplicacionError(
    val codigo: String,
    val lenguaje: Lenguaje,
    val tipo: String,
    val titulo: String,
    val explicacion: String,
    val causaProbable: String,
    val ejemploIncorrecto: String,
    val ejemploCorregido: String,
    val recomendacion: String,
    val categoria: CategoriaConcepto = CategoriaConcepto.SINTAXIS
)

@Serializable
data class Diagnostico(
    val lenguaje: Lenguaje,
    val linea: Int,
    val columna: Int,
    val severidad: Severidad,
    val codigo: String,
    val mensajeOriginal: String,
    val longitud: Int = 1,
    val explicacion: ExplicacionError? = null
) {
    val titulo: String get() = explicacion?.titulo ?: mensajeOriginal
    val tipo: String get() = explicacion?.tipo ?: if (severidad == Severidad.ERROR) "Error de sintaxis" else "Advertencia"
    val categoria: CategoriaConcepto get() = explicacion?.categoria ?: CategoriaConcepto.SINTAXIS
}

data class ResultadoValidacion(
    val lenguaje: Lenguaje,
    val diagnosticos: List<Diagnostico>,
    val duracionMs: Long,
    val motor: String
) {
    val errores: List<Diagnostico> get() = diagnosticos.filter { it.severidad == Severidad.ERROR }
    val advertencias: List<Diagnostico> get() = diagnosticos.filter { it.severidad == Severidad.ADVERTENCIA }
    val esValido: Boolean get() = errores.isEmpty()
}

@Serializable
enum class EstadoEjecucion { EN_CURSO, EXITO, ERROR, TIEMPO_AGOTADO, CANCELADO, NO_DISPONIBLE }

@Serializable
data class ResultadoEjecucion(
    val estado: EstadoEjecucion,
    val salida: String = "",
    val errores: String = "",
    val excepcion: String? = null,
    val lineaExcepcion: Int? = null,
    val duracionMs: Long = 0
)

object PosicionTexto {
    fun desdeDesplazamiento(texto: String, desplazamiento: Int): Pair<Int, Int> {
        val limite = desplazamiento.coerceIn(0, texto.length)
        var linea = 1
        var inicioLinea = 0
        for (i in 0 until limite) {
            if (texto[i] == '\n') {
                linea++
                inicioLinea = i + 1
            }
        }
        return linea to (limite - inicioLinea + 1)
    }

    fun haciaDesplazamiento(texto: String, linea: Int, columna: Int): Int {
        var lineaActual = 1
        var i = 0
        while (i < texto.length && lineaActual < linea) {
            if (texto[i] == '\n') lineaActual++
            i++
        }
        return (i + columna - 1).coerceIn(0, texto.length)
    }
}
