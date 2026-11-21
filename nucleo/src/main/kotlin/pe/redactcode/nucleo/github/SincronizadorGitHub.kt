package pe.redactcode.nucleo.github

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pe.redactcode.nucleo.control.ControlVersiones
import pe.redactcode.nucleo.control.ErrorControlVersiones
import java.io.File
import java.util.zip.ZipInputStream

@Serializable
data class VinculoRemoto(
    val dueno: String,
    val repositorio: String,
    val rama: String,
    val shaCommit: String,
    val shaArbol: String,
    val arbol: Map<String, String>
) {
    val nombreCompleto: String get() = "$dueno/$repositorio"
}

data class ResultadoClonado(val archivos: Int, val diferentesDelRemoto: List<String>, val omitidos: List<String>)
data class ResultadoTraer(val actualizados: List<String>, val eliminados: List<String>, val conflictos: List<String>, val shaRemoto: String, val yaActualizado: Boolean)
data class ResultadoPublicar(val shaCommit: String, val modificados: List<String>, val eliminados: List<String>)

object ExtractorZip {
    const val TAMANO_MAXIMO_ARCHIVO = 10L * 1024 * 1024
    const val TAMANO_MAXIMO_TOTAL = 300L * 1024 * 1024
    const val MAXIMO_ARCHIVOS = 20_000

    fun extraer(zip: File, destino: File, quitarCarpetaRaiz: Boolean): Pair<List<String>, List<String>> {
        val base = destino.canonicalFile
        base.mkdirs()
        val nombres = mutableListOf<String>()
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory) nombres += e.name.replace('\\', '/')
            }
        }
        val prefijo = if (quitarCarpetaRaiz) {
            val primeros = nombres.map { it.substringBefore('/', "") }.toSet()
            if (primeros.size == 1 && primeros.first().isNotEmpty() && nombres.all { it.contains('/') }) primeros.first() + "/" else ""
        } else ""
        val extraidos = mutableListOf<String>()
        val omitidos = mutableListOf<String>()
        var total = 0L
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val nombre = e.name.replace('\\', '/').removePrefix(prefijo)
                if (e.isDirectory || nombre.isBlank()) continue
                if (nombre.startsWith("/") || nombre.split('/').any { it == ".." || it.isEmpty() }) { omitidos += nombre; continue }
                if (nombre.split('/').any { it == ".git" }) continue
                if (extraidos.size >= MAXIMO_ARCHIVOS) { omitidos += nombre; continue }
                val archivo = File(base, nombre).canonicalFile
                if (!archivo.path.startsWith(base.path + File.separator)) { omitidos += nombre; continue }
                archivo.parentFile?.mkdirs()
                var escritos = 0L
                var excedido = false
                archivo.outputStream().use { salida ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = z.read(buffer)
                        if (n < 0) break
                        escritos += n
                        if (escritos > TAMANO_MAXIMO_ARCHIVO) { excedido = true; break }
                        salida.write(buffer, 0, n)
                    }
                }
                if (excedido) { archivo.delete(); omitidos += "$nombre (más de 10 MB)"; continue }
                total += escritos
                if (total > TAMANO_MAXIMO_TOTAL) throw ErrorControlVersiones("El contenido descomprimido supera 300 MB")
                extraidos += nombre
            }
        }
        return extraidos to omitidos
    }
}

