package pe.redactcode.nucleo.agentes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pe.redactcode.nucleo.terminal.ClasificadorRiesgo
import pe.redactcode.nucleo.terminal.EvaluacionRiesgo
import pe.redactcode.nucleo.terminal.NivelRiesgo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pe.redactcode.nucleo.ide.Diferencias
import pe.redactcode.nucleo.ide.ResultadoDiferencias
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class AccionCambio(val nombreVisible: String) { CREAR("Crear"), MODIFICAR("Modificar"), ELIMINAR("Eliminar") }

data class PermisosAgente(
    val leerProyecto: Boolean = true,
    val crearArchivos: Boolean = true,
    val modificarArchivos: Boolean = true,
    val eliminarArchivos: Boolean = false,
    val maxArchivos: Int = 40,
    val maxCaracteres: Int = 120_000,
    val ejecutarComandos: Boolean = false
)

data class MensajeAgente(val rol: String, val texto: String) {
    companion object {
        const val USUARIO = "user"
        const val ASISTENTE = "assistant"
    }
}

interface ProveedorAgente {
    val nombre: String
    suspend fun completar(sistema: String, mensajes: List<MensajeAgente>): String
}

class ErrorAgente(mensaje: String) : Exception(mensaje)

data class CambioPropuesto(
    val ruta: String,
    val accion: AccionCambio,
    val anterior: String?,
    val nuevo: String?,
    val diferencias: ResultadoDiferencias,
    val motivo: String
)

data class ComandoPropuesto(val comando: String, val motivo: String, val riesgo: EvaluacionRiesgo)

data class PropuestaAgente(
    val resumen: String,
    val cambios: List<CambioPropuesto>,
    val rechazados: List<String>,
    val textoOriginal: String,
    val comandos: List<ComandoPropuesto> = emptyList()
)

data class ContextoAgente(val texto: String, val incluidos: List<String>, val omitidos: List<String>)

object AgenteCodigo {
    const val MAXIMO_COMANDOS = 5
    const val MAXIMO_LARGO_COMANDO = 500
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun sistema(p: PermisosAgente): String = buildString {
        appendLine("Eres el agente de programación de Redact Code, un IDE para Android. Respondes en español de Perú.")
        appendLine("Trabajas sobre los archivos del proyecto que se te entregan. Propones cambios; el usuario revisa cada diferencia y decide si la acepta.")
        appendLine("Responde SIEMPRE con un único objeto JSON, sin texto antes ni después, con esta forma:")
        appendLine("{\"resumen\": \"explicación breve\", \"cambios\": [{\"ruta\": \"carpeta/archivo.ext\", \"accion\": \"crear|modificar|eliminar\", \"contenido\": \"contenido COMPLETO del archivo\", \"motivo\": \"por qué\"}]}")
        appendLine("Reglas:")
        appendLine("- Usa rutas relativas al proyecto, sin '..' ni rutas absolutas.")
        appendLine("- En 'modificar' y 'crear' entrega el contenido completo final del archivo, no fragmentos.")
        appendLine("- Si no hace falta cambiar archivos, devuelve \"cambios\": [] y responde en \"resumen\".")
        if (!p.crearArchivos) appendLine("- No tienes permiso para crear archivos.")
        if (!p.modificarArchivos) appendLine("- No tienes permiso para modificar archivos existentes.")
        if (!p.eliminarArchivos) appendLine("- No tienes permiso para eliminar archivos.")
        if (!p.leerProyecto) appendLine("- Solo conoces el archivo activo; no supongas el contenido de otros archivos.")
        if (p.ejecutarComandos) {
            appendLine("Además puedes proponer comandos de terminal con el campo opcional \"comandos\": [{\"comando\": \"python main.py\", \"motivo\": \"por qué\"}].")
            appendLine("- Los comandos NO se ejecutan solos: la usuaria revisa y aprueba cada uno. Se ejecutan sin entrada interactiva y con tiempo límite.")
            appendLine("- Disponibles: python, node, git (init, status, add, commit, log, diff, branch, checkout), pip (install, list), ls, cat, grep, mkdir, echo.")
            appendLine("- No propongas comandos destructivos, de red arbitraria, con sudo, ni que salgan de la carpeta del proyecto. Máximo 5 comandos.")
        } else {
            appendLine("- No tienes permiso para proponer comandos de terminal.")
        }
    }

