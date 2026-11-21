package pe.redactcode.nucleo.javascript

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
import pe.redactcode.nucleo.retos.CasoPrueba
import pe.redactcode.nucleo.retos.EjecutorPruebas
import pe.redactcode.nucleo.retos.InterpreteArnes
import pe.redactcode.nucleo.retos.ResultadoPruebas
import pe.redactcode.nucleo.revision.FuenteHallazgos
import pe.redactcode.nucleo.revision.HallazgoCrudo
import pe.redactcode.nucleo.validacion.CatalogoExplicaciones
import pe.redactcode.nucleo.validacion.ClasificadorMensajes
import pe.redactcode.nucleo.validacion.Validador

@Serializable
private data class ErrorAcorn(val linea: Int, val columna: Int, val mensaje: String, val alFinal: Boolean = false)

@Serializable
private data class RespuestaEjecucion(
    val ok: Boolean,
    val salida: List<String> = emptyList(),
    val nombre: String? = null,
    val mensaje: String? = null,
    val linea: Int = 0
)

object ArnesJavaScript {
    const val LINEAS_PREFIJO = 1

    private val prefijoConsola =
        "var __salida=[];var __texto=function(v){try{return typeof v==='string'?v:(typeof v==='function'?'[función]':JSON.stringify(v));}catch(e){return String(v);}};" +
            "var console={log:function(){__salida.push(Array.prototype.map.call(arguments,__texto).join(' '));},error:function(){__salida.push('[error] '+Array.prototype.map.call(arguments,__texto).join(' '));}," +
            "warn:function(){__salida.push('[aviso] '+Array.prototype.map.call(arguments,__texto).join(' '));},info:function(){__salida.push(Array.prototype.map.call(arguments,__texto).join(' '));}};" +
            "var __linea=function(e){if(e&&typeof e.lineNumber==='number')return e.lineNumber;var r=/(?:redact\\.js|<anonymous>|eval)[^:]*:(\\d+)/.exec(String(e&&e.stack||''));return r?Number(r[1]):0;};"

    fun literal(texto: String): String = JsonPrimitive(texto).toString()

    fun programaEjecucion(codigo: String): String =
        "(function(){$prefijoConsola try{(function(){\n$codigo\n})();return JSON.stringify({ok:true,salida:__salida});}" +
            "catch(e){return JSON.stringify({ok:false,salida:__salida,nombre:String(e&&e.name||'Error'),mensaje:String(e&&e.message||e),linea:Math.max(0,__linea(e)-$LINEAS_PREFIJO)});}})()"

    fun programaPruebas(codigo: String, funcion: String, casos: List<CasoPrueba>): String {
        val entradas = JsonArray(casos.map { JsonArray(it.entrada) }).toString()
        return "(function(){$prefijoConsola var __casos=$entradas;var __resultados=[];try{\n$codigo\n;" +
            "if(typeof $funcion!=='function'){return JSON.stringify({error:'No se encontró la función $funcion',salida:__salida});}" +
            "for(var __i=0;__i<__casos.length;__i++){try{var __r=$funcion.apply(null,__casos[__i]);" +
            "__resultados.push({ok:true,valor:JSON.stringify(__r===undefined?null:__r)});}" +
            "catch(e){__resultados.push({ok:false,error:String(e&&e.name||'Error')+': '+String(e&&e.message||e)});}}" +
            "return JSON.stringify({resultados:__resultados,salida:__salida});}" +
            "catch(e){return JSON.stringify({error:String(e&&e.name||'Error')+': '+String(e&&e.message||e),linea:Math.max(0,__linea(e)-$LINEAS_PREFIJO),salida:__salida});}})()"
    }
}

