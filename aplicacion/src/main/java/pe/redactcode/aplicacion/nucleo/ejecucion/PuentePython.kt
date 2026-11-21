package pe.redactcode.aplicacion.nucleo.ejecucion

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import pe.redactcode.nucleo.mentor.EstructuraCodigo
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.EstadoEjecucion
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.ResultadoEjecucion
import pe.redactcode.nucleo.modelo.ResultadoValidacion
import pe.redactcode.nucleo.modelo.Severidad
import pe.redactcode.nucleo.retos.CasoPrueba
import pe.redactcode.nucleo.retos.EjecutorPruebas
import pe.redactcode.nucleo.retos.InterpreteArnes
import pe.redactcode.nucleo.retos.ResultadoPruebas
import pe.redactcode.nucleo.revision.FuenteHallazgos
import pe.redactcode.nucleo.revision.HallazgoCrudo
import pe.redactcode.nucleo.validacion.CatalogoExplicaciones
import pe.redactcode.nucleo.validacion.ClasificadorMensajes
import pe.redactcode.nucleo.validacion.Validador
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ErrorPython(
    val tipo: String,
    val mensaje: String,
    val linea: Int = 1,
    val columna: Int = 1,
    val columna_fin: Int? = null,
    val severidad: String = "ERROR"
)

@Serializable
private data class RespuestaEjecucionPython(
    val estado: String,
    val salida: String = "",
    val errores: String = "",
    val excepcion: String? = null,
    val linea: Int? = null,
    val mensaje: String? = null,
    val duracion_ms: Long = 0
)

private fun hiloPython(nombre: String): CoroutineDispatcher = Executors.newSingleThreadExecutor { tarea ->
    Thread(null, tarea, nombre, 32L * 1024 * 1024).apply { isDaemon = true }
}.asCoroutineDispatcher()

@Singleton
class PuentePython @Inject constructor(@ApplicationContext private val contexto: Context) : Validador, EjecutorPruebas {
    override val lenguaje = Lenguaje.PYTHON
    private val json = Json { ignoreUnknownKeys = true }
    private val hiloAnalisis = hiloPython("redact-python-analisis")
    private val hiloEjecucion = hiloPython("redact-python-ejecucion")

    @Volatile
    private var modulo: PyObject? = null

    private fun modulo(): PyObject {
        modulo?.let { return it }
        synchronized(this) {
            modulo?.let { return it }
            if (!Python.isStarted()) Python.start(AndroidPlatform(contexto))
            return Python.getInstance().getModule("redact_python").also { modulo = it }
        }
    }

    @Volatile
    var rutaPaquetes: String? = null

    fun carpetaEntorno(proyectoId: Long): java.io.File = java.io.File(contexto.filesDir, "entornos/$proyectoId/python/site-packages")

    fun usarEntorno(proyectoId: Long) {
        rutaPaquetes = carpetaEntorno(proyectoId).takeIf { it.isDirectory }?.absolutePath
    }

    private fun prepararRuta() {
        modulo().callAttr("agregar_ruta_paquetes", rutaPaquetes.orEmpty())
    }

    suspend fun version(): String = withContext(hiloAnalisis) { modulo().callAttr("version").toString() }

    override suspend fun validar(codigo: String): ResultadoValidacion = withContext(hiloAnalisis) {
        val inicio = System.nanoTime()
        val errores = json.decodeFromString<List<ErrorPython>>(modulo().callAttr("validar", codigo).toString())
        val diagnosticos: List<Diagnostico> = errores.map { e ->
            val advertencia = e.severidad == "ADVERTENCIA"
            val codigoError = if (advertencia) "PYW001" else ClasificadorMensajes.python(e.tipo, e.mensaje)
            val longitud = e.columna_fin?.let { (it - e.columna).coerceAtLeast(1) } ?: 1
            CatalogoExplicaciones.diagnostico(
                Lenguaje.PYTHON, e.linea, e.columna, codigoError, "${e.tipo}: ${e.mensaje}",
                if (advertencia) Severidad.ADVERTENCIA else Severidad.ERROR, longitud
            )
        }
        ResultadoValidacion(Lenguaje.PYTHON, diagnosticos, (System.nanoTime() - inicio) / 1_000_000, "CPython (compile) vía Chaquopy")
    }

