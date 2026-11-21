package pe.redactcode.aplicacion.datos

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.base_datos.FragmentoDao
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoDao
import pe.redactcode.nucleo.modelo.Lenguaje
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ManifiestoCopia(
    val formato: String = "redactcode.copia",
    val version: Int = 1,
    val creadaEn: Long,
    val proyectos: List<ProyectoCopia>,
    val fragmentos: List<FragmentoCopia>
)

@Serializable
data class ProyectoCopia(val nombre: String, val descripcion: String, val carpeta: String, val lenguaje: String)

@Serializable
data class FragmentoCopia(val titulo: String, val descripcion: String, val codigo: String, val lenguaje: String, val etiquetas: String)

data class ResumenCopia(val proyectos: Int, val archivos: Int, val fragmentos: Int)

@Singleton
class GestorCopiasSeguridad @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val proyectoDao: ProyectoDao,
    private val fragmentoDao: FragmentoDao,
    private val archivos: GestorArchivos,
    private val repositorioProyectos: RepositorioProyectos,
    private val repositorioFragmentos: RepositorioFragmentos
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val limiteArchivo = GestorArchivos.TAMANO_MAXIMO_BYTES

    suspend fun exportar(destino: Uri): ResumenCopia = withContext(Dispatchers.IO) {
        val proyectos = proyectoDao.todos()
        val fragmentos = fragmentoDao.todos()
        var cantidadArchivos = 0
        val salida = contexto.contentResolver.openOutputStream(destino) ?: throw IllegalStateException("No se pudo abrir el destino")
        ZipOutputStream(salida).use { zip ->
            val manifiesto = ManifiestoCopia(
                creadaEn = System.currentTimeMillis(),
                proyectos = proyectos.map { ProyectoCopia(it.nombre, it.descripcion, it.carpeta, it.lenguajePrincipal) },
                fragmentos = fragmentos.map { FragmentoCopia(it.titulo, it.descripcion, it.codigo, it.lenguaje, it.etiquetas) }
            )
            zip.putNextEntry(ZipEntry("manifiesto.json"))
            zip.write(json.encodeToString(ManifiestoCopia.serializer(), manifiesto).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            proyectos.forEach { p ->
                archivos.listarArchivos(p.carpeta).forEach { ruta ->
                    zip.putNextEntry(ZipEntry("proyectos/${p.carpeta}/$ruta"))
                    archivos.resolver(p.carpeta, ruta).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    cantidadArchivos++
                }
            }
        }
        ResumenCopia(proyectos.size, cantidadArchivos, fragmentos.size)
    }

    suspend fun importar(origen: Uri): ResumenCopia = withContext(Dispatchers.IO) {
        val entradaFlujo = contexto.contentResolver.openInputStream(origen) ?: throw IllegalStateException("No se pudo abrir el archivo")
        var manifiesto: ManifiestoCopia? = null
        val contenidos = mutableMapOf<String, MutableMap<String, String>>()
        var total = 0L
        ZipInputStream(entradaFlujo).use { zip ->
            while (true) {
                val entrada = zip.nextEntry ?: break
                if (entrada.isDirectory) continue
                val nombre = entrada.name.replace('\\', '/')
                if (nombre.contains("..") || nombre.startsWith("/")) throw IllegalStateException("La copia contiene rutas no permitidas")
                val bytes = leerLimitado(zip)
                total += bytes.size
                if (total > 200L * 1024 * 1024) throw IllegalStateException("La copia es demasiado grande")
                when {
                    nombre == "manifiesto.json" -> manifiesto = json.decodeFromString(ManifiestoCopia.serializer(), bytes.toString(Charsets.UTF_8))
                    nombre.startsWith("proyectos/") -> {
                        val resto = nombre.removePrefix("proyectos/")
                        val carpeta = resto.substringBefore('/')
                        val ruta = resto.substringAfter('/', "")
                        val extension = ruta.substringAfterLast('.', "").lowercase()
                        if (ruta.isNotEmpty() && extension in Lenguaje.extensionesPermitidas) {
                            contenidos.getOrPut(carpeta) { mutableMapOf() }[ruta] = bytes.toString(Charsets.UTF_8)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val datos = manifiesto ?: throw IllegalStateException("El archivo no es una copia de seguridad de Redact Code")
        if (datos.formato != "redactcode.copia") throw IllegalStateException("Formato de copia desconocido")
        var cantidadArchivos = 0
        datos.proyectos.forEach { p ->
            val archivosProyecto = contenidos[p.carpeta].orEmpty()
            repositorioProyectos.crear("${p.nombre} (restaurado)".take(60), p.descripcion, PlantillaProyecto.VACIO, archivosProyecto)
            cantidadArchivos += archivosProyecto.size
        }
        datos.fragmentos.forEach { f ->
            repositorioFragmentos.guardar(null, f.titulo, f.descripcion, f.codigo, Lenguaje.desdeClave(f.lenguaje), f.etiquetas.split(','), true)
        }
        ResumenCopia(datos.proyectos.size, cantidadArchivos, datos.fragmentos.size)
    }

    private fun leerLimitado(zip: ZipInputStream): ByteArray {
        val salida = ByteArrayOutputStream()
        val bufer = ByteArray(8192)
        var leidos = 0L
        while (true) {
            val n = zip.read(bufer)
            if (n < 0) break
            leidos += n
            if (leidos > limiteArchivo) throw IllegalStateException("Un archivo de la copia supera 2 MB")
            salida.write(bufer, 0, n)
        }
        return salida.toByteArray()
    }
}