    fun contexto(archivos: Map<String, String>, prioridad: List<String>, p: PermisosAgente): ContextoAgente {
        val orden = (prioridad.filter { it in archivos } + archivos.keys.sorted().filter { it !in prioridad })
            .let { if (p.leerProyecto) it else it.take(1) }
        val incluidos = mutableListOf<String>()
        val omitidos = mutableListOf<String>()
        var total = 0
        val sb = StringBuilder()
        sb.appendLine("Archivos del proyecto:")
        archivos.keys.sorted().forEach { sb.append("- ").appendLine(it) }
        sb.appendLine()
        for (ruta in orden) {
            val contenido = archivos.getValue(ruta)
            if (incluidos.size >= p.maxArchivos || total + contenido.length > p.maxCaracteres) { omitidos += ruta; continue }
            sb.append("<<<ARCHIVO ").append(ruta).appendLine(">>>")
            sb.appendLine(contenido)
            sb.appendLine("<<<FIN>>>")
            total += contenido.length
            incluidos += ruta
        }
        archivos.keys.filter { it !in orden }.forEach { omitidos += it }
        return ContextoAgente(sb.toString(), incluidos, omitidos)
    }

    fun extraerJson(texto: String): JsonObject? {
        val bloque = Regex("```(?:json)?\\s*\\n([\\s\\S]*?)\\n```").find(texto)?.groupValues?.get(1)
        val candidatos = listOfNotNull(bloque, texto.trim())
        for (c in candidatos) {
            runCatching { return json.parseToJsonElement(c) as JsonObject }
            val inicio = c.indexOf('{')
            if (inicio < 0) continue
            var nivel = 0
            var enCadena = false
            var i = inicio
            while (i < c.length) {
                val ch = c[i]
                if (enCadena) {
                    if (ch == '\\') i++ else if (ch == '"') enCadena = false
                } else when (ch) {
                    '"' -> enCadena = true
                    '{' -> nivel++
                    '}' -> { nivel--; if (nivel == 0) { runCatching { return json.parseToJsonElement(c.substring(inicio, i + 1)) as JsonObject }; break } }
                }
                i++
            }
        }
        return null
    }

