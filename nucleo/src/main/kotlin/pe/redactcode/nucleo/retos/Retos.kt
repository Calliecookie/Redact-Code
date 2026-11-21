package pe.redactcode.nucleo.retos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import pe.redactcode.nucleo.modelo.CategoriaConcepto
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.validacion.LectorRecursos
import kotlin.math.abs

@Serializable
enum class Dificultad(val nombreVisible: String, val orden: Int) {
    @SerialName("basico") BASICO("Básico", 1),
    @SerialName("intermedio") INTERMEDIO("Intermedio", 2),
    @SerialName("avanzado") AVANZADO("Avanzado", 3)
}

@Serializable
data class CasoPrueba(
    val entrada: List<JsonElement>,
    val esperado: JsonElement,
    val descripcion: String = "",
    val oculto: Boolean = false
)

@Serializable
data class DefinicionReto(
    val id: String,
    val titulo: String,
    val categoria: CategoriaConcepto,
    val dificultad: Dificultad,
    val lenguaje: Lenguaje,
    val enunciado: String,
    val codigoInicial: String,
    val funcion: String,
    val pruebas: List<CasoPrueba>,
    val pistas: List<String> = emptyList(),
    val conceptos: List<String> = emptyList(),
    val minutosEstimados: Int = 10
)

@Serializable
data class PaqueteRetos(
    val formato: String = FormatoRetos.IDENTIFICADOR,
    val version: Int = FormatoRetos.VERSION,
    val nombre: String = "",
    val retos: List<DefinicionReto>
)

data class ResultadoImportacion(val retos: List<DefinicionReto>, val errores: List<String>)

object FormatoRetos {
    const val IDENTIFICADOR = "redactcode.retos"
    const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val nombreValido = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    fun importar(texto: String): ResultadoImportacion {
        val paquete = try {
            json.decodeFromString<PaqueteRetos>(texto)
        } catch (e: Exception) {
            return ResultadoImportacion(emptyList(), listOf("El archivo no tiene el formato de retos de Redact Code: ${e.message?.take(200)}"))
        }
        val errores = mutableListOf<String>()
        if (paquete.formato != IDENTIFICADOR) errores += "Formato desconocido: ${paquete.formato}"
        if (paquete.version > VERSION) errores += "La versión ${paquete.version} del formato es más reciente que la soportada ($VERSION)"
        val validos = paquete.retos.filter { reto ->
            val problemas = validar(reto)
            problemas.forEach { errores += "${reto.id}: $it" }
            problemas.isEmpty()
        }
        val duplicados = validos.groupBy { it.id }.filter { it.value.size > 1 }.keys
        duplicados.forEach { errores += "Identificador repetido: $it" }
        return ResultadoImportacion(validos.distinctBy { it.id }, errores)
    }

    fun validar(reto: DefinicionReto): List<String> {
        val problemas = mutableListOf<String>()
        if (reto.id.isBlank()) problemas += "el identificador está vacío"
        if (reto.titulo.isBlank()) problemas += "el título está vacío"
        if (reto.lenguaje != Lenguaje.PYTHON && reto.lenguaje != Lenguaje.JAVASCRIPT) problemas += "solo se admiten retos en Python o JavaScript"
        if (!nombreValido.matches(reto.funcion)) problemas += "el nombre de función '${reto.funcion}' no es válido"
        if (reto.pruebas.isEmpty()) problemas += "debe incluir al menos una prueba"
        if (!reto.codigoInicial.contains(reto.funcion)) problemas += "el código inicial debe declarar la función ${reto.funcion}"
        return problemas
    }

    fun exportar(retos: List<DefinicionReto>, nombre: String = ""): String = json.encodeToString(PaqueteRetos.serializer(), PaqueteRetos(nombre = nombre, retos = retos))
}

object CatalogoRetos {
    val retos: List<DefinicionReto> by lazy {
        val resultado = FormatoRetos.importar(LectorRecursos.leerTexto("/pe/redactcode/nucleo/catalogos/retos.json"))
        check(resultado.errores.isEmpty()) { "Catálogo de retos inválido: ${resultado.errores}" }
        resultado.retos
    }

    fun buscar(id: String) = retos.firstOrNull { it.id == id }
}