    suspend fun ejecutar(codigo: String, entrada: String, limiteSegundos: Int): ResultadoEjecucion = withContext(hiloEjecucion) {
        prepararRuta()
        val r = json.decodeFromString<RespuestaEjecucionPython>(modulo().callAttr("ejecutar", codigo, entrada, limiteSegundos).toString())
        ResultadoEjecucion(
            estado = runCatching { EstadoEjecucion.valueOf(r.estado) }.getOrDefault(EstadoEjecucion.ERROR),
            salida = r.salida,
            errores = r.errores,
            excepcion = r.excepcion,
            lineaExcepcion = r.linea,
            duracionMs = r.duracion_ms
        )
    }

    suspend fun consola(codigo: String, limiteSegundos: Int): ResultadoEjecucion = withContext(hiloEjecucion) {
        prepararRuta()
        val r = json.decodeFromString<RespuestaEjecucionPython>(modulo().callAttr("consola", codigo, limiteSegundos).toString())
        ResultadoEjecucion(
            estado = runCatching { EstadoEjecucion.valueOf(r.estado) }.getOrDefault(EstadoEjecucion.ERROR),
            salida = r.salida,
            errores = r.errores,
            excepcion = r.excepcion,
            lineaExcepcion = r.linea,
            duracionMs = r.duracion_ms
        )
    }

    private fun moduloSesion(): PyObject {
        modulo()
        return Python.getInstance().getModule("redact_sesion")
    }

    suspend fun iniciarSesion(codigo: String, depurar: Boolean, puntos: List<Int>, limiteSegundos: Int): Boolean = withContext(Dispatchers.IO) {
        prepararRuta()
        moduloSesion().callAttr("iniciar", codigo, depurar, JsonArray(puntos.map { JsonPrimitive(it) }).toString(), limiteSegundos).toString() == "ok"
    }

    suspend fun siguienteEvento(esperaMs: Int): String = withContext(Dispatchers.IO) {
        moduloSesion().callAttr("siguiente_evento", esperaMs).toString()
    }

    fun enviarEntrada(texto: String) { moduloSesion().callAttr("enviar_entrada", texto) }

    fun comandoDepurador(nombre: String, argumento: String = "") { moduloSesion().callAttr("comando_depurador", nombre, argumento) }

    fun detenerSesion() { moduloSesion().callAttr("detener") }

    suspend fun reiniciarConsola() = withContext(hiloEjecucion) { modulo().callAttr("reiniciar_consola") }

    fun cancelar() {
        modulo?.callAttr("cancelar")
    }

    override suspend fun ejecutar(codigo: String, funcion: String, casos: List<CasoPrueba>, limiteMs: Long): ResultadoPruebas = withContext(hiloEjecucion) {
        val inicio = System.currentTimeMillis()
        val entradas = JsonArray(casos.map { JsonArray(it.entrada) }).toString()
        val respuesta = modulo().callAttr("evaluar_pruebas", codigo, funcion, entradas, (limiteMs / 1000).coerceAtLeast(1)).toString()
        val resultado = InterpreteArnes.interpretar(respuesta, casos, System.currentTimeMillis() - inicio)
        if (respuesta.contains("\"tiempo_agotado\": true")) resultado.copy(tiempoAgotado = true) else resultado
    }

    val fuenteHallazgos = FuenteHallazgos { codigo ->
        withContext(hiloAnalisis) { json.decodeFromString<List<HallazgoCrudo>>(modulo().callAttr("revisar", codigo).toString()) }
    }

    suspend fun estructura(codigo: String): EstructuraCodigo = withContext(hiloAnalisis) {
        json.decodeFromString<EstructuraCodigo>(modulo().callAttr("estructura", codigo).toString())
    }
}
