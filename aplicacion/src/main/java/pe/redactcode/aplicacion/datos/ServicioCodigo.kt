package pe.redactcode.aplicacion.datos

import pe.redactcode.aplicacion.nucleo.ejecucion.PuentePython
import pe.redactcode.nucleo.javascript.ServicioJavaScript
import pe.redactcode.nucleo.mentor.AnalizadorEstructura
import pe.redactcode.nucleo.mentor.EstructuraCodigo
import pe.redactcode.nucleo.mentor.ExplicadorLineas
import pe.redactcode.nucleo.mentor.MentorLocal
import pe.redactcode.nucleo.mentor.MentorProgramacion
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.ResultadoEjecucion
import pe.redactcode.nucleo.modelo.ResultadoValidacion
import pe.redactcode.nucleo.retos.EjecutorPruebas
import pe.redactcode.nucleo.revision.InformeRevision
import pe.redactcode.nucleo.revision.RevisorCodigo
import pe.redactcode.nucleo.validacion.Validador
import pe.redactcode.nucleo.validacion.ValidadorCss
import pe.redactcode.nucleo.validacion.ValidadorHtml
import pe.redactcode.nucleo.validacion.ValidadorJson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServicioCodigo @Inject constructor(
    val python: PuentePython,
    val javaScript: ServicioJavaScript,
    proveedorExterno: ProveedorAnthropic
) {
    private val validadores: Map<Lenguaje, Validador> = mapOf(
        Lenguaje.PYTHON to python,
        Lenguaje.JAVASCRIPT to javaScript,
        Lenguaje.HTML to ValidadorHtml(),
        Lenguaje.CSS to ValidadorCss(),
        Lenguaje.JSON to ValidadorJson()
    )

    val ejecutoresPruebas: Map<Lenguaje, EjecutorPruebas> = mapOf(Lenguaje.PYTHON to python, Lenguaje.JAVASCRIPT to javaScript)

    private val revisor = RevisorCodigo(javaScript.fuenteHallazgos, python.fuenteHallazgos)

    val analizadorEstructura = AnalizadorEstructura { lenguaje, codigo ->
        when (lenguaje) {
            Lenguaje.PYTHON -> python.estructura(codigo)
            Lenguaje.JAVASCRIPT -> javaScript.estructura(codigo)
            Lenguaje.HTML -> ExplicadorLineas.estructuraHtml(codigo)
            Lenguaje.CSS -> ExplicadorLineas.estructuraCss(codigo)
            Lenguaje.JSON -> ExplicadorLineas.estructuraJson(codigo)
            Lenguaje.TEXTO -> EstructuraCodigo("El texto plano no tiene estructura de código")
        }
    }

    private val mentorLocal = MentorLocal(analizadorEstructura)
    val mentor = MentorProgramacion(mentorLocal, proveedorExterno) { proveedorExterno.disponible() }

    suspend fun validar(lenguaje: Lenguaje, codigo: String): ResultadoValidacion {
        val validador = validadores[lenguaje] ?: return ResultadoValidacion(lenguaje, emptyList(), 0, "Sin validador")
        return try {
            validador.validar(codigo)
        } catch (e: Exception) {
            ResultadoValidacion(lenguaje, emptyList(), 0, "Validador no disponible: ${e.message?.take(80)}")
        }
    }

    suspend fun revisar(lenguaje: Lenguaje, codigo: String, diagnosticos: List<Diagnostico>): InformeRevision = revisor.revisar(lenguaje, codigo, diagnosticos)

    suspend fun ejecutarPython(codigo: String, entrada: String, limiteSegundos: Int): ResultadoEjecucion = python.ejecutar(codigo, entrada, limiteSegundos)

    suspend fun ejecutarJavaScript(codigo: String, limiteSegundos: Int): ResultadoEjecucion = javaScript.ejecutar(codigo, limiteSegundos * 1000L)

    fun cancelarPython() = python.cancelar()
}