data class ResultadoCaso(
    val indice: Int,
    val caso: CasoPrueba,
    val aprobado: Boolean,
    val obtenido: String?,
    val error: String?
)

data class ResultadoPruebas(
    val casos: List<ResultadoCaso>,
    val errorGeneral: String?,
    val lineaError: Int?,
    val salida: String,
    val duracionMs: Long,
    val tiempoAgotado: Boolean = false
) {
    val aprobados get() = casos.count { it.aprobado }
    val total get() = casos.size
    val todosAprobados get() = errorGeneral == null && casos.isNotEmpty() && casos.all { it.aprobado }
    val puntaje: Int get() = if (casos.isEmpty()) 0 else aprobados * 100 / total
}

interface EjecutorPruebas {
    val lenguaje: Lenguaje
    suspend fun ejecutar(codigo: String, funcion: String, casos: List<CasoPrueba>, limiteMs: Long = 5_000): ResultadoPruebas
}

@Serializable
private data class RespuestaArnes(
    val resultados: List<RespuestaCaso> = emptyList(),
    val salida: List<String> = emptyList(),
    val error: String? = null,
    val linea: Int? = null
)

@Serializable
private data class RespuestaCaso(val ok: Boolean, val valor: String? = null, val error: String? = null)

object InterpreteArnes {
    private val json = Json { ignoreUnknownKeys = true }

    fun interpretar(texto: String, casos: List<CasoPrueba>, duracionMs: Long): ResultadoPruebas {
        val respuesta = try {
            json.decodeFromString<RespuestaArnes>(texto)
        } catch (e: Exception) {
            return ResultadoPruebas(emptyList(), "Respuesta no válida del evaluador", null, texto.take(500), duracionMs)
        }
        if (respuesta.error != null) {
            return ResultadoPruebas(emptyList(), respuesta.error, respuesta.linea, respuesta.salida.joinToString("\n"), duracionMs)
        }
        val resultados = casos.mapIndexed { indice, caso ->
            val r = respuesta.resultados.getOrNull(indice)
            when {
                r == null -> ResultadoCaso(indice, caso, false, null, "No se obtuvo resultado")
                !r.ok -> ResultadoCaso(indice, caso, false, null, r.error)
                else -> {
                    val obtenido = r.valor ?: "null"
                    val elemento = try { json.parseToJsonElement(obtenido) } catch (e: Exception) { JsonPrimitive(obtenido) }
                    ResultadoCaso(indice, caso, ComparadorValores.iguales(caso.esperado, elemento), obtenido, null)
                }
            }
        }
        return ResultadoPruebas(resultados, null, null, respuesta.salida.joinToString("\n"), duracionMs)
    }
}

object ComparadorValores {
    fun iguales(esperado: JsonElement, obtenido: JsonElement): Boolean = when {
        esperado is JsonNull -> obtenido is JsonNull
        esperado is JsonPrimitive && obtenido is JsonPrimitive -> primitivosIguales(esperado, obtenido)
        esperado is JsonArray && obtenido is JsonArray ->
            esperado.size == obtenido.size && esperado.indices.all { iguales(esperado[it], obtenido[it]) }
        esperado is JsonObject && obtenido is JsonObject ->
            esperado.keys == obtenido.keys && esperado.keys.all { iguales(esperado.getValue(it), obtenido.getValue(it)) }
        else -> false
    }

    private fun primitivosIguales(a: JsonPrimitive, b: JsonPrimitive): Boolean {
        if (a is JsonNull || b is JsonNull) return a is JsonNull && b is JsonNull
        if (a.isString != b.isString) return false
        if (a.isString) return a.content == b.content
        val ba = a.booleanOrNull
        val bb = b.booleanOrNull
        if (ba != null || bb != null) return ba == bb
        val da = a.doubleOrNull
        val db = b.doubleOrNull
        if (da != null && db != null) return abs(da - db) <= 1e-9 * maxOf(1.0, abs(da), abs(db))
        return a.content == b.content
    }

    fun formatear(elemento: JsonElement): String = elemento.toString()

    fun formatearEntrada(entrada: List<JsonElement>): String = entrada.joinToString(", ") { it.toString() }
}
