package pe.redactcode.aplicacion.nucleo.archivos

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.redactcode.aplicacion.nucleo.preferencias.OrdenArchivos
import pe.redactcode.nucleo.modelo.Lenguaje
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class NodoArchivo(
    val nombre: String,
    val rutaRelativa: String,
    val esCarpeta: Boolean,
    val tamano: Long,
    val modificado: Long,
    val profundidad: Int,
    val hijos: List<NodoArchivo> = emptyList()
) {
    val lenguaje: Lenguaje get() = Lenguaje.desdeNombreArchivo(nombre)
    val nombreSinExtension: String get() = if (esCarpeta) nombre else nombre.substringBeforeLast('.', nombre)
}

data class CoincidenciaBusqueda(val rutaRelativa: String, val linea: Int, val texto: String, val enNombre: Boolean)

class ErrorArchivo(mensaje: String) : Exception(mensaje)

@Singleton
class GestorArchivos @Inject constructor(@ApplicationContext private val contexto: Context) {
    val raiz: File by lazy { File(contexto.filesDir, "proyectos").apply { mkdirs() } }
    val temporal: File by lazy { File(contexto.cacheDir, "transferencias").apply { mkdirs() } }

    companion object {
        const val TAMANO_MAXIMO_BYTES = 2L * 1024 * 1024
        private val caracteresProhibidos = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

        fun validarNombre(nombre: String, esArchivo: Boolean): String? {
            val limpio = nombre.trim()
            return when {
                limpio.isEmpty() -> "El nombre no puede estar vacío"
                limpio.length > 80 -> "El nombre es demasiado largo (máximo 80 caracteres)"
                limpio == "." || limpio == ".." -> "Nombre no permitido"
                limpio.any { it in caracteresProhibidos || it.isISOControl() } -> "El nombre contiene caracteres no permitidos"
                limpio.startsWith(".") -> "El nombre no puede empezar con un punto"
                esArchivo && limpio.substringAfterLast('.', "").lowercase() !in Lenguaje.extensionesEditables ->
                    "Extensión no permitida. Usa una extensión de texto como .py, .js, .html, .css, .json, .txt o .md"
                else -> null
            }
        }

        fun nombreCarpetaSeguro(nombre: String): String {
            val base = nombre.trim().lowercase()
                .map { if (it.isLetterOrDigit()) it else '_' }
                .joinToString("")
                .replace(Regex("_+"), "_")
                .trim('_')
                .take(40)
            return base.ifEmpty { "proyecto" }
        }
    }

    fun carpetaProyecto(carpeta: String): File {
        val directorio = File(raiz, carpeta)
        if (directorio.canonicalFile.parentFile != raiz.canonicalFile) throw ErrorArchivo("Carpeta de proyecto no válida")
        return directorio
    }

    fun resolver(carpeta: String, rutaRelativa: String): File {
        val base = carpetaProyecto(carpeta).canonicalFile
        val destino = File(base, rutaRelativa.trim('/')).canonicalFile
        if (destino != base && !destino.path.startsWith(base.path + File.separator)) {
            throw ErrorArchivo("La ruta sale del proyecto y fue bloqueada")
        }
        return destino
    }

    private fun relativa(carpeta: String, archivo: File): String =
        archivo.canonicalFile.relativeTo(carpetaProyecto(carpeta).canonicalFile).invariantSeparatorsPath

    suspend fun crearCarpetaProyecto(carpeta: String) = withContext(Dispatchers.IO) {
        val directorio = carpetaProyecto(carpeta)
        if (!directorio.exists() && !directorio.mkdirs()) throw ErrorArchivo("No se pudo crear la carpeta del proyecto")
    }

    suspend fun eliminarProyecto(carpeta: String) = withContext(Dispatchers.IO) {
        carpetaProyecto(carpeta).deleteRecursively()
    }

    suspend fun arbol(carpeta: String, orden: OrdenArchivos): List<NodoArchivo> = withContext(Dispatchers.IO) {
        val base = carpetaProyecto(carpeta)
        fun construir(directorio: File, profundidad: Int): List<NodoArchivo> {
            val elementos = directorio.listFiles()?.filterNot { it.name.startsWith(".") } ?: return emptyList()
            val ordenados = when (orden) {
                OrdenArchivos.NOMBRE -> elementos.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                OrdenArchivos.TIPO -> elementos.sortedWith(compareBy<File>({ !it.isDirectory }, { it.extension.lowercase() }, { it.name.lowercase() }))
                OrdenArchivos.MODIFICACION -> elementos.sortedWith(compareBy<File>({ !it.isDirectory }, { -it.lastModified() }))
            }
            return ordenados.map { f ->
                NodoArchivo(
                    nombre = f.name,
                    rutaRelativa = relativa(carpeta, f),
                    esCarpeta = f.isDirectory,
                    tamano = if (f.isFile) f.length() else 0,
                    modificado = f.lastModified(),
                    profundidad = profundidad,
                    hijos = if (f.isDirectory && profundidad < 12) construir(f, profundidad + 1) else emptyList()
                )
            }
        }
        construir(base, 0)
    }

    suspend fun listarArchivos(carpeta: String): List<String> = withContext(Dispatchers.IO) {
        val base = carpetaProyecto(carpeta)
        base.walkTopDown().onEnter { it == base || (!it.name.startsWith(".") && it.name != "node_modules") }.filter { it.isFile && !it.name.startsWith(".") }.map { relativa(carpeta, it) }.sorted().toList()
    }

