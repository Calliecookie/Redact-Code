package pe.redactcode.aplicacion.datos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.archivos.NodoArchivo
import pe.redactcode.aplicacion.nucleo.base_datos.ArchivoDao
import pe.redactcode.aplicacion.nucleo.base_datos.ArchivoEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoDao
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoEntidad
import pe.redactcode.aplicacion.nucleo.preferencias.OrdenArchivos
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.proyectos.ProyectoGuiado
import javax.inject.Inject
import javax.inject.Singleton

data class ArchivoReciente(val proyecto: ProyectoEntidad, val rutaRelativa: String, val abiertoEn: Long) {
    val nombre: String get() = rutaRelativa.substringAfterLast('/')
    val lenguaje: Lenguaje get() = Lenguaje.desdeNombreArchivo(nombre)
}

enum class PlantillaProyecto(val nombreVisible: String, val lenguaje: Lenguaje) {
    PYTHON("Python", Lenguaje.PYTHON),
    WEB("Sitio web (HTML, CSS y JavaScript)", Lenguaje.HTML),
    JAVASCRIPT("JavaScript", Lenguaje.JAVASCRIPT),
    VACIO("Vacío", Lenguaje.TEXTO)
}

object PlantillasIniciales {
    fun archivos(plantilla: PlantillaProyecto, nombre: String): Map<String, String> = when (plantilla) {
        PlantillaProyecto.PYTHON -> mapOf("main.py" to "def main():\n    print(\"Hola desde $nombre\")\n\n\nif __name__ == \"__main__\":\n    main()\n")
        PlantillaProyecto.WEB -> mapOf(
            "index.html" to "<!DOCTYPE html>\n<html lang=\"es\">\n<head>\n  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n  <title>$nombre</title>\n  <link rel=\"stylesheet\" href=\"estilos.css\">\n</head>\n<body>\n  <main>\n    <h1>$nombre</h1>\n    <p id=\"mensaje\">Edita index.html para empezar.</p>\n  </main>\n  <script src=\"app.js\"></script>\n</body>\n</html>\n",
            "estilos.css" to "body {\n  font-family: system-ui, sans-serif;\n  margin: 0;\n  padding: 24px;\n  background: #0d0d0f;\n  color: #f5f5f7;\n}\n",
            "app.js" to "const mensaje = document.getElementById(\"mensaje\");\nconsole.log(\"Página cargada\");\n"
        )
        PlantillaProyecto.JAVASCRIPT -> mapOf("app.js" to "function saludar(nombre) {\n  return `Hola, ${'$'}{nombre}`;\n}\n\nconsole.log(saludar(\"$nombre\"));\n")
        PlantillaProyecto.VACIO -> mapOf("notas.txt" to "")
    }
}

