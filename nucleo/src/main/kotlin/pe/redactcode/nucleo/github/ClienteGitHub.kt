package pe.redactcode.nucleo.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
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

    @GET("repos/{dueno}/{repositorio}")
    suspend fun repositorio(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String): RepositorioGitHub

    @Streaming
    @GET("repos/{dueno}/{repositorio}/zipball/{ref}")
    suspend fun zipball(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Path(value = "ref", encoded = true) ref: String): ResponseBody

    @GET("repos/{dueno}/{repositorio}/git/ref/heads/{rama}")
    suspend fun referencia(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Path(value = "rama", encoded = true) rama: String): ReferenciaGit

    @GET("repos/{dueno}/{repositorio}/git/commits/{sha}")
    suspend fun commitGit(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Path("sha") sha: String): CommitGit

    @GET("repos/{dueno}/{repositorio}/git/trees/{sha}")
    suspend fun arbol(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Path("sha") sha: String, @Query("recursive") recursivo: Int = 1): ArbolGit

    @GET("repos/{dueno}/{repositorio}/git/blobs/{sha}")
    suspend fun blob(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Path("sha") sha: String): BlobGit

    @POST("repos/{dueno}/{repositorio}/git/blobs")
    suspend fun crearBlob(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Body cuerpo: NuevoBlob): ReferenciaSha

    @POST("repos/{dueno}/{repositorio}/git/trees")
    suspend fun crearArbol(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Body cuerpo: JsonObject): ReferenciaSha

    @POST("repos/{dueno}/{repositorio}/git/commits")
    suspend fun crearCommit(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Body cuerpo: NuevoCommit): ReferenciaSha

    @PATCH("repos/{dueno}/{repositorio}/git/refs/heads/{rama}")
    suspend fun actualizarReferencia(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Path(value = "rama", encoded = true) rama: String, @Body cuerpo: ActualizarReferencia): ReferenciaGit

    @POST("repos/{dueno}/{repositorio}/git/refs")
    suspend fun crearReferencia(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Body cuerpo: NuevaReferencia): ReferenciaGit

    @GET("repos/{dueno}/{repositorio}/commits")
    suspend fun commits(@Path("dueno") dueno: String, @Path("repositorio") repositorio: String, @Query("sha") rama: String, @Query("per_page") porPagina: Int = 30): List<ResumenCommitGitHub>
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

    suspend fun repositorio(dueno: String, repositorio: String): RepositorioGitHub = llamar { repositorio(dueno, repositorio) }

    suspend fun descargarZip(dueno: String, repositorio: String, ref: String, destino: java.io.OutputStream, limiteBytes: Long = 200L * 1024 * 1024): Long = llamar {
        zipball(dueno, repositorio, codificarRuta(ref)).use { cuerpo ->
            cuerpo.byteStream().use { entrada ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = entrada.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > limiteBytes) throw ErrorGitHub("El repositorio supera el tamaño máximo de descarga (${limiteBytes / (1024 * 1024)} MB)")
                    destino.write(buffer, 0, n)
                }
                total
            }
        }
    }

    suspend fun shaRama(dueno: String, repositorio: String, rama: String): String = llamar { referencia(dueno, repositorio, codificarRuta(rama)).objeto.sha }

    suspend fun arbolDeCommit(dueno: String, repositorio: String, shaCommit: String): Pair<String, ArbolGit> = llamar {
        val c = commitGit(dueno, repositorio, shaCommit)
        c.arbol.sha to arbol(dueno, repositorio, c.arbol.sha)
    }

    suspend fun bytesBlob(dueno: String, repositorio: String, sha: String): ByteArray = llamar {
        val b = blob(dueno, repositorio, sha)
        if (b.encoding != "base64") throw ErrorGitHub("Codificación de blob no admitida: ${b.encoding}")
        Base64.getMimeDecoder().decode(b.content)
    }

    suspend fun subirBlob(dueno: String, repositorio: String, bytes: ByteArray): String = llamar {
        crearBlob(dueno, repositorio, NuevoBlob(Base64.getEncoder().encodeToString(bytes), "base64")).sha
    }

    suspend fun nuevoArbol(dueno: String, repositorio: String, base: String?, entradas: List<EntradaNuevoArbol>): String = llamar {
        crearArbol(dueno, repositorio, cuerpoArbol(base, entradas)).sha
    }

    suspend fun nuevoCommit(dueno: String, repositorio: String, mensaje: String, arbol: String, padres: List<String>, autor: AutorGit?): String = llamar {
        crearCommit(dueno, repositorio, NuevoCommit(mensaje, arbol, padres, autor)).sha
    }

    suspend fun moverRama(dueno: String, repositorio: String, rama: String, sha: String): String = llamar {
        actualizarReferencia(dueno, repositorio, codificarRuta(rama), ActualizarReferencia(sha, false)).objeto.sha
    }

    suspend fun crearRama(dueno: String, repositorio: String, rama: String, sha: String): String = llamar {
        crearReferencia(dueno, repositorio, NuevaReferencia("refs/heads/$rama", sha)).objeto.sha
    }

    suspend fun historial(dueno: String, repositorio: String, rama: String, cantidad: Int = 30): List<ResumenCommitGitHub> = llamar { commits(dueno, repositorio, rama, cantidad) }

    companion object {
        fun cuerpoArbol(base: String?, entradas: List<EntradaNuevoArbol>): JsonObject = buildJsonObject {
            if (base != null) put("base_tree", base)
            put("tree", buildJsonArray {
                entradas.forEach { e ->
                    add(buildJsonObject {
                        put("path", e.path)
                        put("mode", e.mode)
                        put("type", e.type)
                        if (e.sha == null) put("sha", JsonNull) else put("sha", e.sha)
                    })
                }
            })
        }
    }

    private fun codificarRuta(ruta: String): String =
        ruta.trim('/').split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}
