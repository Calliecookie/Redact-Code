package pe.redactcode.nucleo.aprendizaje

import pe.redactcode.nucleo.modelo.CategoriaConcepto
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.NivelExperiencia
import pe.redactcode.nucleo.retos.DefinicionReto
import pe.redactcode.nucleo.retos.Dificultad
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

data class EstadoRepaso(
    val repeticiones: Int = 0,
    val intervaloDias: Int = 0,
    val factorFacilidad: Double = 2.5,
    val proximaFecha: LocalDate
)

object AlgoritmoSm2 {
    const val FACILIDAD_MINIMA = 1.3

    fun calcular(estado: EstadoRepaso, calidad: Int, hoy: LocalDate): EstadoRepaso {
        require(calidad in 0..5) { "La calidad debe estar entre 0 y 5" }
        val nuevaFacilidad = max(
            FACILIDAD_MINIMA,
            estado.factorFacilidad + (0.1 - (5 - calidad) * (0.08 + (5 - calidad) * 0.02))
        )
        if (calidad < 3) {
            return EstadoRepaso(0, 1, nuevaFacilidad, hoy.plusDays(1))
        }
        val repeticiones = estado.repeticiones + 1
        val intervalo = when (repeticiones) {
            1 -> 1
            2 -> 6
            else -> (estado.intervaloDias * nuevaFacilidad).roundToInt().coerceAtLeast(1)
        }
        return EstadoRepaso(repeticiones, intervalo, nuevaFacilidad, hoy.plusDays(intervalo.toLong()))
    }
}

object CalculadoraRacha {
    fun rachaActual(diasActivos: Collection<LocalDate>, hoy: LocalDate): Int {
        val dias = diasActivos.toSet()
        var cursor = if (hoy in dias) hoy else hoy.minusDays(1)
        var racha = 0
        while (cursor in dias) {
            racha++
            cursor = cursor.minusDays(1)
        }
        return racha
    }

    fun mejorRacha(diasActivos: Collection<LocalDate>): Int {
        val ordenados = diasActivos.toSortedSet().toList()
        var mejor = 0
        var actual = 0
        var anterior: LocalDate? = null
        for (dia in ordenados) {
            actual = if (anterior != null && anterior.plusDays(1) == dia) actual + 1 else 1
            mejor = max(mejor, actual)
            anterior = dia
        }
        return mejor
    }
}

data class RegistroErrorAprendizaje(val categoria: CategoriaConcepto, val lenguaje: Lenguaje, val codigo: String, val fecha: LocalDate)

data class IntentoAprendizaje(
    val retoId: String,
    val categoria: CategoriaConcepto,
    val lenguaje: Lenguaje,
    val dificultad: Dificultad,
    val aprobado: Boolean,
    val puntaje: Int,
    val fecha: LocalDate
)

data class DominioConcepto(
    val categoria: CategoriaConcepto,
    val puntaje: Int,
    val intentos: Int,
    val aprobados: Int,
    val errores: Int,
    val estado: EstadoDominio
)

enum class EstadoDominio(val nombreVisible: String) {
    SIN_DATOS("Sin datos"),
    EN_PROGRESO("En progreso"),
    DIFICULTAD("Dificultad recurrente"),
    DOMINADO("Dominado")
}

data class Recomendacion(val titulo: String, val detalle: String, val categoria: CategoriaConcepto?, val retoId: String?)

data class PerfilCalculado(
    val dominios: List<DominioConcepto>,
    val erroresFrecuentes: List<Pair<String, Int>>,
    val lenguajesUsados: List<Pair<Lenguaje, Int>>,
    val dominados: List<CategoriaConcepto>,
    val dificultades: List<CategoriaConcepto>,
    val nivelSugerido: NivelExperiencia,
    val evolucionSemanal: List<Pair<LocalDate, Int>>
) {
    val tieneDatos: Boolean get() = dominios.any { it.estado != EstadoDominio.SIN_DATOS }
}

