package pe.redactcode.nucleo.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class ErrorNpm(mensaje: String) : Exception(mensaje)

data class VersionSemver(val mayor: Long, val menor: Long, val parche: Long, val prelanzamiento: String?) : Comparable<VersionSemver> {
    override fun compareTo(other: VersionSemver): Int {
        val c = compareValuesBy(this, other, { it.mayor }, { it.menor }, { it.parche })
        if (c != 0) return c
        if (prelanzamiento == null && other.prelanzamiento == null) return 0
        if (prelanzamiento == null) return 1
        if (other.prelanzamiento == null) return -1
        return prelanzamiento.compareTo(other.prelanzamiento)
    }

    override fun toString() = "$mayor.$menor.$parche" + (prelanzamiento?.let { "-$it" } ?: "")

    companion object {
        private val patron = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.\-]+))?(?:\+[0-9A-Za-z.\-]+)?$""")

        fun analizar(texto: String): VersionSemver? {
            val m = patron.matchEntire(texto.trim()) ?: return null
            return VersionSemver(m.groupValues[1].toLong(), m.groupValues[2].ifEmpty { "0" }.toLong(), m.groupValues[3].ifEmpty { "0" }.toLong(), m.groupValues[4].ifEmpty { null })
        }
    }
}

object RangoSemver {
    fun cumple(version: VersionSemver, rango: String): Boolean {
        val r = rango.trim()
        if (r.isEmpty() || r == "*" || r == "latest" || r == "x") return version.prelanzamiento == null
        return r.split("||").any { alternativa -> comparadores(alternativa.trim()).let { c -> c != null && c.all { it(version) } } && (version.prelanzamiento == null || alternativa.contains("-")) }
    }

    fun elegir(versiones: Collection<String>, rango: String): String? =
        versiones.mapNotNull { v -> VersionSemver.analizar(v)?.let { v to it } }
            .filter { (_, v) -> cumple(v, rango) }.maxByOrNull { it.second }?.first

    private fun comparadores(texto: String): List<(VersionSemver) -> Boolean>? {
        if (texto.isEmpty() || texto == "*") return listOf { true }
        val guion = Regex("""^(\S+)\s+-\s+(\S+)$""").matchEntire(texto)
        if (guion != null) {
            val a = VersionSemver.analizar(guion.groupValues[1]) ?: return null
            val b = VersionSemver.analizar(guion.groupValues[2]) ?: return null
            return listOf({ it >= a }, { it <= b })
        }
        val lista = mutableListOf<(VersionSemver) -> Boolean>()
        val tokens = Regex("""(\^|~>?|>=|<=|>|<|=)?\s*v?([0-9xX*]+(?:\.[0-9xX*]+){0,2}(?:-[0-9A-Za-z.\-]+)?)""").findAll(texto).toList()
        if (tokens.isEmpty()) return null
        for (m in tokens) {
            val op = m.groupValues[1].ifEmpty { "=" }
            val partes = m.groupValues[2].substringBefore('-').split('.')
            val comodin = partes.indexOfFirst { it == "x" || it == "X" || it == "*" }
            val numeros = (if (comodin >= 0) partes.take(comodin) else partes).map { it.toLongOrNull() ?: return null }
            val base = VersionSemver(numeros.getOrElse(0) { 0 }, numeros.getOrElse(1) { 0 }, numeros.getOrElse(2) { 0 }, m.groupValues[2].substringAfter('-', "").ifEmpty { null })
            val parcial = numeros.size < 3
            when {
                op == "^" -> {
                    val tope = when {
                        base.mayor > 0 || numeros.size == 1 -> VersionSemver(base.mayor + 1, 0, 0, null)
                        base.menor > 0 || numeros.size == 2 -> VersionSemver(0, base.menor + 1, 0, null)
                        else -> VersionSemver(0, 0, base.parche + 1, null)
                    }
                    lista += { it >= base && it < tope }
                }
                op.startsWith("~") -> {
                    val tope = if (numeros.size == 1) VersionSemver(base.mayor + 1, 0, 0, null) else VersionSemver(base.mayor, base.menor + 1, 0, null)
                    lista += { it >= base && it < tope }
                }
                op == "=" && parcial -> {
                    val tope = when (numeros.size) { 0 -> null; 1 -> VersionSemver(base.mayor + 1, 0, 0, null); else -> VersionSemver(base.mayor, base.menor + 1, 0, null) }
                    lista += { it >= base && (tope == null || it < tope) }
                }
                op == "=" -> lista += { it.compareTo(base) == 0 }
                op == ">=" -> lista += { it >= base }
                op == ">" -> lista += { if (parcial) it >= (if (numeros.size == 1) VersionSemver(base.mayor + 1, 0, 0, null) else VersionSemver(base.mayor, base.menor + 1, 0, null)) else it > base }
                op == "<=" -> lista += { if (parcial) it < (if (numeros.size == 1) VersionSemver(base.mayor + 1, 0, 0, null) else VersionSemver(base.mayor, base.menor + 1, 0, null)) else it <= base }
                op == "<" -> lista += { it < base }
            }
        }
        return lista
    }
}

object ExtractorTar {
    class Limites(val archivo: Long = 10L * 1024 * 1024, val total: Long = 60L * 1024 * 1024, val maximoArchivos: Int = 5000)

    fun extraerTgz(entrada: InputStream, destino: File, limites: Limites = Limites()): Int {
        val base = destino.canonicalFile
        base.mkdirs()
        val flujo = GZIPInputStream(entrada.buffered())
        var archivos = 0
        var total = 0L
        val cabecera = ByteArray(512)
        var nombreLargo: String? = null
        while (true) {
            if (!leerCompleto(flujo, cabecera)) break
            if (cabecera.all { it == 0.toByte() }) break
            val nombreBase = texto(cabecera, 0, 100)
            val prefijo = texto(cabecera, 345, 155)
            var nombre = nombreLargo ?: if (prefijo.isNotEmpty()) "$prefijo/$nombreBase" else nombreBase
            nombreLargo = null
            val tamano = octal(cabecera, 124, 12)
            val tipo = cabecera[156].toInt().toChar()
            val relleno = (512 - (tamano % 512)) % 512
            if (tipo == 'L') {
                val datos = ByteArray(tamano.toInt())
                if (!leerCompleto(flujo, datos)) throw ErrorNpm("Paquete dañado")
                flujo.skip(relleno)
                nombreLargo = String(datos, Charsets.UTF_8).trimEnd('\u0000')
                continue
            }
            if (tipo == 'x' || tipo == 'g') { omitir(flujo, tamano + relleno); continue }
            val normal = tipo == '0' || tipo == '\u0000'
            val relativa = nombre.replace('\\', '/').substringAfter('/', "")
            if (!normal || relativa.isEmpty()) { omitir(flujo, tamano + relleno); continue }
            if (relativa.startsWith("/") || relativa.split('/').any { it == ".." || it.isEmpty() }) throw ErrorNpm("El paquete contiene rutas no permitidas: $nombre")
            if (tamano > limites.archivo) throw ErrorNpm("El paquete contiene un archivo demasiado grande: $relativa")
            total += tamano
            archivos++
            if (total > limites.total || archivos > limites.maximoArchivos) throw ErrorNpm("El paquete supera los límites de tamaño")
            val archivo = File(base, relativa).canonicalFile
            if (!archivo.path.startsWith(base.path + File.separator)) throw ErrorNpm("El paquete contiene rutas no permitidas: $nombre")
            archivo.parentFile?.mkdirs()
            archivo.outputStream().use { salida ->
                var restante = tamano
                val buffer = ByteArray(16 * 1024)
                while (restante > 0) {
                    val n = flujo.read(buffer, 0, minOf(buffer.size.toLong(), restante).toInt())
                    if (n < 0) throw ErrorNpm("Paquete dañado")
                    salida.write(buffer, 0, n)
                    restante -= n
                }
            }
            omitir(flujo, relleno)
        }
        return archivos
    }

    private fun leerCompleto(f: InputStream, b: ByteArray): Boolean {
        var leidos = 0
        while (leidos < b.size) {
            val n = f.read(b, leidos, b.size - leidos)
            if (n < 0) return leidos != 0 && false
            leidos += n
        }
        return true
    }

    private fun omitir(f: InputStream, cantidad: Long) {
        var resto = cantidad
        val buffer = ByteArray(8192)
        while (resto > 0) {
            val n = f.read(buffer, 0, minOf(buffer.size.toLong(), resto).toInt())
            if (n < 0) return
            resto -= n
        }
    }

    private fun texto(b: ByteArray, desde: Int, largo: Int): String {
        var fin = desde
        while (fin < desde + largo && b[fin] != 0.toByte()) fin++
        return String(b, desde, fin - desde, Charsets.UTF_8)
    }

    private fun octal(b: ByteArray, desde: Int, largo: Int): Long {
        val t = texto(b, desde, largo).trim()
        return if (t.isEmpty()) 0 else t.toLongOrNull(8) ?: throw ErrorNpm("Paquete dañado")
    }
}

data class PaqueteNpm(val nombre: String, val version: String)

class InstaladorNpm(
    private val urlRegistro: String = "https://registry.npmjs.org",
    clienteBase: OkHttpClient? = null,
    private val paquetesMaximos: Int = 100,
    private val profundidadMaxima: Int = 12,
    private val tamanoMaximoTarball: Long = 30L * 1024 * 1024
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cliente = (clienteBase?.newBuilder() ?: OkHttpClient.Builder()).connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    companion object {
        private val nombreValido = Regex("""^(@[a-z0-9][a-z0-9._\-]*/)?[a-z0-9][a-z0-9._\-]*$""")
        fun validarNombre(nombre: String): Boolean = nombre.length <= 214 && nombreValido.matches(nombre)
        fun separar(especificacion: String): Pair<String, String> {
            val i = especificacion.lastIndexOf('@')
            return if (i > 0) especificacion.substring(0, i) to especificacion.substring(i + 1) else especificacion to "latest"
        }
    }

    private fun descargarJson(nombre: String): JsonObject {
        val ruta = nombre.replace("/", "%2F")
        cliente.newCall(Request.Builder().url("${urlRegistro.trimEnd('/')}/$ruta").header("Accept", "application/vnd.npm.install-v1+json, application/json").build()).execute().use { r ->
            if (r.code == 404) throw ErrorNpm("No existe el paquete en el registro: $nombre")
            if (!r.isSuccessful) throw ErrorNpm("El registro respondió ${r.code}")
            return json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
        }
    }

    suspend fun listar(proyecto: File): List<PaqueteNpm> = withContext(Dispatchers.IO) {
        val carpeta = File(proyecto, "node_modules")
        val nombres = carpeta.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.flatMap { d ->
            if (d.name.startsWith("@")) d.listFiles().orEmpty().filter { it.isDirectory }.map { "${d.name}/${it.name}" } else listOf(d.name)
        }
        nombres.sorted().map { n ->
            val v = runCatching { json.parseToJsonElement(File(carpeta, "$n/package.json").readText()).jsonObject["version"]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
            PaqueteNpm(n, v)
        }
    }

    suspend fun desinstalar(proyecto: File, nombre: String): Boolean = withContext(Dispatchers.IO) {
        if (!validarNombre(nombre)) return@withContext false
        val carpeta = File(proyecto, "node_modules/$nombre").canonicalFile
        val base = File(proyecto, "node_modules").canonicalFile
        if (!carpeta.path.startsWith(base.path + File.separator) || !carpeta.isDirectory) return@withContext false
        carpeta.deleteRecursively()
        quitarDePackageJson(proyecto, nombre)
        true
    }

    suspend fun instalar(proyecto: File, especificaciones: List<String>, guardar: Boolean = true, alProgreso: (String) -> Unit = {}): List<PaqueteNpm> = withContext(Dispatchers.IO) {
        val modulos = File(proyecto, "node_modules").apply { mkdirs() }
        val instalados = HashMap<String, String>()
        listar(proyecto).forEach { instalados[it.nombre] = it.version }
        val nuevos = mutableListOf<PaqueteNpm>()
        val cola = ArrayDeque<Triple<String, String, Int>>()
        val pedidos = mutableListOf<Pair<String, String>>()
        especificaciones.forEach { e ->
            val (n, r) = separar(e.trim())
            if (!validarNombre(n)) throw ErrorNpm("Nombre de paquete no válido: $n")
            cola += Triple(n, r, 0)
            pedidos += n to r
        }
        while (cola.isNotEmpty()) {
            val (nombre, rango, profundidad) = cola.removeFirst()
            if (profundidad > profundidadMaxima) throw ErrorNpm("Demasiados niveles de dependencias en $nombre")
            val actual = instalados[nombre]
            if (actual != null) {
                val v = VersionSemver.analizar(actual)
                if (v == null || RangoSemver.cumple(v, rango) || rango.contains(Regex("""^(git|http|file|github)"""))) continue
                throw ErrorNpm("Conflicto: $nombre $actual ya está instalado y se pide $rango (no se admiten copias anidadas)")
            }
            if (instalados.size >= paquetesMaximos) throw ErrorNpm("Se superó el máximo de $paquetesMaximos paquetes")
            if (rango.contains(Regex("""^(git|http|file|github|link|workspace|npm:)""")) || rango.contains('/')) throw ErrorNpm("$nombre: solo se admiten versiones del registro público, no '$rango'")
            alProgreso("Resolviendo $nombre")
            val meta = descargarJson(nombre)
            val versiones = meta["versions"]?.jsonObject ?: throw ErrorNpm("Respuesta inesperada para $nombre")
            val etiqueta = meta["dist-tags"]?.jsonObject
            val elegida = (etiqueta?.get(rango)?.jsonPrimitive?.contentOrNull?.takeIf { VersionSemver.analizar(rango) == null && rango != "*" && rango != "" }
                ?: if (rango == "latest" || rango.isEmpty() || rango == "*") etiqueta?.get("latest")?.jsonPrimitive?.contentOrNull else null)
                ?: RangoSemver.elegir(versiones.keys, rango)
                ?: throw ErrorNpm("No hay una versión de $nombre que cumpla '$rango'")
            val datos = versiones[elegida]?.jsonObject ?: throw ErrorNpm("Falta la versión $elegida de $nombre")
            val dist = datos["dist"]?.jsonObject ?: throw ErrorNpm("Falta la información de descarga de $nombre")
            val tarball = dist["tarball"]?.jsonPrimitive?.contentOrNull ?: throw ErrorNpm("Falta la URL de descarga de $nombre")
            if (!tarball.startsWith("https://") && !tarball.startsWith(urlRegistro.trimEnd('/'))) throw ErrorNpm("URL de descarga no permitida para $nombre")
            alProgreso("Descargando $nombre@$elegida")
            val temporal = File.createTempFile("npm", ".tgz", modulos)
            val destino = File(modulos, nombre)
            try {
                val sha1 = MessageDigest.getInstance("SHA-1")
                val sha512 = MessageDigest.getInstance("SHA-512")
                var total = 0L
                cliente.newCall(Request.Builder().url(tarball).build()).execute().use { r ->
                    if (!r.isSuccessful) throw ErrorNpm("No se pudo descargar $nombre: ${r.code}")
                    val cuerpo = r.body ?: throw ErrorNpm("Respuesta vacía")
                    temporal.outputStream().use { salida ->
                        cuerpo.byteStream().use { entrada ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val n = entrada.read(buffer)
                                if (n < 0) break
                                total += n
                                if (total > tamanoMaximoTarball) throw ErrorNpm("El paquete $nombre supera el límite de tamaño")
                                sha1.update(buffer, 0, n)
                                sha512.update(buffer, 0, n)
                                salida.write(buffer, 0, n)
                            }
                        }
                    }
                }
                verificar(nombre, dist, sha1.digest(), sha512.digest())
                if (destino.exists()) destino.deleteRecursively()
                try {
                    temporal.inputStream().use { ExtractorTar.extraerTgz(it, destino) }
                } catch (e: Exception) {
                    destino.deleteRecursively()
                    throw if (e is ErrorNpm) e else ErrorNpm("No se pudo extraer $nombre: ${e.message}")
                }
            } finally {
                temporal.delete()
            }
            instalados[nombre] = elegida
            nuevos += PaqueteNpm(nombre, elegida)
            (datos["dependencies"] as? JsonObject)?.forEach { (dep, r) -> cola += Triple(dep, (r as? JsonPrimitive)?.contentOrNull.orEmpty(), profundidad + 1) }
        }
        if (guardar) registrarEnPackageJson(proyecto, pedidos.map { (n, _) -> n to (instalados[n] ?: "") }.filter { it.second.isNotEmpty() })
        nuevos
    }

    private fun verificar(nombre: String, dist: JsonObject, sha1: ByteArray, sha512: ByteArray) {
        val integridad = dist["integrity"]?.jsonPrimitive?.contentOrNull
        if (integridad != null && integridad.startsWith("sha512-")) {
            val esperado = integridad.removePrefix("sha512-").substringBefore(' ')
            if (Base64.getEncoder().encodeToString(sha512) != esperado) throw ErrorNpm("La integridad de $nombre no coincide")
            return
        }
        val shasum = dist["shasum"]?.jsonPrimitive?.contentOrNull ?: throw ErrorNpm("El registro no entregó suma de verificación para $nombre")
        if (!shasum.equals(sha1.joinToString("") { "%02x".format(it) }, ignoreCase = true)) throw ErrorNpm("La suma de verificación de $nombre no coincide")
    }

    private fun leerPackageJson(proyecto: File): JsonObject =
        File(proyecto, "package.json").takeIf { it.isFile }?.let { runCatching { json.parseToJsonElement(it.readText()).jsonObject }.getOrNull() } ?: buildJsonObject { put("name", proyecto.name.lowercase().replace(Regex("[^a-z0-9._\\-]"), "-").ifEmpty { "proyecto" }); put("version", "1.0.0") }

    private fun escribirPackageJson(proyecto: File, objeto: JsonObject) {
        File(proyecto, "package.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), objeto) + "\n")
    }

    private fun registrarEnPackageJson(proyecto: File, paquetes: List<Pair<String, String>>) {
        if (paquetes.isEmpty()) return
        val actual = leerPackageJson(proyecto)
        val dependencias = (actual["dependencies"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        paquetes.forEach { (n, v) -> dependencias[n] = JsonPrimitive("^$v") }
        val nuevo = LinkedHashMap<String, JsonElement>(actual)
        nuevo["dependencies"] = JsonObject(dependencias.toSortedMap())
        escribirPackageJson(proyecto, JsonObject(nuevo))
    }

    private fun quitarDePackageJson(proyecto: File, nombre: String) {
        val actual = leerPackageJson(proyecto)
        val dependencias = (actual["dependencies"] as? JsonObject)?.toMutableMap() ?: return
        if (dependencias.remove(nombre) == null) return
        val nuevo = LinkedHashMap<String, JsonElement>(actual)
        nuevo["dependencies"] = JsonObject(dependencias)
        escribirPackageJson(proyecto, JsonObject(nuevo))
    }
}