class ServicioJavaScript(
    private val motorAnalisis: MotorJavaScript,
    private val motorEjecucion: MotorJavaScript = motorAnalisis
) : Validador, EjecutorPruebas {
    override val lenguaje = Lenguaje.JAVASCRIPT
    private val json = Json { ignoreUnknownKeys = true }

    val nombreMotorEjecucion: String get() = motorEjecucion.nombre
    val ejecucionAislada: Boolean get() = motorEjecucion.procesoAislado

    override suspend fun validar(codigo: String): ResultadoValidacion {
        val inicio = System.nanoTime()
        val diagnosticos = mutableListOf<Diagnostico>()
        when (val r = motorAnalisis.evaluar("RedactAnalisis.validar(${ArnesJavaScript.literal(codigo)})", 15_000, conAnalizador = true)) {
            is ResultadoMotor.Exito -> json.decodeFromString<List<ErrorAcorn>>(r.texto).forEach { e ->
                diagnosticos += CatalogoExplicaciones.diagnostico(lenguaje, e.linea, e.columna, ClasificadorMensajes.javascript(e.mensaje, e.alFinal), e.mensaje)
            }
            else -> Unit
        }
        return ResultadoValidacion(lenguaje, diagnosticos, (System.nanoTime() - inicio) / 1_000_000, "Acorn sobre ${motorAnalisis.nombre}")
    }

    val fuenteHallazgos = FuenteHallazgos { codigo ->
        when (val r = motorAnalisis.evaluar("RedactAnalisis.revisar(${ArnesJavaScript.literal(codigo)})", 15_000, conAnalizador = true)) {
            is ResultadoMotor.Exito -> json.decodeFromString<List<HallazgoCrudo>>(r.texto)
            else -> emptyList()
        }
    }

    suspend fun estructura(codigo: String): EstructuraCodigo =
        when (val r = motorAnalisis.evaluar("RedactAnalisis.explicarLineas(${ArnesJavaScript.literal(codigo)})", 15_000, conAnalizador = true)) {
            is ResultadoMotor.Exito -> json.decodeFromString<EstructuraCodigo>(r.texto)
            is ResultadoMotor.Fallo -> EstructuraCodigo(r.mensaje)
            ResultadoMotor.TiempoAgotado -> EstructuraCodigo("El análisis tardó demasiado")
            is ResultadoMotor.NoDisponible -> EstructuraCodigo(r.motivo)
        }

    suspend fun ejecutar(codigo: String, limiteMs: Long = 5_000): ResultadoEjecucion {
        val inicio = System.currentTimeMillis()
        val r = motorEjecucion.evaluar(ArnesJavaScript.programaEjecucion(codigo), limiteMs, conAnalizador = false)
        val duracion = System.currentTimeMillis() - inicio
        return when (r) {
            is ResultadoMotor.Exito -> {
                val respuesta = json.decodeFromString<RespuestaEjecucion>(r.texto)
                if (respuesta.ok) {
                    ResultadoEjecucion(EstadoEjecucion.EXITO, respuesta.salida.joinToString("\n"), duracionMs = duracion)
                } else {
                    ResultadoEjecucion(
                        EstadoEjecucion.ERROR, respuesta.salida.joinToString("\n"), "${respuesta.nombre}: ${respuesta.mensaje}",
                        respuesta.nombre, respuesta.linea.takeIf { it > 0 }, duracion
                    )
                }
            }
            is ResultadoMotor.Fallo -> ResultadoEjecucion(EstadoEjecucion.ERROR, "", "${r.nombre}: ${r.mensaje}", r.nombre, (r.linea - ArnesJavaScript.LINEAS_PREFIJO).takeIf { it > 0 }, duracion)
            ResultadoMotor.TiempoAgotado -> ResultadoEjecucion(EstadoEjecucion.TIEMPO_AGOTADO, "", "La ejecución superó ${limiteMs / 1000} s y fue detenida", "TiempoAgotado", null, duracion)
            is ResultadoMotor.NoDisponible -> ResultadoEjecucion(EstadoEjecucion.NO_DISPONIBLE, "", r.motivo, duracionMs = duracion)
        }
    }

    override suspend fun ejecutar(codigo: String, funcion: String, casos: List<CasoPrueba>, limiteMs: Long): ResultadoPruebas {
        val inicio = System.currentTimeMillis()
        val r = motorEjecucion.evaluar(ArnesJavaScript.programaPruebas(codigo, funcion, casos), limiteMs, conAnalizador = false)
        val duracion = System.currentTimeMillis() - inicio
        return when (r) {
            is ResultadoMotor.Exito -> InterpreteArnes.interpretar(r.texto, casos, duracion)
            is ResultadoMotor.Fallo -> ResultadoPruebas(emptyList(), "${r.nombre}: ${r.mensaje}", (r.linea - ArnesJavaScript.LINEAS_PREFIJO).takeIf { it > 0 }, "", duracion)
            ResultadoMotor.TiempoAgotado -> ResultadoPruebas(emptyList(), "Tiempo agotado: revisa si hay un bucle infinito", null, "", duracion, tiempoAgotado = true)
            is ResultadoMotor.NoDisponible -> ResultadoPruebas(emptyList(), r.motivo, null, "", duracion)
        }
    }
}
