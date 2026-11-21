package pe.redactcode.nucleo.proyectos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pe.redactcode.nucleo.modelo.CategoriaConcepto
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.retos.CasoPrueba
import pe.redactcode.nucleo.retos.Dificultad
import pe.redactcode.nucleo.retos.EjecutorPruebas
import pe.redactcode.nucleo.validacion.AnalizadorCss
import pe.redactcode.nucleo.validacion.AnalizadorHtml
import pe.redactcode.nucleo.validacion.LectorRecursos

@Serializable
enum class TipoVerificacion {
    @SerialName("funcion") FUNCION,
    @SerialName("selector_html") SELECTOR_HTML,
    @SerialName("regla_css") REGLA_CSS
}

@Serializable
data class Verificacion(
    val tipo: TipoVerificacion,
    val archivo: String,
    val descripcion: String,
    val funcion: String = "",
    val pruebas: List<CasoPrueba> = emptyList(),
    val selector: String = "",
    val propiedad: String = ""
)

@Serializable
data class PasoProyecto(
    val titulo: String,
    val explicacion: String,
    val objetivo: String,
    val pista: String = "",
    val verificaciones: List<Verificacion>
)

@Serializable
data class ProyectoGuiado(
    val id: String,
    val titulo: String,
    val nivel: Dificultad,
    val lenguaje: Lenguaje,
    val objetivo: String,
    val descripcion: String,
    val requisitos: List<String>,
    val conceptos: List<CategoriaConcepto>,
    val archivosIniciales: Map<String, String>,
    val pasos: List<PasoProyecto>,
    val resultadoEsperado: String
)

object CatalogoProyectosGuiados {
    val proyectos: List<ProyectoGuiado> by lazy {
        LectorRecursos.json.decodeFromString<List<ProyectoGuiado>>(LectorRecursos.leerTexto("/pe/redactcode/nucleo/catalogos/proyectos_guiados.json"))
    }

    fun buscar(id: String) = proyectos.firstOrNull { it.id == id }
}

data class ResultadoVerificacion(val verificacion: Verificacion, val aprobada: Boolean, val detalle: String)

data class ResultadoPaso(val resultados: List<ResultadoVerificacion>) {
    val aprobado: Boolean get() = resultados.isNotEmpty() && resultados.all { it.aprobada }
}

class VerificadorProyecto(
    private val ejecutores: Map<Lenguaje, EjecutorPruebas>,
    private val leerArchivo: suspend (String) -> String?
) {
    suspend fun verificar(paso: PasoProyecto): ResultadoPaso = ResultadoPaso(paso.verificaciones.map { verificar(it) })

    suspend fun verificar(v: Verificacion): ResultadoVerificacion {
        val contenido = leerArchivo(v.archivo) ?: return ResultadoVerificacion(v, false, "No se encontró el archivo ${v.archivo}")
        return when (v.tipo) {
            TipoVerificacion.FUNCION -> {
                val lenguaje = Lenguaje.desdeNombreArchivo(v.archivo)
                val ejecutor = ejecutores[lenguaje] ?: return ResultadoVerificacion(v, false, "No hay un ejecutor disponible para ${lenguaje.nombreVisible}")
                val r = ejecutor.ejecutar(contenido, v.funcion, v.pruebas)
                when {
                    r.errorGeneral != null -> ResultadoVerificacion(v, false, r.errorGeneral + (r.lineaError?.let { " (línea $it)" } ?: ""))
                    r.todosAprobados -> ResultadoVerificacion(v, true, "${r.aprobados}/${r.total} pruebas aprobadas")
                    else -> {
                        val fallo = r.casos.first { !it.aprobado }
                        val detalle = fallo.error ?: "Con ${fallo.caso.entrada.joinToString(", ")} se esperaba ${fallo.caso.esperado} y se obtuvo ${fallo.obtenido}"
                        ResultadoVerificacion(v, false, "${r.aprobados}/${r.total} pruebas aprobadas. $detalle")
                    }
                }
            }
            TipoVerificacion.SELECTOR_HTML -> {
                val documento = AnalizadorHtml.analizar(contenido).documento
                val encontrado = try { documento.select(v.selector).isNotEmpty() } catch (e: Exception) { false }
                ResultadoVerificacion(v, encontrado, if (encontrado) "Encontrado" else "Aún no se encuentra: ${v.descripcion.lowercase()}")
            }
            TipoVerificacion.REGLA_CSS -> {
                val hoja = AnalizadorCss(contenido).analizar().hoja
                val normalizar: (String) -> String = { it.replace(Regex("\\s+"), " ").trim().lowercase() }
                val reglas = hoja.reglas.filter { regla -> regla.selector.split(',').any { normalizar(it) == normalizar(v.selector) } }
                val cumple = if (v.propiedad.isBlank()) reglas.isNotEmpty() else reglas.any { r -> r.declaraciones.any { it.propiedad == v.propiedad.lowercase() && it.valor.isNotBlank() } }
                ResultadoVerificacion(v, cumple, if (cumple) "Regla encontrada" else "Falta: ${v.descripcion.lowercase()}")
            }
        }
    }
}
