package pe.redactcode.nucleo.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import pe.redactcode.nucleo.github.ExtractorZip
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Serializable
data class ArchivoPyPi(
    val filename: String = "",
    val url: String = "",
    val packagetype: String = "",
    val size: Long = 0,
    val yanked: Boolean = false,
    val digests: Map<String, String> = emptyMap()
)

@Serializable
data class InfoPyPi(val name: String = "", val version: String = "", val requires_dist: List<String>? = null)

@Serializable
data class RespuestaPyPi(
    val info: InfoPyPi = InfoPyPi(),
    val releases: Map<String, List<ArchivoPyPi>> = emptyMap(),
    val urls: List<ArchivoPyPi> = emptyList()
)

data class PaqueteInstalado(val nombre: String, val version: String)

class ErrorPip(mensaje: String) : Exception(mensaje)

data class RequisitoPip(val nombre: String, val especificador: String, val marcador: String)

object AnalizadorRequisitos {
    private val patron = Regex("""^\s*([A-Za-z0-9][A-Za-z0-9._\-]*)\s*(\[[^\]]*\])?\s*(\(?\s*(?:[<>=!~]=?=?[^;,)\s]+\s*,?\s*)*\)?)?\s*(?:;\s*(.*))?$""")

    fun analizar(texto: String): RequisitoPip? {
        val linea = texto.substringBefore(" #").trim()
        if (linea.isEmpty() || linea.startsWith("#") || linea.startsWith("-")) return null
        val m = patron.matchEntire(linea) ?: return null
        val espec = m.groupValues[3].trim().removePrefix("(").removeSuffix(")").replace(" ", "")
        return RequisitoPip(m.groupValues[1], espec, m.groupValues[4].trim())
    }

    fun marcadorCumple(marcador: String): Boolean {
        if (marcador.isBlank()) return true
        return marcador.split(Regex("""\s+or\s+""")).any { alternativa ->
            alternativa.split(Regex("""\s+and\s+""")).all { condicion -> condicionCumple(condicion.trim().trim('(', ')').trim()) }
        }
    }

    private val valores = mapOf(
        "python_version" to "3.13", "python_full_version" to "3.13.0", "sys_platform" to "linux", "platform_system" to "Linux",
        "os_name" to "posix", "platform_machine" to "aarch64", "implementation_name" to "cpython", "platform_python_implementation" to "CPython", "extra" to ""
    )

    private fun condicionCumple(c: String): Boolean {
        val m = Regex("""^["']?([^"'\s]+)["']?\s*(==|!=|>=|<=|>|<|~=|in|not in)\s*["']?([^"']*)["']?$""").matchEntire(c) ?: return false
        var izquierda = m.groupValues[1]
        var derecha = m.groupValues[3]
        val variableIzquierda = valores.containsKey(izquierda)
        if (!variableIzquierda && valores.containsKey(derecha)) { val t = izquierda; izquierda = derecha; derecha = t }
        val real = valores[izquierda] ?: return false
        return when (m.groupValues[2]) {
            "==" -> real == derecha || (izquierda.contains("version") && comparar(real, derecha) == 0)
            "!=" -> real != derecha && !(izquierda.contains("version") && comparar(real, derecha) == 0)
            ">=", ">", "<=", "<" -> {
                val r = comparar(real, derecha)
                when (m.groupValues[2]) { ">=" -> r >= 0; ">" -> r > 0; "<=" -> r <= 0; else -> r < 0 }
            }
            "in" -> derecha.contains(real)
            "not in" -> !derecha.contains(real)
            else -> false
        }
    }

    private fun comparar(a: String, b: String): Int {
        val va = VersionPep440.analizar(a); val vb = VersionPep440.analizar(b)
        return if (va != null && vb != null) va.compareTo(vb) else a.compareTo(b)
    }
}

