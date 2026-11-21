package pe.redactcode.nucleo.control

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pe.redactcode.nucleo.ide.Diferencias
import pe.redactcode.nucleo.ide.ResultadoDiferencias
import java.io.File
import java.security.MessageDigest

@Serializable
data class CommitLocal(
    val id: String,
    val padre: String? = null,
    val mensaje: String,
    val autor: String,
    val correo: String = "",
    val fecha: Long,
    val arbol: Map<String, String>,
    val remoto: String? = null
)

@Serializable
private data class CuerpoCommit(val padre: String?, val mensaje: String, val autor: String, val correo: String, val fecha: Long, val arbol: Map<String, String>)

enum class EstadoCambio(val letra: String, val nombreVisible: String) {
    AGREGADO("A", "Agregado"), MODIFICADO("M", "Modificado"), ELIMINADO("D", "Eliminado")
}

data class CambioTrabajo(val ruta: String, val estado: EstadoCambio)

data class DiferenciaArchivo(val ruta: String, val estado: EstadoCambio, val diferencias: ResultadoDiferencias?, val esBinario: Boolean)

class ErrorControlVersiones(mensaje: String) : Exception(mensaje)

class ControlVersiones(
    private val raiz: File,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val tamanoMaximo: Long = 5L * 1024 * 1024
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val meta = File(raiz, CARPETA_META)
    private val objetos = File(meta, "objetos")
    private val commits = File(meta, "commits")
    private val ramasDir = File(meta, "ramas")
    private val cabezaArchivo = File(meta, "HEAD")

    companion object {
        const val CARPETA_META = ".redact"
        private val patronRama = Regex("^[A-Za-z0-9][A-Za-z0-9._/-]{0,79}$")

        fun hashBlob(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun validarRama(nombre: String): String? = when {
            !patronRama.matches(nombre) -> "Usa letras, números, punto, guion, guion bajo o barra (máximo 80)"
            nombre.contains("..") || nombre.endsWith("/") || nombre.endsWith(".lock") || nombre.contains("//") -> "Nombre de rama no permitido"
            else -> null
        }

        fun esTexto(bytes: ByteArray): Boolean {
            if (bytes.any { it == 0.toByte() }) return false
            return runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)) }.isSuccess
        }
    }

    fun inicializado(): Boolean = cabezaArchivo.isFile

    fun inicializar(ramaInicial: String = "main") {
        if (inicializado()) return
        validarRama(ramaInicial)?.let { throw ErrorControlVersiones(it) }
        objetos.mkdirs(); commits.mkdirs(); ramasDir.mkdirs()
        escribirAtomico(cabezaArchivo, ramaInicial)
    }

    private fun exigir() { if (!inicializado()) throw ErrorControlVersiones("El control de versiones no está inicializado en este proyecto") }

    fun ramaActual(): String { exigir(); return cabezaArchivo.readText().trim() }

    fun ramas(): List<String> {
        exigir()
        val actual = ramaActual()
        val existentes = ramasDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(ramasDir).invariantSeparatorsPath }.toMutableSet()
        existentes += actual
        return existentes.sorted()
    }

    fun idRama(rama: String): String? = File(ramasDir, rama).takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }

    fun cabeza(): CommitLocal? = idRama(ramaActual())?.let(::commit)

    fun commit(id: String): CommitLocal? {
        if (!id.matches(Regex("^[0-9a-f]{40}$"))) return null
        val archivo = File(commits, "$id.json")
        return if (archivo.isFile) json.decodeFromString<CommitLocal>(archivo.readText()) else null
    }

    fun historial(limite: Int = 200, desde: String? = null): List<CommitLocal> {
        exigir()
        val lista = mutableListOf<CommitLocal>()
        var actual = (desde ?: idRama(ramaActual()))?.let(::commit)
        while (actual != null && lista.size < limite) {
            lista += actual
            actual = actual.padre?.let(::commit)
        }
        return lista
    }

    private fun relativa(f: File) = f.relativeTo(raiz).invariantSeparatorsPath

    private fun ignorado(ruta: String) = ruta.split('/').any { it.startsWith(".") || it == "node_modules" }

    fun archivosTrabajo(): Map<String, File> =
        raiz.walkTopDown()
            .onEnter { it == raiz || !it.name.startsWith(".") }
            .filter { it.isFile && !ignorado(relativa(it)) && it.length() <= tamanoMaximo }
            .associateBy { relativa(it) }

    fun hashesTrabajo(): Map<String, String> = archivosTrabajo().mapValues { hashBlob(it.value.readBytes()) }

    fun estado(): List<CambioTrabajo> {
        exigir()
        val base = cabeza()?.arbol.orEmpty()
        val trabajo = hashesTrabajo()
        val cambios = mutableListOf<CambioTrabajo>()
        trabajo.forEach { (ruta, hash) ->
            val previo = base[ruta]
            if (previo == null) cambios += CambioTrabajo(ruta, EstadoCambio.AGREGADO)
            else if (previo != hash) cambios += CambioTrabajo(ruta, EstadoCambio.MODIFICADO)
        }
        base.keys.filter { it !in trabajo }.forEach { cambios += CambioTrabajo(it, EstadoCambio.ELIMINADO) }
        return cambios.sortedBy { it.ruta }
    }

    private fun guardarObjeto(bytes: ByteArray): String {
        val hash = hashBlob(bytes)
        val destino = File(objetos, "${hash.take(2)}/${hash.drop(2)}")
        if (!destino.isFile) {
            destino.parentFile?.mkdirs()
            val temporal = File(destino.parentFile, "${destino.name}.tmp")
            temporal.writeBytes(bytes)
            if (!temporal.renameTo(destino)) { temporal.delete(); throw ErrorControlVersiones("No se pudo guardar un objeto") }
        }
        return hash
    }

    fun bytesObjeto(hash: String): ByteArray? {
        if (!hash.matches(Regex("^[0-9a-f]{40}$"))) return null
        val archivo = File(objetos, "${hash.take(2)}/${hash.drop(2)}")
        return if (archivo.isFile) archivo.readBytes() else null
    }

    fun contenido(hash: String): String? = bytesObjeto(hash)?.takeIf(::esTexto)?.toString(Charsets.UTF_8)

    fun confirmar(mensaje: String, autor: String, correo: String = "", rutas: Set<String>? = null, remoto: String? = null): CommitLocal {
        exigir()
        val limpio = mensaje.trim()
        if (limpio.isEmpty()) throw ErrorControlVersiones("Escribe un mensaje para el commit")
        val cambios = estado().filter { rutas == null || it.ruta in rutas }
        if (cambios.isEmpty()) throw ErrorControlVersiones("No hay cambios para confirmar")
        val padre = cabeza()
        val arbol = padre?.arbol.orEmpty().toMutableMap()
        val trabajo = archivosTrabajo()
        cambios.forEach { c ->
            when (c.estado) {
                EstadoCambio.ELIMINADO -> arbol.remove(c.ruta)
                else -> arbol[c.ruta] = guardarObjeto(trabajo.getValue(c.ruta).readBytes())
            }
        }
        return registrarCommit(padre?.id, limpio, autor.trim().ifEmpty { "Redact Code" }, correo.trim(), arbol.toSortedMap(), remoto)
    }

    fun registrarCommit(padre: String?, mensaje: String, autor: String, correo: String, arbol: Map<String, String>, remoto: String? = null, fecha: Long = reloj()): CommitLocal {
        val cuerpo = CuerpoCommit(padre, mensaje, autor, correo, fecha, arbol)
        val id = MessageDigest.getInstance("SHA-1").digest(json.encodeToString(cuerpo).toByteArray()).joinToString("") { "%02x".format(it) }
        val commit = CommitLocal(id, padre, mensaje, autor, correo, fecha, arbol, remoto)
        commits.mkdirs()
        escribirAtomico(File(commits, "$id.json"), json.encodeToString(commit))
        val archivoRama = File(ramasDir, ramaActual())
        archivoRama.parentFile?.mkdirs()
        escribirAtomico(archivoRama, id)
        return commit
    }

    fun importarArchivo(bytes: ByteArray): String = guardarObjeto(bytes)

    fun marcarRemoto(id: String, remoto: String) {
        val c = commit(id) ?: return
        escribirAtomico(File(commits, "$id.json"), json.encodeToString(c.copy(remoto = remoto)))
    }

    private fun diferencia(ruta: String, estado: EstadoCambio, anterior: ByteArray?, nuevo: ByteArray?): DiferenciaArchivo {
        val binario = (anterior != null && !esTexto(anterior)) || (nuevo != null && !esTexto(nuevo))
        val resultado = if (binario) null else Diferencias.comparar(anterior?.toString(Charsets.UTF_8).orEmpty(), nuevo?.toString(Charsets.UTF_8).orEmpty())
        return DiferenciaArchivo(ruta, estado, resultado, binario)
    }

    fun diferenciaTrabajo(ruta: String): DiferenciaArchivo? {
        val cambio = estado().firstOrNull { it.ruta == ruta } ?: return null
        val anterior = cabeza()?.arbol?.get(ruta)?.let(::bytesObjeto)
        val nuevo = archivosTrabajo()[ruta]?.readBytes()
        return diferencia(ruta, cambio.estado, anterior, nuevo)
    }

    fun diferenciasCommit(id: String): List<DiferenciaArchivo> {
        val c = commit(id) ?: throw ErrorControlVersiones("El commit no existe")
        val anterior = c.padre?.let(::commit)?.arbol.orEmpty()
        val rutas = (anterior.keys + c.arbol.keys).sorted()
        return rutas.mapNotNull { ruta ->
            val a = anterior[ruta]
            val b = c.arbol[ruta]
            when {
                a == b -> null
                a == null -> diferencia(ruta, EstadoCambio.AGREGADO, null, b?.let(::bytesObjeto))
                b == null -> diferencia(ruta, EstadoCambio.ELIMINADO, bytesObjeto(a), null)
                else -> diferencia(ruta, EstadoCambio.MODIFICADO, bytesObjeto(a), bytesObjeto(b))
            }
        }
    }

    fun descartar(ruta: String) {
        val cambio = estado().firstOrNull { it.ruta == ruta } ?: return
        val destino = resolver(ruta)
        when (cambio.estado) {
            EstadoCambio.AGREGADO -> destino.delete()
            else -> {
                val bytes = cabeza()?.arbol?.get(ruta)?.let(::bytesObjeto) ?: throw ErrorControlVersiones("No se encontró la versión confirmada")
                destino.parentFile?.mkdirs()
                escribirBytesAtomico(destino, bytes)
            }
        }
    }

    fun crearRama(nombre: String, cambiar: Boolean = true) {
        exigir()
        validarRama(nombre)?.let { throw ErrorControlVersiones(it) }
        if (nombre in ramas()) throw ErrorControlVersiones("La rama $nombre ya existe")
        val archivo = File(ramasDir, nombre)
        archivo.parentFile?.mkdirs()
        escribirAtomico(archivo, idRama(ramaActual()).orEmpty())
        if (cambiar) escribirAtomico(cabezaArchivo, nombre)
    }

    fun cambiarRama(nombre: String) {
        exigir()
        if (nombre == ramaActual()) return
        if (nombre !in ramas()) throw ErrorControlVersiones("La rama $nombre no existe")
        if (estado().isNotEmpty()) throw ErrorControlVersiones("Confirma o descarta tus cambios antes de cambiar de rama")
        val actual = cabeza()?.arbol.orEmpty()
        val destino = idRama(nombre)?.let(::commit)?.arbol.orEmpty()
        actual.keys.filter { it !in destino }.forEach { resolver(it).delete() }
        destino.forEach { (ruta, hash) ->
            if (actual[ruta] != hash) {
                val archivo = resolver(ruta)
                archivo.parentFile?.mkdirs()
                escribirBytesAtomico(archivo, bytesObjeto(hash) ?: throw ErrorControlVersiones("Falta el objeto de $ruta"))
            }
        }
        limpiarCarpetasVacias()
        escribirAtomico(cabezaArchivo, nombre)
    }

    fun eliminarRama(nombre: String) {
        exigir()
        if (nombre == ramaActual()) throw ErrorControlVersiones("No puedes eliminar la rama actual")
        val archivo = File(ramasDir, nombre)
        if (!archivo.isFile) throw ErrorControlVersiones("La rama $nombre no existe")
        archivo.delete()
    }

    fun restaurarArbol(arbol: Map<String, String>) {
        val trabajo = archivosTrabajo()
        trabajo.keys.filter { it !in arbol }.forEach { resolver(it).delete() }
        arbol.forEach { (ruta, hash) ->
            val actual = trabajo[ruta]
            if (actual == null || hashBlob(actual.readBytes()) != hash) {
                val archivo = resolver(ruta)
                archivo.parentFile?.mkdirs()
                escribirBytesAtomico(archivo, bytesObjeto(hash) ?: throw ErrorControlVersiones("Falta el objeto de $ruta"))
            }
        }
        limpiarCarpetasVacias()
    }

    private fun limpiarCarpetasVacias() {
        raiz.walkBottomUp().filter { it.isDirectory && it != raiz && !relativa(it).split('/').any { s -> s.startsWith(".") } && it.listFiles().isNullOrEmpty() }.forEach { it.delete() }
    }

    fun resolver(ruta: String): File {
        val base = raiz.canonicalFile
        val destino = File(base, ruta).canonicalFile
        if (!destino.path.startsWith(base.path + File.separator) || ignorado(ruta)) throw ErrorControlVersiones("Ruta no permitida: $ruta")
        return destino
    }

    private fun escribirAtomico(archivo: File, texto: String) = escribirBytesAtomico(archivo, texto.toByteArray(Charsets.UTF_8))

    private fun escribirBytesAtomico(archivo: File, bytes: ByteArray) {
        archivo.parentFile?.mkdirs()
        val temporal = File(archivo.parentFile, ".${archivo.name}.tmp")
        temporal.writeBytes(bytes)
        if (archivo.exists()) archivo.delete()
        if (!temporal.renameTo(archivo)) { temporal.delete(); throw ErrorControlVersiones("No se pudo escribir ${archivo.name}") }
    }
}
