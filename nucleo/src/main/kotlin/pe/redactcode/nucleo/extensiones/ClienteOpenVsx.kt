package pe.redactcode.nucleo.extensiones

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class ArchivosOpenVsx(val download: String? = null)

@Serializable
data class ExtensionOpenVsx(
    val namespace: String,
    val name: String,
    val version: String = "",
    val displayName: String? = null,
    val description: String? = null,
    val downloadCount: Long = 0,
    val license: String? = null,
    val files: ArchivosOpenVsx = ArchivosOpenVsx()
) {
    val titulo: String get() = displayName?.takeIf { it.isNotBlank() } ?: name
    val identificador: String get() = "$namespace.$name"
}

@Serializable
data class RespuestaBusquedaOpenVsx(val totalSize: Int = 0, val extensions: List<ExtensionOpenVsx> = emptyList())

class ClienteOpenVsx(private val urlBase: String = "https://open-vsx.org", clienteBase: OkHttpClient? = null) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cliente = (clienteBase?.newBuilder() ?: OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun buscar(consulta: String, categoria: String? = null, cantidad: Int = 20): List<ExtensionOpenVsx> = withContext(Dispatchers.IO) {
        val url = urlBase.toHttpUrl().newBuilder()
            .addPathSegments("api/-/search")
            .addQueryParameter("query", consulta.trim().take(80))
            .addQueryParameter("size", cantidad.coerceIn(1, 50).toString())
            .addQueryParameter("sortBy", "downloadCount")
            .apply { categoria?.let { addQueryParameter("category", it) } }
            .build()
        val texto = pedir(Request.Builder().url(url).header("Accept", "application/json").build(), 2L * 1024 * 1024).toString(Charsets.UTF_8)
        runCatching { json.decodeFromString<RespuestaBusquedaOpenVsx>(texto).extensions }.getOrElse { throw ErrorExtension("Respuesta del catálogo no reconocida") }
    }

    suspend fun descargar(extension: ExtensionOpenVsx): ByteArray = withContext(Dispatchers.IO) {
        val destino = extension.files.download?.toHttpUrl() ?: throw ErrorExtension("La extensión no ofrece un paquete descargable")
        val base = urlBase.toHttpUrl()
        if (destino.scheme != base.scheme || destino.host != base.host) throw ErrorExtension("La descarga apunta a un servidor distinto del catálogo y fue bloqueada")
        pedir(Request.Builder().url(destino).build(), LectorExtensiones.TAMANO_MAXIMO_PAQUETE)
    }

    private fun pedir(peticion: Request, limite: Long): ByteArray {
        val respuesta = try { cliente.newCall(peticion).execute() } catch (e: IOException) { throw ErrorExtension("No hay conexión con el catálogo de extensiones") }
        respuesta.use { r ->
            if (!r.isSuccessful) throw ErrorExtension("El catálogo respondió con el código ${r.code}")
            val longitud = r.body.contentLength()
            if (longitud > limite) throw ErrorExtension("El paquete supera el tamaño máximo permitido")
            val datos = LectorExtensiones.leerLimitado(r.body.byteStream(), limite + 1)
            if (datos.size > limite) throw ErrorExtension("El paquete supera el tamaño máximo permitido")
            return datos
        }
    }
}
