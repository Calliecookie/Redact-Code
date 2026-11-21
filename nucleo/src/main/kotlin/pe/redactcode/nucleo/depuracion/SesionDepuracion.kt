package pe.redactcode.nucleo.depuracion

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MarcoPila(val funcion: String, val linea: Int)

@Serializable
data class VariableDepuracion(val nombre: String, val valor: String, val tipo: String)

@Serializable
data class EventoSesion(
    val tipo: String,
    val texto: String = "",
    val error: Boolean = false,
    val linea: Int? = null,
    val pila: List<MarcoPila> = emptyList(),
    val variables: List<VariableDepuracion> = emptyList(),
    val estado: String? = null,
    val mensaje: String? = null,
    val duracion_ms: Long = 0
)

enum class FaseSesion { INACTIVA, EJECUTANDO, ESPERANDO_ENTRADA, PAUSADA, TERMINADA }

data class SegmentoSalida(val texto: String, val error: Boolean)

data class Evaluacion(val expresion: String, val resultado: String, val error: Boolean)

data class EstadoDepuracion(
    val fase: FaseSesion = FaseSesion.INACTIVA,
    val depurando: Boolean = false,
    val salida: List<SegmentoSalida> = emptyList(),
    val lineaActual: Int? = null,
    val pila: List<MarcoPila> = emptyList(),
    val variables: List<VariableDepuracion> = emptyList(),
    val evaluaciones: List<Evaluacion> = emptyList(),
    val estadoFinal: String? = null,
    val mensajeFinal: String? = null,
    val lineaError: Int? = null,
    val duracionMs: Long = 0,
    val expresionPendiente: String? = null
)

object ReductorSesion {
    const val MAXIMO_SALIDA = 250_000
    const val MAXIMO_EVALUACIONES = 50

    private val json = Json { ignoreUnknownKeys = true }

    fun interpretar(texto: String): EventoSesion? =
        texto.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(EventoSesion.serializer(), it) }.getOrNull() }

    fun iniciar(depurando: Boolean) = EstadoDepuracion(fase = FaseSesion.EJECUTANDO, depurando = depurando)

    fun aplicar(e: EstadoDepuracion, ev: EventoSesion): EstadoDepuracion = when (ev.tipo) {
        "salida" -> e.copy(salida = agregar(e.salida, ev.texto, ev.error))
        "entrada" -> e.copy(fase = FaseSesion.ESPERANDO_ENTRADA)
        "pausa" -> e.copy(fase = FaseSesion.PAUSADA, lineaActual = ev.linea, pila = ev.pila, variables = ev.variables)
        "evaluacion" -> e.copy(
            evaluaciones = (e.evaluaciones + Evaluacion(e.expresionPendiente.orEmpty(), ev.texto, ev.error)).takeLast(MAXIMO_EVALUACIONES),
            expresionPendiente = null
        )
        "fin" -> e.copy(
            fase = FaseSesion.TERMINADA, estadoFinal = ev.estado, mensajeFinal = ev.mensaje, lineaError = ev.linea,
            duracionMs = ev.duracion_ms, lineaActual = null, pila = emptyList(), variables = emptyList()
        )
        else -> e
    }

    fun alEnviarEntrada(e: EstadoDepuracion): EstadoDepuracion = if (e.fase == FaseSesion.ESPERANDO_ENTRADA) e.copy(fase = FaseSesion.EJECUTANDO) else e

    fun alReanudar(e: EstadoDepuracion): EstadoDepuracion = if (e.fase == FaseSesion.PAUSADA) e.copy(fase = FaseSesion.EJECUTANDO, lineaActual = null) else e

    fun alEvaluar(e: EstadoDepuracion, expresion: String): EstadoDepuracion = e.copy(expresionPendiente = expresion)

    private fun agregar(actual: List<SegmentoSalida>, texto: String, error: Boolean): List<SegmentoSalida> {
        if (texto.isEmpty()) return actual
        val total = actual.sumOf { it.texto.length }
        if (total >= MAXIMO_SALIDA) return actual
        val recortado = texto.take(MAXIMO_SALIDA - total)
        val ultimo = actual.lastOrNull()
        return if (ultimo != null && ultimo.error == error) actual.dropLast(1) + SegmentoSalida(ultimo.texto + recortado, error)
        else actual + SegmentoSalida(recortado, error)
    }
}

class PuntosInterrupcion {
    private val porArchivo = HashMap<String, java.util.TreeSet<Int>>()

    fun alternar(archivo: String, linea: Int): Boolean {
        if (linea < 1) return false
        val conjunto = porArchivo.getOrPut(archivo) { java.util.TreeSet() }
        return if (conjunto.remove(linea)) false else { conjunto.add(linea); true }
    }

    fun de(archivo: String): List<Int> = porArchivo[archivo]?.toList().orEmpty()

    fun todos(): Map<String, List<Int>> = porArchivo.filterValues { it.isNotEmpty() }.mapValues { it.value.toList() }

    fun limpiar(archivo: String? = null) { if (archivo == null) porArchivo.clear() else porArchivo.remove(archivo) }
}
