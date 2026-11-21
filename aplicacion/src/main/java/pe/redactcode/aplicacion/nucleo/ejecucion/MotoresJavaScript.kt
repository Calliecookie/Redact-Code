package pe.redactcode.aplicacion.nucleo.ejecucion

import android.content.Context
import androidx.concurrent.futures.await
import androidx.javascriptengine.EvaluationFailedException
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import pe.redactcode.nucleo.javascript.MotorJavaScript
import pe.redactcode.nucleo.javascript.MotorRhino
import pe.redactcode.nucleo.javascript.ResultadoMotor
import pe.redactcode.nucleo.javascript.ScriptsAnalisis

class MotorJavaScriptSandbox(private val contexto: Context) : MotorJavaScript {
    override val nombre = "V8 en proceso aislado (JavaScriptSandbox)"
    override val procesoAislado = true

    private val exclusion = Mutex()
    private var sandbox: JavaScriptSandbox? = null
    private var aislamientoAnalisis: JavaScriptIsolate? = null

    val soportado: Boolean by lazy { runCatching { JavaScriptSandbox.isSupported() }.getOrDefault(false) }

    private suspend fun obtenerSandbox(): JavaScriptSandbox =
        sandbox ?: JavaScriptSandbox.createConnectedInstanceAsync(contexto.applicationContext).await().also { sandbox = it }

    private suspend fun obtenerAnalisis(): JavaScriptIsolate {
        aislamientoAnalisis?.let { return it }
        val nuevo = obtenerSandbox().createIsolate()
        nuevo.evaluateJavaScriptAsync(ScriptsAnalisis.acorn + "\n;" + ScriptsAnalisis.analisis + "\n;'listo'").await()
        aislamientoAnalisis = nuevo
        return nuevo
    }

    private fun reiniciar() {
        runCatching { aislamientoAnalisis?.close() }
        runCatching { sandbox?.close() }
        aislamientoAnalisis = null
        sandbox = null
    }

    override suspend fun evaluar(script: String, limiteMs: Long, conAnalizador: Boolean): ResultadoMotor = exclusion.withLock {
        if (!soportado) return@withLock ResultadoMotor.NoDisponible("JavaScriptSandbox no está disponible en este dispositivo")
        var aislamiento: JavaScriptIsolate? = null
        try {
            aislamiento = if (conAnalizador) obtenerAnalisis() else obtenerSandbox().createIsolate()
            val actual = aislamiento
            val resultado = withTimeout(limiteMs) { actual.evaluateJavaScriptAsync(script).await() }
            ResultadoMotor.Exito(resultado)
        } catch (e: TimeoutCancellationException) {
            reiniciar()
            ResultadoMotor.TiempoAgotado
        } catch (e: EvaluationFailedException) {
            val mensaje = e.message ?: "Error de evaluación"
            ResultadoMotor.Fallo(mensaje.substringBefore(':').trim().ifEmpty { "Error" }, mensaje.substringAfter(':').trim(), 0)
        } catch (e: Exception) {
            reiniciar()
            ResultadoMotor.NoDisponible(e.message ?: "El motor JavaScript se detuvo")
        } finally {
            if (!conAnalizador) runCatching { aislamiento?.close() }
        }
    }

    override fun cerrar() = reiniciar()
}

class MotorJavaScriptCompuesto(
    private val principal: MotorJavaScriptSandbox,
    private val respaldo: MotorRhino
) : MotorJavaScript {
    override val nombre: String get() = if (principal.soportado) principal.nombre else "${respaldo.nombre} (respaldo, mismo proceso)"
    override val procesoAislado: Boolean get() = principal.soportado

    override suspend fun evaluar(script: String, limiteMs: Long, conAnalizador: Boolean): ResultadoMotor {
        if (principal.soportado) {
            val resultado = principal.evaluar(script, limiteMs, conAnalizador)
            if (resultado !is ResultadoMotor.NoDisponible) return resultado
        }
        val respaldoResultado = respaldo.evaluar(script, limiteMs, conAnalizador)
        if (!conAnalizador && respaldoResultado is ResultadoMotor.Fallo && respaldoResultado.nombre == "SyntaxError") {
            return respaldoResultado.copy(
                mensaje = "${respaldoResultado.mensaje}. El motor de respaldo Rhino no admite parte de la sintaxis moderna (por ejemplo class, async o for...of con const). " +
                    "Tu dispositivo no ofrece JavaScriptSandbox; usa la vista previa web para ejecutar este código."
            )
        }
        return respaldoResultado
    }

    override fun cerrar() {
        principal.cerrar()
        respaldo.cerrar()
    }
}