@Singleton
class RepositorioProyectos @Inject constructor(
    private val proyectoDao: ProyectoDao,
    private val archivoDao: ArchivoDao,
    val archivos: GestorArchivos
) {
    val proyectos: Flow<List<ProyectoEntidad>> = proyectoDao.observarTodos()

    fun observar(id: Long) = proyectoDao.observar(id)

    suspend fun obtener(id: Long) = proyectoDao.obtener(id)

    val recientes: Flow<List<ArchivoReciente>> = combine(archivoDao.observarRecientes(12), proyectoDao.observarTodos()) { archivosRecientes, lista ->
        val porId = lista.associateBy { it.id }
        archivosRecientes.mapNotNull { a -> porId[a.proyectoId]?.let { ArchivoReciente(it, a.rutaRelativa, a.abiertoEn ?: 0) } }
    }

    private suspend fun carpetaUnica(nombre: String): String {
        val base = GestorArchivos.nombreCarpetaSeguro(nombre)
        var candidata = base
        var indice = 2
        while (proyectoDao.contarCarpeta(candidata) > 0 || archivos.carpetaProyecto(candidata).exists()) {
            candidata = "${base}_$indice"
            indice++
        }
        return candidata
    }

    suspend fun crear(
        nombre: String,
        descripcion: String,
        plantilla: PlantillaProyecto,
        archivosIniciales: Map<String, String> = PlantillasIniciales.archivos(plantilla, nombre.trim()),
        origen: String = ProyectoEntidad.ORIGEN_LOCAL,
        guiadoId: String? = null,
        githubRepositorio: String? = null,
        githubRama: String? = null
    ): Long {
        val limpio = nombre.trim()
        require(limpio.isNotEmpty() && limpio.length <= 60) { "El nombre del proyecto debe tener entre 1 y 60 caracteres" }
        val carpeta = carpetaUnica(limpio)
        archivos.crearCarpetaProyecto(carpeta)
        val ahora = System.currentTimeMillis()
        val id = proyectoDao.insertar(
            ProyectoEntidad(
                nombre = limpio, descripcion = descripcion.trim(), carpeta = carpeta, lenguajePrincipal = plantilla.lenguaje.name,
                origen = origen, proyectoGuiadoId = guiadoId, githubRepositorio = githubRepositorio, githubRama = githubRama,
                creadoEn = ahora, modificadoEn = ahora
            )
        )
        archivosIniciales.forEach { (ruta, contenido) ->
            archivos.guardar(carpeta, ruta, contenido)
            archivoDao.guardar(ArchivoEntidad(proyectoId = id, rutaRelativa = ruta, lenguaje = Lenguaje.desdeNombreArchivo(ruta).name, modificadoEn = ahora))
        }
        return id
    }

    suspend fun crearDesdeGuiado(guiado: ProyectoGuiado): Long {
        proyectoDao.porProyectoGuiado(guiado.id)?.let { return it.id }
        val plantilla = when (guiado.lenguaje) {
            Lenguaje.PYTHON -> PlantillaProyecto.PYTHON
            Lenguaje.JAVASCRIPT -> PlantillaProyecto.JAVASCRIPT
            else -> PlantillaProyecto.WEB
        }
        return crear(guiado.titulo, guiado.descripcion, plantilla, guiado.archivosIniciales, ProyectoEntidad.ORIGEN_GUIADO, guiado.id)
    }

    suspend fun proyectoDeGuiado(guiadoId: String) = proyectoDao.porProyectoGuiado(guiadoId)

    fun observarGuiados() = proyectoDao.observarGuiados()

    suspend fun actualizar(proyecto: ProyectoEntidad) = proyectoDao.actualizar(proyecto.copy(modificadoEn = System.currentTimeMillis()))

    suspend fun renombrarProyecto(proyecto: ProyectoEntidad, nombre: String) {
        require(nombre.isNotBlank() && nombre.length <= 60) { "Nombre no válido" }
        actualizar(proyecto.copy(nombre = nombre.trim()))
    }

    suspend fun eliminarProyecto(proyecto: ProyectoEntidad) {
        archivos.eliminarProyecto(proyecto.carpeta)
        proyectoDao.eliminar(proyecto)
    }

    suspend fun arbol(proyecto: ProyectoEntidad, orden: OrdenArchivos): List<NodoArchivo> = archivos.arbol(proyecto.carpeta, orden)

    suspend fun leer(proyecto: ProyectoEntidad, ruta: String): String = archivos.leer(proyecto.carpeta, ruta)

    suspend fun guardar(proyecto: ProyectoEntidad, ruta: String, contenido: String) {
        archivos.guardar(proyecto.carpeta, ruta, contenido)
        val existente = archivoDao.obtener(proyecto.id, ruta)
        archivoDao.guardar((existente ?: ArchivoEntidad(proyectoId = proyecto.id, rutaRelativa = ruta, lenguaje = Lenguaje.desdeNombreArchivo(ruta).name, modificadoEn = 0))
            .copy(modificadoEn = System.currentTimeMillis()))
        proyectoDao.tocar(proyecto.id, System.currentTimeMillis())
    }

    suspend fun registrarApertura(proyecto: ProyectoEntidad, ruta: String) {
        val existente = archivoDao.obtener(proyecto.id, ruta)
        val ahora = System.currentTimeMillis()
        archivoDao.guardar((existente ?: ArchivoEntidad(proyectoId = proyecto.id, rutaRelativa = ruta, lenguaje = Lenguaje.desdeNombreArchivo(ruta).name, modificadoEn = ahora)).copy(abiertoEn = ahora))
    }

    suspend fun crearArchivo(proyecto: ProyectoEntidad, carpetaPadre: String, nombre: String): String {
        val ruta = archivos.crearArchivo(proyecto.carpeta, carpetaPadre, nombre)
        archivoDao.guardar(ArchivoEntidad(proyectoId = proyecto.id, rutaRelativa = ruta, lenguaje = Lenguaje.desdeNombreArchivo(ruta).name, modificadoEn = System.currentTimeMillis()))
        proyectoDao.tocar(proyecto.id, System.currentTimeMillis())
        return ruta
    }

    suspend fun crearCarpeta(proyecto: ProyectoEntidad, carpetaPadre: String, nombre: String): String {
        val ruta = archivos.crearCarpeta(proyecto.carpeta, carpetaPadre, nombre)
        proyectoDao.tocar(proyecto.id, System.currentTimeMillis())
        return ruta
    }

    suspend fun renombrar(proyecto: ProyectoEntidad, ruta: String, nuevoNombre: String): String {
        val nueva = archivos.renombrar(proyecto.carpeta, ruta, nuevoNombre)
        reubicarRegistros(proyecto, ruta, nueva)
        return nueva
    }

    suspend fun mover(proyecto: ProyectoEntidad, ruta: String, destino: String): String {
        val nueva = archivos.mover(proyecto.carpeta, ruta, destino)
        reubicarRegistros(proyecto, ruta, nueva)
        return nueva
    }

    private suspend fun reubicarRegistros(proyecto: ProyectoEntidad, anterior: String, nueva: String) {
        val afectados = archivoDao.delProyecto(proyecto.id).filter { it.rutaRelativa == anterior || it.rutaRelativa.startsWith("$anterior/") }
        archivoDao.eliminarRuta(proyecto.id, anterior, "$anterior/%")
        afectados.forEach { a ->
            val ruta = nueva + a.rutaRelativa.removePrefix(anterior)
            archivoDao.guardar(a.copy(id = 0, rutaRelativa = ruta, lenguaje = Lenguaje.desdeNombreArchivo(ruta).name))
        }
        proyectoDao.tocar(proyecto.id, System.currentTimeMillis())
    }

    suspend fun eliminar(proyecto: ProyectoEntidad, ruta: String) {
        archivos.eliminar(proyecto.carpeta, ruta)
        archivoDao.eliminarRuta(proyecto.id, ruta, "$ruta/%")
        proyectoDao.tocar(proyecto.id, System.currentTimeMillis())
    }

    suspend fun buscar(proyecto: ProyectoEntidad, consulta: String) = archivos.buscar(proyecto.carpeta, consulta)

    suspend fun registroArchivo(proyecto: ProyectoEntidad, ruta: String) = archivoDao.obtener(proyecto.id, ruta)

    suspend fun guardarShaGitHub(proyecto: ProyectoEntidad, ruta: String, sha: String) {
        val existente = archivoDao.obtener(proyecto.id, ruta) ?: ArchivoEntidad(proyectoId = proyecto.id, rutaRelativa = ruta, lenguaje = Lenguaje.desdeNombreArchivo(ruta).name, modificadoEn = System.currentTimeMillis())
        archivoDao.guardar(existente.copy(githubSha = sha))
    }

    suspend fun usoPorLenguaje(): Map<Lenguaje, Int> = archivoDao.conteoPorLenguaje().associate { Lenguaje.desdeClave(it.lenguaje) to it.cantidad }
}