class SincronizadorGitHub(private val cliente: ClienteGitHub, private val raiz: File, reloj: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }
    val vcs = ControlVersiones(raiz, reloj)
    private val archivoVinculo = File(raiz, "${ControlVersiones.CARPETA_META}/remoto.json")

    private fun visible(ruta: String) = ruta.split('/').none { it.startsWith(".") }

    fun vinculo(): VinculoRemoto? = archivoVinculo.takeIf { it.isFile }?.let { runCatching { json.decodeFromString<VinculoRemoto>(it.readText()) }.getOrNull() }

    fun guardarVinculo(v: VinculoRemoto) {
        archivoVinculo.parentFile?.mkdirs()
        val temporal = File(archivoVinculo.parentFile, "remoto.json.tmp")
        temporal.writeText(json.encodeToString(VinculoRemoto.serializer(), v))
        archivoVinculo.delete()
        temporal.renameTo(archivoVinculo)
    }

    fun desvincular() { archivoVinculo.delete() }

    suspend fun clonar(dueno: String, repositorio: String, rama: String, autor: String, temporal: File): ResultadoClonado {
        val sha = cliente.shaRama(dueno, repositorio, rama)
        temporal.outputStream().use { cliente.descargarZip(dueno, repositorio, sha, it) }
        val (_, omitidos) = try { ExtractorZip.extraer(temporal, raiz, quitarCarpetaRaiz = true) } finally { temporal.delete() }
        val (shaArbol, arbol) = cliente.arbolDeCommit(dueno, repositorio, sha)
        val remoto = arbol.archivos.filterKeys(::visible)
        vcs.inicializar(rama)
        val local = vcs.archivosTrabajo().mapValues { vcs.importarArchivo(it.value.readBytes()) }
        vcs.registrarCommit(null, "Clonado de $dueno/$repositorio@${sha.take(7)}", autor, "", local.toSortedMap(), sha)
        guardarVinculo(VinculoRemoto(dueno, repositorio, rama, sha, shaArbol, remoto))
        val diferentes = (local.keys + remoto.keys).filter { local[it] != remoto[it] }.sorted()
        return ResultadoClonado(local.size, diferentes, omitidos + if (arbol.truncated) listOf("El árbol remoto está truncado por GitHub (repositorio muy grande)") else emptyList())
    }

    suspend fun vincular(dueno: String, repositorio: String, rama: String) {
        val sha = cliente.shaRama(dueno, repositorio, rama)
        val (shaArbol, arbol) = cliente.arbolDeCommit(dueno, repositorio, sha)
        if (!vcs.inicializado()) vcs.inicializar(rama)
        guardarVinculo(VinculoRemoto(dueno, repositorio, rama, sha, shaArbol, arbol.archivos.filterKeys(::visible)))
    }

    suspend fun traer(autor: String): ResultadoTraer {
        val v = vinculo() ?: throw ErrorControlVersiones("El proyecto no está vinculado a un repositorio de GitHub")
        if (vcs.estado().isNotEmpty()) throw ErrorControlVersiones("Confirma o descarta tus cambios antes de traer cambios del remoto")
        val shaRemoto = cliente.shaRama(v.dueno, v.repositorio, v.rama)
        if (shaRemoto == v.shaCommit) return ResultadoTraer(emptyList(), emptyList(), emptyList(), shaRemoto, true)
        val (shaArbol, arbol) = cliente.arbolDeCommit(v.dueno, v.repositorio, shaRemoto)
        val remoto = arbol.archivos.filterKeys(::visible)
        val base = v.arbol
        val cabeza = vcs.cabeza()
        val local = cabeza?.arbol.orEmpty()
        val nuevo = local.toMutableMap()
        val actualizados = mutableListOf<String>()
        val eliminados = mutableListOf<String>()
        val conflictos = mutableListOf<String>()
        (remoto.keys + base.keys + local.keys).sorted().forEach { ruta ->
            val r = remoto[ruta]
            val b = base[ruta]
            val l = local[ruta]
            when {
                r == b || l == r -> Unit
                l == b -> if (r == null) { nuevo.remove(ruta); eliminados += ruta } else { nuevo[ruta] = r; actualizados += ruta }
                else -> conflictos += ruta
            }
        }
        if (conflictos.isNotEmpty()) return ResultadoTraer(emptyList(), emptyList(), conflictos, shaRemoto, false)
        actualizados.forEach { ruta ->
            val sha = remoto.getValue(ruta)
            if (vcs.bytesObjeto(sha) == null) {
                val bytes = cliente.bytesBlob(v.dueno, v.repositorio, sha)
                if (vcs.importarArchivo(bytes) != sha) throw ErrorControlVersiones("El contenido descargado de $ruta no coincide con su identificador")
            }
        }
        vcs.restaurarArbol(nuevo)
        vcs.registrarCommit(cabeza?.id, "Traer cambios de ${v.nombreCompleto}/${v.rama} (${shaRemoto.take(7)})", autor, "", nuevo.toSortedMap(), shaRemoto)
        guardarVinculo(v.copy(shaCommit = shaRemoto, shaArbol = shaArbol, arbol = remoto))
        return ResultadoTraer(actualizados, eliminados, emptyList(), shaRemoto, false)
    }

    fun pendientesDePublicar(): Pair<List<String>, List<String>> {
        val v = vinculo() ?: return emptyList<String>() to emptyList()
        val local = vcs.cabeza()?.arbol.orEmpty()
        val modificados = local.filter { (ruta, hash) -> v.arbol[ruta] != hash }.keys.sorted()
        val eliminados = v.arbol.keys.filter { it !in local }.sorted()
        return modificados to eliminados
    }

    suspend fun publicar(mensaje: String, autor: AutorGit?, ramaNueva: String? = null): ResultadoPublicar {
        var v = vinculo() ?: throw ErrorControlVersiones("El proyecto no está vinculado a un repositorio de GitHub")
        val cabeza = vcs.cabeza() ?: throw ErrorControlVersiones("Confirma tus cambios antes de publicar")
        val (modificados, eliminados) = pendientesDePublicar()
        if (modificados.isEmpty() && eliminados.isEmpty()) throw ErrorControlVersiones("No hay commits locales pendientes de publicar")
        if (ramaNueva != null) {
            ControlVersiones.validarRama(ramaNueva)?.let { throw ErrorControlVersiones(it) }
            cliente.crearRama(v.dueno, v.repositorio, ramaNueva, v.shaCommit)
            v = v.copy(rama = ramaNueva)
            guardarVinculo(v)
        } else {
            val actual = cliente.shaRama(v.dueno, v.repositorio, v.rama)
            if (actual != v.shaCommit) throw ErrorControlVersiones("La rama remota tiene commits nuevos. Usa «Traer» antes de publicar.")
        }
        val entradas = mutableListOf<EntradaNuevoArbol>()
        modificados.forEach { ruta ->
            val hash = cabeza.arbol.getValue(ruta)
            val bytes = vcs.bytesObjeto(hash) ?: throw ErrorControlVersiones("Falta el contenido de $ruta")
            val sha = cliente.subirBlob(v.dueno, v.repositorio, bytes)
            if (sha != hash) throw ErrorControlVersiones("GitHub calculó un identificador distinto para $ruta")
            entradas += EntradaNuevoArbol(ruta, sha = sha)
        }
        eliminados.forEach { entradas += EntradaNuevoArbol(it, sha = null) }
        val arbolNuevo = cliente.nuevoArbol(v.dueno, v.repositorio, v.shaArbol, entradas)
        val commit = cliente.nuevoCommit(v.dueno, v.repositorio, mensaje.trim().ifEmpty { cabeza.mensaje }, arbolNuevo, listOf(v.shaCommit), autor)
        cliente.moverRama(v.dueno, v.repositorio, v.rama, commit)
        guardarVinculo(v.copy(shaCommit = commit, shaArbol = arbolNuevo, arbol = cabeza.arbol))
        vcs.marcarRemoto(cabeza.id, commit)
        return ResultadoPublicar(commit, modificados, eliminados)
    }
}