object AnalizadorAprendizaje {
    fun calcular(
        intentos: List<IntentoAprendizaje>,
        errores: List<RegistroErrorAprendizaje>,
        usoLenguajes: Map<Lenguaje, Int>,
        hoy: LocalDate
    ): PerfilCalculado {
        val dominios = CategoriaConcepto.entries.map { categoria ->
            val intentosCategoria = intentos.filter { it.categoria == categoria }
            val erroresCategoria = errores.count { it.categoria == categoria && it.fecha >= hoy.minusDays(30) }
            val aprobadosUnicos = intentosCategoria.filter { it.aprobado }.map { it.retoId }.distinct().size
            val intentosTotales = intentosCategoria.size
            if (intentosTotales == 0 && erroresCategoria == 0) {
                DominioConcepto(categoria, 0, 0, 0, 0, EstadoDominio.SIN_DATOS)
            } else {
                val tasaExito = if (intentosTotales == 0) 0.0 else intentosCategoria.count { it.aprobado }.toDouble() / intentosTotales
                val bonoDificultad = intentosCategoria.filter { it.aprobado }.maxOfOrNull { it.dificultad.orden } ?: 0
                val penalizacion = (erroresCategoria * 4).coerceAtMost(40)
                val puntaje = ((tasaExito * 60) + aprobadosUnicos.coerceAtMost(4) * 7 + bonoDificultad * 4 - penalizacion).roundToInt().coerceIn(0, 100)
                val estado = when {
                    puntaje >= 75 && aprobadosUnicos >= 2 -> EstadoDominio.DOMINADO
                    erroresCategoria >= 5 || (intentosTotales >= 3 && tasaExito < 0.4) -> EstadoDominio.DIFICULTAD
                    else -> EstadoDominio.EN_PROGRESO
                }
                DominioConcepto(categoria, puntaje, intentosTotales, aprobadosUnicos, erroresCategoria, estado)
            }
        }
        val erroresFrecuentes = errores.groupingBy { it.codigo }.eachCount().toList().sortedByDescending { it.second }.take(8)
        val lenguajes = usoLenguajes.toList().filter { it.second > 0 }.sortedByDescending { it.second }
        val dominados = dominios.filter { it.estado == EstadoDominio.DOMINADO }.map { it.categoria }
        val dificultades = dominios.filter { it.estado == EstadoDominio.DIFICULTAD }.sortedByDescending { it.errores }.map { it.categoria }
        val avanzadosAprobados = intentos.count { it.aprobado && it.dificultad == Dificultad.AVANZADO }
        val intermediosAprobados = intentos.count { it.aprobado && it.dificultad == Dificultad.INTERMEDIO }
        val nivel = when {
            avanzadosAprobados >= 3 && dominados.size >= 4 -> NivelExperiencia.AVANZADO
            intermediosAprobados >= 3 || dominados.size >= 2 -> NivelExperiencia.INTERMEDIO
            else -> NivelExperiencia.PRINCIPIANTE
        }
        val inicioSemanas = hoy.minusWeeks(7)
        val evolucion = (0..7).map { semana ->
            val desde = inicioSemanas.plusWeeks(semana.toLong())
            val hasta = desde.plusDays(6)
            val enSemana = intentos.filter { it.fecha in desde..hasta }
            desde to if (enSemana.isEmpty()) -1 else enSemana.map { it.puntaje }.average().roundToInt()
        }
        return PerfilCalculado(dominios, erroresFrecuentes, lenguajes, dominados, dificultades, nivel, evolucion)
    }

    fun recomendar(perfil: PerfilCalculado, catalogo: List<DefinicionReto>, resueltos: Set<String>, lenguajesPreferidos: List<Lenguaje>): List<Recomendacion> {
        val recomendaciones = mutableListOf<Recomendacion>()
        val prioridad = if (perfil.dificultades.isNotEmpty()) perfil.dificultades else
            perfil.dominios.filter { it.estado == EstadoDominio.EN_PROGRESO }.sortedBy { it.puntaje }.map { it.categoria }
        val ordenLenguaje: (DefinicionReto) -> Int = { reto ->
            val indice = lenguajesPreferidos.indexOf(reto.lenguaje)
            if (indice < 0) lenguajesPreferidos.size else indice
        }
        for (categoria in prioridad.take(3)) {
            val reto = catalogo.filter { it.categoria == categoria && it.id !in resueltos }
                .sortedWith(compareBy<DefinicionReto>({ ordenLenguaje(it) }, { it.dificultad.orden })).firstOrNull()
            val motivo = if (categoria in perfil.dificultades) "Detectamos errores frecuentes en ${categoria.nombreVisible.lowercase()}." else
                "Aún no consolidas ${categoria.nombreVisible.lowercase()}."
            recomendaciones += Recomendacion("Refuerza ${categoria.nombreVisible}", motivo, categoria, reto?.id)
        }
        if (recomendaciones.size < 3) {
            val nivelMaximo = when (perfil.nivelSugerido) {
                NivelExperiencia.PRINCIPIANTE -> 1
                NivelExperiencia.INTERMEDIO -> 2
                NivelExperiencia.AVANZADO -> 3
            }
            catalogo.filter { it.id !in resueltos && it.dificultad.orden <= nivelMaximo && recomendaciones.none { r -> r.retoId == it.id } }
                .sortedWith(compareBy<DefinicionReto>({ ordenLenguaje(it) }, { it.dificultad.orden }))
                .take(3 - recomendaciones.size)
                .forEach { recomendaciones += Recomendacion(it.titulo, "Reto ${it.dificultad.nombreVisible.lowercase()} de ${it.categoria.nombreVisible.lowercase()} en ${it.lenguaje.nombreVisible}.", it.categoria, it.id) }
        }
        return recomendaciones
    }
}
