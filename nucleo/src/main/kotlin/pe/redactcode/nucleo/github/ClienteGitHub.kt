package pe.redactcode.nucleo.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

@Serializable
data class UsuarioGitHub(val login: String, val name: String? = null, @SerialName("avatar_url") val avatarUrl: String? = null)

@Serializable
data class DuenoRepositorio(val login: String)

@Serializable
data class RepositorioGitHub(
    val id: Long,
    val name: String,
    @SerialName("full_name") val nombreCompleto: String,
    val private: Boolean = false,
    val description: String? = null,
    @SerialName("default_branch") val ramaPrincipal: String = "main",
    val owner: DuenoRepositorio,
    @SerialName("updated_at") val actualizado: String? = null,
    val permissions: PermisosRepositorio? = null
)

@Serializable
data class PermisosRepositorio(val push: Boolean = false, val pull: Boolean = true)

@Serializable
data class CommitReferencia(val sha: String)

@Serializable
data class RamaGitHub(val name: String, val commit: CommitReferencia, val protected: Boolean = false)

@Serializable
data class EntradaContenido(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0,
    val type: String,
    val content: String? = null,
    val encoding: String? = null
) {
    val esCarpeta: Boolean get() = type == "dir"
    val esArchivo: Boolean get() = type == "file"
}

@Serializable
data class SolicitudPublicacion(
    val message: String,
    val content: String,
    val branch: String,
    val sha: String? = null
)

@Serializable
data class CommitPublicado(val sha: String, @SerialName("html_url") val url: String? = null)

@Serializable
data class RespuestaPublicacion(val content: EntradaContenido? = null, val commit: CommitPublicado)

@Serializable
data class PullRequestGitHub(
    val number: Int,
    val title: String,
    val state: String,
    val user: DuenoRepositorio,
    @SerialName("html_url") val url: String
)

@Serializable
data class ArchivoPullRequest(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val patch: String? = null
)

interface ApiGitHub {
    @GET("user")
    suspend fun usuario(): UsuarioGitHub

    @GET("user/repos")
    suspend fun repositorios(
        @Query("per_page") porPagina: Int = 100,
        @Query("sort") orden: String = "updated",
        @Query("page") pagina: Int = 1
    ): List<RepositorioGitHub>

    @GET("repos/{dueno}/{repositorio}/branches")
    suspend fun ramas(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Query("per_page") porPagina: Int = 100): List<RamaGitHub>

    @GET("repos/{dueno}/{repositorio}/contents")
    suspend fun contenidoRaiz(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Query("ref") ref: String): JsonElement

    @GET("repos/{dueno}/{repositorio}/contents/{ruta}")
    suspend fun contenido(
        @Path("dueno") dueno: String,
        @Path("repositorio") repositorio: String,
        @Path(value = "ruta", encoded = true) ruta: String,
        @Query("ref") ref: String
    ): JsonElement

    @PUT("repos/{dueno}/{repositorio}/contents/{ruta}")
    suspend fun publicar(
        @Path("dueno") dueno: String,
        @Path("repositorio") repositorio: String,
        @Path(value = "ruta", encoded = true) ruta: String,
        @Body solicitud: SolicitudPublicacion
    ): RespuestaPublicacion

    @GET("repos/{dueno}/{repositorio}/pulls")
    suspend fun pullRequests(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Query("state") estado: String = "open"): List<PullRequestGitHub>

    @GET("repos/{dueno}/{repositorio}/pulls/{numero}/files")
    suspend fun archivosPullRequest(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Path("numero") numero: Int): List<ArchivoPullRequest>
}

class ErrorGitHub(mensaje: String, val codigoHttp: Int? = null) : Exception(mensaje)