    suspend fun crearArchivo(carpeta: String, carpetaPadre: String, nombre: String, contenido: String = ""): String = withContext(Dispatchers.IO) {
        validarNombre(nombre, esArchivo = true)?.let { throw ErrorArchivo(it) }
        val destino = resolver(carpeta, if (carpetaPadre.isBlank()) nombre.trim() else "$carpetaPadre/${nombre.trim()}")
        if (destino.exists()) throw ErrorArchivo("Ya existe un elemento con ese nombre")
        destino.parentFile?.mkdirs()
        destino.writeText(contenido, Charsets.UTF_8)
        relativa(carpeta, destino)
    }

    suspend fun crearCarpeta(carpeta: String, carpetaPadre: String, nombre: String): String = withContext(Dispatchers.IO) {
        validarNombre(nombre, esArchivo = false)?.let { throw ErrorArchivo(it) }
        val destino = resolver(carpeta, if (carpetaPadre.isBlank()) nombre.trim() else "$carpetaPadre/${nombre.trim()}")
        if (destino.exists()) throw ErrorArchivo("Ya existe un elemento con ese nombre")
        if (!destino.mkdirs()) throw ErrorArchivo("No se pudo crear la carpeta")
        relativa(carpeta, destino)
    }

    suspend fun renombrar(carpeta: String, rutaRelativa: String, nuevoNombre: String): String = withContext(Dispatchers.IO) {
        val origen = resolver(carpeta, rutaRelativa)
        if (!origen.exists()) throw ErrorArchivo("El elemento ya no existe")
        validarNombre(nuevoNombre, esArchivo = origen.isFile)?.let { throw ErrorArchivo(it) }
        val destino = File(origen.parentFile, nuevoNombre.trim())
        resolver(carpeta, relativa(carpeta, destino))
        if (destino.exists()) throw ErrorArchivo("Ya existe un elemento con ese nombre")
        if (!origen.renameTo(destino)) throw ErrorArchivo("No se pudo renombrar")
        relativa(carpeta, destino)
    }

    suspend fun mover(carpeta: String, rutaRelativa: String, carpetaDestino: String): String = withContext(Dispatchers.IO) {
        val origen = resolver(carpeta, rutaRelativa)
        val directorioDestino = resolver(carpeta, carpetaDestino)
        if (!directorioDestino.isDirectory) throw ErrorArchivo("El destino no es una carpeta")
        if (origen.isDirectory && (directorioDestino == origen || directorioDestino.path.startsWith(origen.path + File.separator))) {
            throw ErrorArchivo("No se puede mover una carpeta dentro de sí misma")
        }
        val destino = File(directorioDestino, origen.name)
        if (destino.exists()) throw ErrorArchivo("El destino ya contiene un elemento con ese nombre")
        if (!origen.renameTo(destino)) throw ErrorArchivo("No se pudo mover el elemento")
        relativa(carpeta, destino)
    }

    suspend fun eliminar(carpeta: String, rutaRelativa: String) = withContext(Dispatchers.IO) {
        val objetivo = resolver(carpeta, rutaRelativa)
        if (objetivo == carpetaProyecto(carpeta).canonicalFile) throw ErrorArchivo("No se puede eliminar la raíz del proyecto")
        if (!objetivo.deleteRecursively()) throw ErrorArchivo("No se pudo eliminar")
    }

    suspend fun leer(carpeta: String, rutaRelativa: String): String = withContext(Dispatchers.IO) {
        val archivo = resolver(carpeta, rutaRelativa)
        if (!archivo.isFile) throw ErrorArchivo("El archivo no existe")
        if (archivo.length() > TAMANO_MAXIMO_BYTES) throw ErrorArchivo("El archivo supera el tamaño máximo de 2 MB")
        archivo.readText(Charsets.UTF_8)
    }

    suspend fun existe(carpeta: String, rutaRelativa: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver(carpeta, rutaRelativa).isFile }.getOrDefault(false)
    }

    suspend fun guardar(carpeta: String, rutaRelativa: String, contenido: String) = withContext(Dispatchers.IO) {
        val archivo = resolver(carpeta, rutaRelativa)
        archivo.parentFile?.mkdirs()
        val temporal = File(archivo.parentFile, ".${archivo.name}.tmp")
        try {
            temporal.writeText(contenido, Charsets.UTF_8)
            if (archivo.exists() && !archivo.delete()) throw IOException("No se pudo reemplazar el archivo")
            if (!temporal.renameTo(archivo)) throw IOException("No se pudo completar el guardado")
        } catch (e: IOException) {
            temporal.delete()
            throw ErrorArchivo(e.message ?: "Error al guardar")
        }
    }

    suspend fun buscar(carpeta: String, consulta: String, limite: Int = 200): List<CoincidenciaBusqueda> = withContext(Dispatchers.IO) {
        if (consulta.isBlank()) return@withContext emptyList()
        val resultado = mutableListOf<CoincidenciaBusqueda>()
        val base = carpetaProyecto(carpeta)
        for (archivo in base.walkTopDown().onEnter { it == base || (!it.name.startsWith(".") && it.name != "node_modules") }.filter { it.isFile && !it.name.startsWith(".") }) {
            val ruta = relativa(carpeta, archivo)
            if (archivo.name.contains(consulta, ignoreCase = true)) resultado += CoincidenciaBusqueda(ruta, 0, archivo.name, true)
            if (archivo.length() <= TAMANO_MAXIMO_BYTES) {
                archivo.useLines(Charsets.UTF_8) { lineas ->
                    lineas.forEachIndexed { indice, linea ->
                        if (resultado.size < limite && linea.contains(consulta, ignoreCase = true)) {
                            resultado += CoincidenciaBusqueda(ruta, indice + 1, linea.trim().take(160), false)
                        }
                    }
                }
            }
            if (resultado.size >= limite) break
        }
        resultado
    }
}