class InstaladorPip(
    private val urlIndice: String = "https://pypi.org",
    clienteBase: OkHttpClient? = null,
    private val tamanoMaximoRueda: Long = 50L * 1024 * 1024,
    private val profundidadMaxima: Int = 15,
    private val paquetesMaximos: Int = 60
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cliente = (clienteBase?.newBuilder() ?: OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private fun descargarTexto(url: String): String {
        val peticion = Request.Builder().url(url).header("Accept", "application/json").build()
        cliente.newCall(peticion).execute().use { r ->
            if (r.code == 404) throw ErrorPip("No existe el paquete en el índice: ${url.substringAfterLast("/pypi/").substringBefore("/json")}")
            if (!r.isSuccessful) throw ErrorPip("El índice respondió ${r.code}")
            return r.body?.string().orEmpty()
        }
    }

    private fun ruedaPura(archivos: List<ArchivoPyPi>): ArchivoPyPi? = archivos.firstOrNull {
        !it.yanked && it.packagetype == "bdist_wheel" && (it.filename.endsWith("-py3-none-any.whl") || it.filename.endsWith("-py2.py3-none-any.whl"))
    }

    suspend fun listarInstalados(destino: File): List<PaqueteInstalado> = withContext(Dispatchers.IO) {
        destino.listFiles { f -> f.isDirectory && f.name.endsWith(".dist-info") }.orEmpty().mapNotNull { d ->
            val meta = File(d, "METADATA").takeIf { it.isFile }?.readLines().orEmpty()
            val nombre = meta.firstOrNull { it.startsWith("Name: ") }?.removePrefix("Name: ")?.trim() ?: return@mapNotNull null
            val version = meta.firstOrNull { it.startsWith("Version: ") }?.removePrefix("Version: ")?.trim().orEmpty()
            PaqueteInstalado(nombre, version)
        }.sortedBy { it.nombre.lowercase() }
    }

    suspend fun desinstalar(destino: File, nombre: String): Boolean = withContext(Dispatchers.IO) {
        val normal = EspecificadorPep440.normalizarNombre(nombre)
        val info = destino.listFiles { f -> f.isDirectory && f.name.endsWith(".dist-info") }.orEmpty().firstOrNull {
            EspecificadorPep440.normalizarNombre(it.name.removeSuffix(".dist-info").substringBeforeLast('-')) == normal
        } ?: return@withContext false
        val destinoCanonico = destino.canonicalFile
        val registro = File(info, "RECORD")
        val rutas = if (registro.isFile) registro.readLines().map { it.substringBefore(',') } else emptyList()
        rutas.forEach { r ->
            val f = File(destino, r).canonicalFile
            if (f.path.startsWith(destinoCanonico.path + File.separator) && f.isFile) f.delete()
        }
        info.deleteRecursively()
        destino.walkBottomUp().filter { it.isDirectory && it != destino && it.list().isNullOrEmpty() }.forEach { it.delete() }
        true
    }

    suspend fun instalar(requisitos: List<String>, destino: File, alProgreso: (String) -> Unit = {}): List<PaqueteInstalado> = withContext(Dispatchers.IO) {
        destino.mkdirs()
        val instalados = LinkedHashMap<String, PaqueteInstalado>()
        listarInstalados(destino).forEach { instalados[EspecificadorPep440.normalizarNombre(it.nombre)] = it }
        val nuevos = mutableListOf<PaqueteInstalado>()
        val cola = ArrayDeque<Pair<RequisitoPip, Int>>()
        requisitos.forEach { texto ->
            val r = AnalizadorRequisitos.analizar(texto) ?: throw ErrorPip("Requisito no válido: $texto")
            cola += r to 0
        }
        while (cola.isNotEmpty()) {
            val (req, profundidad) = cola.removeFirst()
            if (!AnalizadorRequisitos.marcadorCumple(req.marcador)) continue
            if (profundidad > profundidadMaxima) throw ErrorPip("Demasiados niveles de dependencias en ${req.nombre}")
            val clave = EspecificadorPep440.normalizarNombre(req.nombre)
            val existente = instalados[clave]
            if (existente != null) {
                val v = VersionPep440.analizar(existente.version)
                val restricciones = EspecificadorPep440.analizar(req.especificador)
                if (v == null || restricciones == null || restricciones.all { it.cumple(v) }) continue
                throw ErrorPip("Conflicto: ${req.nombre} ${existente.version} ya está instalado y se pide ${req.especificador}")
            }
            if (instalados.size >= paquetesMaximos) throw ErrorPip("Se superó el máximo de $paquetesMaximos paquetes")
            alProgreso("Resolviendo ${req.nombre}")
            val base = urlIndice.trimEnd('/')
            val versionesTexto = runCatching { descargarTexto("$base/pypi/${req.nombre}/json") }.getOrElse { throw if (it is ErrorPip) it else ErrorPip("No se pudo consultar el índice: ${it.message}") }
            val completa = json.decodeFromString(RespuestaPyPi.serializer(), versionesTexto)
            val especificador = req.especificador
            val elegida = EspecificadorPep440.elegir(completa.releases.filterValues { it.any { a -> !a.yanked } }.keys.ifEmpty { listOf(completa.info.version) }, especificador)
                ?: throw ErrorPip("No hay una versión de ${req.nombre} que cumpla '${especificador.ifEmpty { "cualquiera" }}'")
            val detalle = if (elegida == completa.info.version) completa
            else json.decodeFromString(RespuestaPyPi.serializer(), runCatching { descargarTexto("$base/pypi/${req.nombre}/$elegida/json") }.getOrElse { throw if (it is ErrorPip) it else ErrorPip("No se pudo consultar el índice: ${it.message}") })
            val archivos = detalle.urls.ifEmpty { completa.releases[elegida].orEmpty() }
            val rueda = ruedaPura(archivos) ?: throw ErrorPip(
                "${req.nombre} $elegida necesita código nativo o no publica una rueda Python pura: solo puede incluirse al compilar con Chaquopy (bloque pip del build.gradle.kts)"
            )
            if (rueda.size > tamanoMaximoRueda) throw ErrorPip("La rueda de ${req.nombre} supera el límite de ${tamanoMaximoRueda / (1024 * 1024)} MB")
            alProgreso("Descargando ${rueda.filename}")
            val temporal = File.createTempFile("rueda", ".whl", destino)
            try {
                val sha = MessageDigest.getInstance("SHA-256")
                var total = 0L
                cliente.newCall(Request.Builder().url(rueda.url).build()).execute().use { r ->
                    if (!r.isSuccessful) throw ErrorPip("No se pudo descargar ${rueda.filename}: ${r.code}")
                    val cuerpo = r.body ?: throw ErrorPip("Respuesta vacía")
                    temporal.outputStream().use { salida ->
                        cuerpo.byteStream().use { entrada ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val n = entrada.read(buffer)
                                if (n < 0) break
                                total += n
                                if (total > tamanoMaximoRueda) throw ErrorPip("La rueda supera el límite de tamaño")
                                sha.update(buffer, 0, n)
                                salida.write(buffer, 0, n)
                            }
                        }
                    }
                }
                val esperado = rueda.digests["sha256"]
                val obtenido = sha.digest().joinToString("") { "%02x".format(it) }
                if (esperado == null || !esperado.equals(obtenido, ignoreCase = true)) throw ErrorPip("La suma de verificación de ${rueda.filename} no coincide")
                val (_, omitidos) = ExtractorZip.extraer(temporal, destino, quitarCarpetaRaiz = false)
                if (omitidos.isNotEmpty()) throw ErrorPip("La rueda contiene rutas no permitidas: ${omitidos.first()}")
            } finally {
                temporal.delete()
            }
            val paquete = PaqueteInstalado(completa.info.name.ifEmpty { req.nombre }, elegida)
            instalados[clave] = paquete
            nuevos += paquete
            val dependencias = detalle.info.requires_dist.orEmpty()
            dependencias.forEach { d -> AnalizadorRequisitos.analizar(d)?.let { dep -> cola += dep to profundidad + 1 } }
        }
        nuevos
    }
}