    fun interpretar(respuesta: String, actuales: Map<String, String>, p: PermisosAgente, validarRuta: (String) -> String?): PropuestaAgente {
        val obj = extraerJson(respuesta) ?: return PropuestaAgente(respuesta.trim(), emptyList(), listOf("La respuesta no tenía el formato JSON esperado; se muestra como texto."), respuesta)
        val resumen = (obj["resumen"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val cambios = mutableListOf<CambioPropuesto>()
        val rechazados = mutableListOf<String>()
        ((obj["cambios"] as? JsonArray) ?: JsonArray(emptyList())).forEach { e ->
            val c = e as? JsonObject ?: return@forEach
            val ruta = (c["ruta"] as? JsonPrimitive)?.contentOrNull?.trim()?.trim('/')?.replace('\\', '/') ?: run { rechazados += "Cambio sin ruta"; return@forEach }
            val accionTexto = (c["accion"] as? JsonPrimitive)?.contentOrNull?.lowercase().orEmpty()
            val contenido = (c["contenido"] as? JsonPrimitive)?.contentOrNull
            val motivo = (c["motivo"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val existe = ruta in actuales
            val accion = when (accionTexto) {
                "eliminar", "delete" -> AccionCambio.ELIMINAR
                "crear", "create" -> if (existe) AccionCambio.MODIFICAR else AccionCambio.CREAR
                else -> if (existe) AccionCambio.MODIFICAR else AccionCambio.CREAR
            }
            if (ruta.split('/').any { it == ".." || it.isEmpty() || it.startsWith(".") }) { rechazados += "$ruta: ruta no permitida"; return@forEach }
            validarRuta(ruta)?.let { rechazados += "$ruta: $it"; return@forEach }
            if (cambios.any { it.ruta == ruta }) { rechazados += "$ruta: cambio duplicado"; return@forEach }
            when (accion) {
                AccionCambio.CREAR -> if (!p.crearArchivos) { rechazados += "$ruta: sin permiso para crear archivos"; return@forEach }
                AccionCambio.MODIFICAR -> if (!p.modificarArchivos) { rechazados += "$ruta: sin permiso para modificar archivos"; return@forEach }
                AccionCambio.ELIMINAR -> {
                    if (!p.eliminarArchivos) { rechazados += "$ruta: sin permiso para eliminar archivos"; return@forEach }
                    if (!existe) { rechazados += "$ruta: no existe"; return@forEach }
                }
            }
            if (accion != AccionCambio.ELIMINAR && contenido == null) { rechazados += "$ruta: falta el contenido"; return@forEach }
            val anterior = actuales[ruta]
            val nuevo = if (accion == AccionCambio.ELIMINAR) null else contenido
            if (accion == AccionCambio.MODIFICAR && anterior == nuevo) return@forEach
            cambios += CambioPropuesto(ruta, accion, anterior, nuevo, Diferencias.comparar(anterior.orEmpty(), nuevo.orEmpty()), motivo)
        }
        val comandos = mutableListOf<ComandoPropuesto>()
        ((obj["comandos"] as? JsonArray) ?: JsonArray(emptyList())).forEach { e ->
            val c = e as? JsonObject ?: return@forEach
            val linea = (c["comando"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val motivo = (c["motivo"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (linea.isEmpty()) return@forEach
            if (!p.ejecutarComandos) { rechazados += "Comando '${linea.take(60)}': sin permiso para proponer comandos"; return@forEach }
            if (linea.length > MAXIMO_LARGO_COMANDO || linea.contains('\n')) { rechazados += "Comando rechazado: demasiado largo o con varias líneas"; return@forEach }
            if (comandos.size >= MAXIMO_COMANDOS) { rechazados += "Comando '${linea.take(60)}': se superó el máximo de $MAXIMO_COMANDOS"; return@forEach }
            val riesgo = ClasificadorRiesgo.clasificar(linea)
            if (riesgo.nivel == NivelRiesgo.BLOQUEADO) { rechazados += "Comando bloqueado '${linea.take(60)}': ${riesgo.motivo}"; return@forEach }
            comandos += ComandoPropuesto(linea, motivo, riesgo)
        }
        return PropuestaAgente(resumen.ifBlank { if (cambios.isEmpty() && comandos.isEmpty()) "Sin cambios propuestos." else "Cambios propuestos." }, cambios, rechazados, respuesta, comandos)
    }
}

class ProveedorCompatibleOpenAI(
    override val nombre: String,
    private val urlBase: String,
    private val clave: () -> String?,
    private val modelo: String,
    clienteBase: OkHttpClient? = null
) : ProveedorAgente {
    private val json = Json { ignoreUnknownKeys = true }
    private val cliente = (clienteBase?.newBuilder() ?: OkHttpClient.Builder())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    companion object {
        fun validarUrl(url: String): String? = when {
            !url.startsWith("https://") && !Regex("^http://(localhost|127\\.0\\.0\\.1|10\\.0\\.2\\.2)(:\\d+)?(/.*)?$").matches(url) ->
                "Usa https://. Solo se admite http:// para localhost, 127.0.0.1 o 10.0.2.2 (emulador)."
            else -> null
        }
    }

    override suspend fun completar(sistema: String, mensajes: List<MensajeAgente>): String = withContext(Dispatchers.IO) {
        validarUrl(urlBase)?.let { throw ErrorAgente(it) }
        val cuerpo = buildJsonObject {
            put("model", modelo)
            put("temperature", 0.2)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", sistema) }
                mensajes.forEach { m -> addJsonObject { put("role", m.rol); put("content", m.texto) } }
            }
        }
        val peticion = Request.Builder()
            .url(urlBase.trimEnd('/') + "/chat/completions")
            .post(cuerpo.toString().toRequestBody("application/json".toMediaType()))
            .apply { clave()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .build()
        val respuesta = try { cliente.newCall(peticion).execute() } catch (e: IOException) { throw ErrorAgente("No hay conexión con el proveedor: ${e.message}") }
        respuesta.use { r ->
            val texto = r.body.string()
            if (!r.isSuccessful) {
                val detalle = runCatching { json.parseToJsonElement(texto).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
                throw ErrorAgente(when (r.code) {
                    401, 403 -> "La clave no es válida o no tiene permiso"
                    404 -> "No se encontró el modelo o la dirección del servicio"
                    429 -> "Se alcanzó el límite de solicitudes"
                    else -> "El proveedor respondió con el código ${r.code}"
                } + (detalle?.let { ": $it" } ?: ""))
            }
            val contenido = runCatching {
                json.parseToJsonElement(texto).jsonObject["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
            }.getOrElse { throw ErrorAgente("Respuesta del proveedor no reconocida") }
            contenido.ifBlank { throw ErrorAgente("El proveedor no devolvió texto") }
        }
    }
}
