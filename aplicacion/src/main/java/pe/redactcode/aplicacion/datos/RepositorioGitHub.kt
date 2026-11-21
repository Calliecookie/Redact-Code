package pe.redactcode.aplicacion.datos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoEntidad
import pe.redactcode.aplicacion.nucleo.seguridad.AlmacenCredenciales
import pe.redactcode.nucleo.github.ClienteGitHub
import pe.redactcode.nucleo.github.EntradaContenido
import pe.redactcode.nucleo.github.ErrorGitHub
import pe.redactcode.nucleo.github.RamaGitHub
import pe.redactcode.nucleo.github.RepositorioGitHub as RepoGitHub
import pe.redactcode.nucleo.github.RespuestaPublicacion
import pe.redactcode.nucleo.modelo.Lenguaje
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepositorioGitHub @Inject constructor(
    private val cliente: ClienteGitHub,
    private val credenciales: AlmacenCredenciales,
    private val proyectos: RepositorioProyectos
) {
    private val usuarioActual = MutableStateFlow<String?>(null)
    val usuario: StateFlow<String?> = usuarioActual.asStateFlow()

    suspend fun cargarEstado() {
        usuarioActual.value = if (credenciales.leer(AlmacenCredenciales.TOKEN_GITHUB) != null) credenciales.leer(AlmacenCredenciales.USUARIO_GITHUB) else null
    }

    suspend fun conectar(token: String): String {
        val limpio = token.trim()
        if (limpio.length < 20 || limpio.any { it.isWhitespace() }) throw ErrorGitHub("El token no tiene un formato válido")
        credenciales.guardar(AlmacenCredenciales.TOKEN_GITHUB, limpio)
        return try {
            val usuario = cliente.usuario()
            credenciales.guardar(AlmacenCredenciales.USUARIO_GITHUB, usuario.login)
            usuarioActual.value = usuario.login
            usuario.login
        } catch (e: Exception) {
            credenciales.eliminar(AlmacenCredenciales.TOKEN_GITHUB)
            throw e
        }
    }

    suspend fun desconectar() {
        credenciales.eliminar(AlmacenCredenciales.TOKEN_GITHUB)
        credenciales.eliminar(AlmacenCredenciales.USUARIO_GITHUB)
        usuarioActual.value = null
    }

    suspend fun repositorios(): List<RepoGitHub> = cliente.repositorios()

    suspend fun ramas(repo: RepoGitHub): List<RamaGitHub> = cliente.ramas(repo.owner.login, repo.name)

    suspend fun listar(repo: RepoGitHub, ruta: String, rama: String): List<EntradaContenido> = cliente.listar(repo.owner.login, repo.name, ruta, rama)

    suspend fun descargarEnProyecto(repo: RepoGitHub, rama: String, rutas: List<String>, proyectoDestino: ProyectoEntidad?): Long {
        val permitidas = rutas.filter { Lenguaje.desdeNombreArchivo(it) != Lenguaje.TEXTO || it.endsWith(".txt") }
        if (permitidas.isEmpty()) throw ErrorGitHub("Redact Code solo abre archivos .py, .js, .html, .css, .json y .txt")
        val descargados = permitidas.map { ruta ->
            val (contenido, sha) = cliente.descargar(repo.owner.login, repo.name, ruta, rama)
            Triple(ruta, contenido, sha)
        }
        val proyecto = proyectoDestino ?: proyectos.obtener(
            proyectos.crear(repo.name, repo.description ?: "Importado de ${repo.nombreCompleto}", PlantillaProyecto.VACIO, emptyMap(), ProyectoEntidad.ORIGEN_GITHUB, null, repo.nombreCompleto, rama)
        )!!
        descargados.forEach { (ruta, contenido, sha) ->
            proyectos.guardar(proyecto, ruta, contenido)
            proyectos.guardarShaGitHub(proyecto, ruta, sha)
        }
        return proyecto.id
    }

    suspend fun publicar(proyecto: ProyectoEntidad, ruta: String, mensaje: String, repositorioCompleto: String, rama: String): RespuestaPublicacion {
        val (dueno, nombre) = repositorioCompleto.split('/').let { if (it.size == 2) it[0] to it[1] else throw ErrorGitHub("Repositorio no válido. Usa el formato usuario/repositorio") }
        require(mensaje.isNotBlank()) { "El mensaje del commit es obligatorio" }
        val contenido = proyectos.leer(proyecto, ruta)
        val shaRemoto = cliente.shaActual(dueno, nombre, ruta, rama)
        val shaLocal = proyectos.registroArchivo(proyecto, ruta)?.githubSha
        if (shaRemoto != null && shaLocal != null && shaRemoto != shaLocal) {
            throw ErrorGitHub("El archivo cambió en GitHub desde tu última descarga. Descárgalo de nuevo para no sobrescribir cambios ajenos.")
        }
        val respuesta = cliente.publicar(dueno, nombre, ruta, rama, contenido, mensaje.trim(), shaRemoto)
        respuesta.content?.sha?.let { proyectos.guardarShaGitHub(proyecto, ruta, it) }
        if (proyecto.githubRepositorio == null) proyectos.actualizar(proyecto.copy(githubRepositorio = repositorioCompleto, githubRama = rama))
        return respuesta
    }
}