class ClienteGitHub(
    proveedorToken: () -> String?,
    urlBase: String = "https://api.github.com/",
    clienteBase: OkHttpClient? = null
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    private val api: ApiGitHub = run {
        val interceptor = Interceptor { cadena ->
            val peticion = cadena.request().newBuilder()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "RedactCode-Android")
                .apply { proveedorToken()?.let { header("Authorization", "Bearer $it") } }
                .build()
            cadena.proceed(peticion)
        }
        val cliente = (clienteBase?.newBuilder() ?: OkHttpClient.Builder())
            .addInterceptor(interceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(urlBase)
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiGitHub::class.java)
    }

    private suspend fun <T> llamar(bloque: suspend ApiGitHub.() -> T): T = try {
        api.bloque()
    } catch (e: HttpException) {
        val mensaje = when (e.code()) {
            401 -> "El token no es válido o expiró. Vuelve a conectar tu cuenta."
            403 -> "GitHub rechazó la operación: el token no tiene permisos suficientes o se alcanzó el límite de solicitudes."
            404 -> "No se encontró el recurso. Verifica el repositorio, la rama o los permisos del token."
            409 -> "Conflicto: el archivo cambió en GitHub. Descarga la versión actual antes de publicar."
            422 -> "GitHub no aceptó los datos enviados. Verifica la rama y el contenido."
            else -> "GitHub respondió con el código ${e.code()}."
        }
        throw ErrorGitHub(mensaje, e.code())
    } catch (e: IOException) {
        throw ErrorGitHub("No hay conexión con GitHub. Revisa tu acceso a internet.")
    }

    suspend fun usuario(): UsuarioGitHub = llamar { usuario() }

    suspend fun repositorios(): List<RepositorioGitHub> = llamar {
        val todos = mutableListOf<RepositorioGitHub>()
        var pagina = 1
        while (pagina <= 5) {
            val lote = repositorios(pagina = pagina)
            todos += lote
            if (lote.size < 100) break
            pagina++
        }
        todos
    }

    suspend fun ramas(dueno: String, repositorio: String): List<RamaGitHub> = llamar { ramas(dueno, repositorio) }

    suspend fun listar(dueno: String, repositorio: String, ruta: String, rama: String): List<EntradaContenido> = llamar {
        val elemento = if (ruta.isBlank()) contenidoRaiz(dueno, repositorio, rama) else contenido(dueno, repositorio, codificarRuta(ruta), rama)
        when (elemento) {
            is JsonArray -> elemento.map { json.decodeFromJsonElement<EntradaContenido>(it) }
                .sortedWith(compareBy({ !it.esCarpeta }, { it.name.lowercase() }))
            is JsonObject -> listOf(json.decodeFromJsonElement<EntradaContenido>(elemento))
            else -> emptyList()
        }
    }

    suspend fun descargar(dueno: String, repositorio: String, ruta: String, rama: String): Pair<String, String> = llamar {
        val elemento = contenido(dueno, repositorio, codificarRuta(ruta), rama)
        val entrada = (elemento as? JsonObject)?.let { json.decodeFromJsonElement<EntradaContenido>(it) }
            ?: throw ErrorGitHub("La ruta indicada es una carpeta, no un archivo.")
        if (entrada.encoding != "base64" || entrada.content == null) throw ErrorGitHub("El archivo es demasiado grande para descargarse con la API de contenidos (máximo 1 MB).")
        val bytes = Base64.getMimeDecoder().decode(entrada.content)
        String(bytes, Charsets.UTF_8) to entrada.sha
    }

    suspend fun publicar(dueno: String, repositorio: String, ruta: String, rama: String, contenido: String, mensaje: String, shaAnterior: String?): RespuestaPublicacion = llamar {
        val codificado = Base64.getEncoder().encodeToString(contenido.toByteArray(Charsets.UTF_8))
        publicar(dueno, repositorio, codificarRuta(ruta), SolicitudPublicacion(mensaje, codificado, rama, shaAnterior))
    }

    suspend fun shaActual(dueno: String, repositorio: String, ruta: String, rama: String): String? = try {
        descargar(dueno, repositorio, ruta, rama).second
    } catch (e: ErrorGitHub) {
        if (e.codigoHttp == 404) null else throw e
    }

    suspend fun pullRequestsAbiertos(dueno: String, repositorio: String) = llamar { pullRequests(dueno, repositorio) }

    suspend fun archivosDePullRequest(dueno: String, repositorio: String, numero: Int) = llamar { archivosPullRequest(dueno, repositorio, numero) }

    private fun codificarRuta(ruta: String): String =
        ruta.trim('/').split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}
