package pe.redactcode.nucleo.javascript

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Script
import org.mozilla.javascript.ScriptableObject
import pe.redactcode.nucleo.validacion.LectorRecursos
import java.util.concurrent.Executors

sealed interface ResultadoMotor {
    data class Exito(val texto: String) : ResultadoMotor
    data class Fallo(val nombre: String, val mensaje: String, val linea: Int) : ResultadoMotor
    data object TiempoAgotado : ResultadoMotor
    data class NoDisponible(val motivo: String) : ResultadoMotor
}

interface MotorJavaScript {
    val nombre: String
    val procesoAislado: Boolean
    suspend fun evaluar(script: String, limiteMs: Long, conAnalizador: Boolean): ResultadoMotor
    fun cerrar() {}
}

object ScriptsAnalisis {
    val acorn: String by lazy { LectorRecursos.leerTexto("/pe/redactcode/nucleo/js/acorn.js") }
    val analisis: String by lazy { LectorRecursos.leerTexto("/pe/redactcode/nucleo/js/analisis_redact.js") }
}

class TiempoAgotadoRhino : Error("Tiempo de ejecución agotado")

private class FabricaContextoLimitado : ContextFactory() {
    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        val limite = cx.getThreadLocal(CLAVE_LIMITE) as? Long ?: return
        if (System.currentTimeMillis() > limite) throw TiempoAgotadoRhino()
    }

    override fun makeContext(): Context {
        val cx = super.makeContext()
        cx.instructionObserverThreshold = 5_000
        return cx
    }

    companion object {
        const val CLAVE_LIMITE = "redact.limite"
    }
}

class MotorRhino : MotorJavaScript {
    override val nombre = "Rhino ${Context::class.java.`package`?.implementationVersion ?: ""}".trim()
    override val procesoAislado = false

    private val despachador = Executors.newSingleThreadExecutor { tarea ->
        Thread(null, tarea, "redact-rhino", 8L * 1024 * 1024).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val fabrica = FabricaContextoLimitado()
    private var alcanceAnalisis: ScriptableObject? = null
    private var scriptsCompilados: List<Script>? = null

    override suspend fun evaluar(script: String, limiteMs: Long, conAnalizador: Boolean): ResultadoMotor = withContext(despachador) {
        val cx = fabrica.enterContext()
        try {
            cx.isInterpretedMode = true
            cx.languageVersion = Context.VERSION_ECMASCRIPT
            val alcance = if (conAnalizador) {
                cx.putThreadLocal(FabricaContextoLimitado.CLAVE_LIMITE, Long.MAX_VALUE)
                val base = obtenerAlcanceAnalisis(cx)
                (cx.newObject(base) as ScriptableObject).also {
                    it.prototype = base
                    it.parentScope = null
                }
            } else {
                cx.initSafeStandardObjects()
            }
            cx.putThreadLocal(FabricaContextoLimitado.CLAVE_LIMITE, System.currentTimeMillis() + limiteMs)
            val resultado = cx.evaluateString(alcance, script, "redact.js", 1, null)
            ResultadoMotor.Exito(Context.toString(resultado))
        } catch (e: TiempoAgotadoRhino) {
            ResultadoMotor.TiempoAgotado
        } catch (e: EcmaError) {
            ResultadoMotor.Fallo(e.name, e.errorMessage, e.lineNumber())
        } catch (e: EvaluatorException) {
            ResultadoMotor.Fallo("SyntaxError", e.details(), e.lineNumber())
        } catch (e: JavaScriptException) {
            ResultadoMotor.Fallo("Error", e.details(), e.lineNumber())
        } catch (e: RhinoException) {
            ResultadoMotor.Fallo("Error", e.details(), e.lineNumber())
        } catch (e: StackOverflowError) {
            ResultadoMotor.Fallo("RangeError", "Se excedió el tamaño máximo de la pila de llamadas", 0)
        } finally {
            Context.exit()
        }
    }

    private fun obtenerAlcanceAnalisis(cx: Context): ScriptableObject {
        alcanceAnalisis?.let { return it }
        val compilados = scriptsCompilados ?: listOf(
            cx.compileString(ScriptsAnalisis.acorn, "acorn.js", 1, null),
            cx.compileString(ScriptsAnalisis.analisis, "analisis_redact.js", 1, null)
        ).also { scriptsCompilados = it }
        val alcance = cx.initStandardObjects()
        compilados.forEach { it.exec(cx, alcance, alcance) }
        alcance.sealObject()
        alcanceAnalisis = alcance
        return alcance
    }

    override fun cerrar() {
        despachador.close()
    }
}
