package pe.redactcode.aplicacion.funcionalidades.terminal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.nucleo.ide.ResultadoComando
import pe.redactcode.nucleo.modelo.EstadoEjecucion
import pe.redactcode.nucleo.ide.ComandoInterno
import pe.redactcode.nucleo.ide.TerminalLocal
import pe.redactcode.nucleo.terminal.CensorSecretos
import pe.redactcode.nucleo.terminal.ClasificadorRiesgo
import pe.redactcode.nucleo.github.AutorGit
import pe.redactcode.nucleo.github.ClienteGitHub
import pe.redactcode.nucleo.github.SincronizadorGitHub
import pe.redactcode.nucleo.terminal.AccionesRemotasGit
import pe.redactcode.nucleo.terminal.ComandoGit
import pe.redactcode.nucleo.terminal.NivelRiesgo
import pe.redactcode.nucleo.terminal.RegistroEjecuciones
import pe.redactcode.nucleo.terminal.ErrorPip
import pe.redactcode.nucleo.terminal.ErrorNpm
import pe.redactcode.nucleo.terminal.InstaladorNpm
import pe.redactcode.nucleo.terminal.InstaladorPip
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.redactcode.nucleo.terminal.PuenteComandos
import pe.redactcode.nucleo.terminal.ServidorPuente
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServicioPuenteTerminal @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val servicio: ServicioCodigo,
    private val preferencias: RepositorioPreferencias,
    private val cliente: ClienteGitHub,
    private val repositorioGitHub: pe.redactcode.aplicacion.datos.RepositorioGitHub
) {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val servidores = HashMap<Long, ServidorPuente>()
    private val instalador = InstaladorPip()
    private val instaladorNpm = InstaladorNpm()
    val registro = RegistroEjecuciones(File(contexto.filesDir, "terminal/registro_ejecuciones.jsonl"))

    suspend fun ejecutarLinea(proyectoId: Long, raiz: File, linea: String, origen: String, limiteSegundos: Int): ResultadoComando {
        val riesgo = ClasificadorRiesgo.clasificar(linea)
        if (riesgo.nivel == NivelRiesgo.BLOQUEADO) {
            val mensaje = "Comando bloqueado: ${riesgo.motivo}\n"
            registro.registrar(System.currentTimeMillis(), origen, linea, riesgo.nivel, 126, mensaje)
            return ResultadoComando("", mensaje, 126)
        }
        val internos = PuenteComandos.COMANDOS.associateWith { c -> ComandoInterno { args, dir -> ejecutar(proyectoId, raiz, c, args, dir) } }
        val terminal = TerminalLocal(raiz, internos = internos)
        terminalEnCurso = terminal
        val r = try { terminal.ejecutar(linea, limiteSegundos) } finally { terminalEnCurso = null }
        val limpio = r.copy(salida = CensorSecretos.censurar(r.salida), error = CensorSecretos.censurar(r.error))
        registro.registrar(System.currentTimeMillis(), origen, linea, riesgo.nivel, r.codigo, limpio.salida + limpio.error)
        return limpio
    }

    fun carpetaEntorno(proyectoId: Long): File = servicio.python.carpetaEntorno(proyectoId)

    @Synchronized
    fun asegurar(proyectoId: Long, raiz: File): File {
        val carpeta = File(contexto.filesDir, "terminal/puente/$proyectoId").apply { mkdirs() }
        if (!servidores.containsKey(proyectoId)) {
            servidores[proyectoId] = ServidorPuente(carpeta, raiz, alcance) { comando, args, dir -> ejecutar(proyectoId, raiz, comando, args, dir) }.also { it.iniciar() }
        }
        return carpeta
    }

    @Synchronized
    fun detener(proyectoId: Long) {
        servidores.remove(proyectoId)?.detener()
    }

    @Volatile
    private var terminalEnCurso: TerminalLocal? = null

    fun cancelarLinea() {
        terminalEnCurso?.cancelar()
    }

    private suspend fun ejecutar(proyectoId: Long, raiz: File, comando: String, args: List<String>, dir: File): ResultadoComando = when (comando) {
        "python", "python3" -> ejecutarArchivo(proyectoId, raiz, args, dir, true)
        "node" -> ejecutarArchivo(proyectoId, raiz, args, dir, false)
        "git" -> {
            val p = preferencias.actuales()
            val autor = p.gitAutor.ifBlank { "Redact Code" }
            ComandoGit(raiz, { autor to p.gitCorreo }, accionesRemotas(raiz, autor, p.gitCorreo)).ejecutar(args)
        }
        "pip", "pip3" -> pip(proyectoId, raiz, dir, args)
        "npm" -> npm(raiz, args)
        else -> ayuda()
    }

    private fun accionesRemotas(raiz: File, autor: String, correo: String) = object : AccionesRemotasGit {
        override suspend fun clonar(url: String, rama: String?): String {
            val partes = url.removePrefix("https://github.com/").removeSuffix("/").removeSuffix(".git").split('/')
            val repo = cliente.repositorio(partes[0], partes[1])
            val id = repositorioGitHub.clonar(repo, rama ?: repo.ramaPrincipal)
            return "Clonado ${repo.nombreCompleto} como un proyecto nuevo (id $id). Ábrelo desde Inicio."
        }

        override suspend fun traer(): String {
            val r = SincronizadorGitHub(cliente, raiz).traer(autor)
            return when {
                r.yaActualizado -> "Ya estás al día con el remoto"
                r.conflictos.isNotEmpty() -> "Conflictos en: ${r.conflictos.joinToString()}. No se aplicó ningún cambio."
                else -> "Actualizados ${r.actualizados.size}, eliminados ${r.eliminados.size}"
            }
        }

        override suspend fun publicar(): String {
            val r = SincronizadorGitHub(cliente, raiz).publicar("", if (correo.isNotBlank()) AutorGit(autor, correo) else null)
            return "Publicado ${r.shaCommit.take(7)}: ${r.modificados.size} modificado(s), ${r.eliminados.size} eliminado(s)"
        }
    }

    private suspend fun npm(raiz: File, args: List<String>): ResultadoComando {
        val sub = args.firstOrNull().orEmpty()
        val resto = args.drop(1).filter { !it.startsWith("-") }
        return try {
            when (sub) {
                "install", "i", "add" -> {
                    val pedidos = resto.ifEmpty { dependenciasDePackageJson(raiz) }
                    if (pedidos.isEmpty()) return ResultadoComando("", "Uso: npm install paquete[@version]. No hay dependencias en package.json.\n", 2)
                    val nuevos = instaladorNpm.instalar(raiz, pedidos, guardar = resto.isNotEmpty())
                    ResultadoComando(if (nuevos.isEmpty()) "Nada que instalar\n" else "Instalados en node_modules: ${nuevos.joinToString(", ") { "${it.nombre}@${it.version}" }}\nNo se ejecutan scripts de instalación.\n")
                }
                "list", "ls" -> ResultadoComando(instaladorNpm.listar(raiz).joinToString("\n") { "${it.nombre}@${it.version}" }.let { if (it.isEmpty()) "Sin paquetes\n" else it + "\n" })
                "uninstall", "remove", "rm" -> {
                    val n = resto.firstOrNull() ?: return ResultadoComando("", "Uso: npm uninstall paquete\n", 2)
                    if (instaladorNpm.desinstalar(raiz, n)) ResultadoComando("Desinstalado $n\n") else ResultadoComando("", "No está instalado: $n\n", 1)
                }
                else -> ResultadoComando("", "npm $sub: no disponible. Redact Code solo descarga paquetes (install, list, uninstall) y no ejecuta Node.js ni scripts.\n", 1)
            }
        } catch (e: ErrorNpm) {
            ResultadoComando("", "${e.message}\n", 1)
        } catch (e: java.io.IOException) {
            ResultadoComando("", "Error de red: ${e.message}\n", 1)
        }
    }

    private fun dependenciasDePackageJson(raiz: File): List<String> {
        val f = File(raiz, "package.json")
        if (!f.isFile) return emptyList()
        val obj = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(f.readText()).jsonObject }.getOrNull() ?: return emptyList()
        return (obj["dependencies"] as? kotlinx.serialization.json.JsonObject)?.map { (n, v) -> "$n@${v.jsonPrimitive.content}" }.orEmpty()
    }

    private fun ayuda() = ResultadoComando(
        "Comandos de Redact Code: ${PuenteComandos.COMANDOS.joinToString(" ")}\n" +
            "python archivo.py · node archivo.js · git (init, status, add, commit, log, diff, branch, checkout, clone, pull, push) · pip (install, list, uninstall, show) · npm (install, list, uninstall)\n"
    )

    private suspend fun ejecutarArchivo(proyectoId: Long, raiz: File, args: List<String>, dir: File, esPython: Boolean): ResultadoComando {
        val nombre = args.firstOrNull { !it.startsWith("-") } ?: return ResultadoComando("", "Uso: ${if (esPython) "python" else "node"} archivo\n", 2)
        val archivo = File(dir, nombre).canonicalFile
        if (!archivo.path.startsWith(raiz.canonicalPath + File.separator)) return ResultadoComando("", "El archivo está fuera del proyecto\n", 1)
        if (!archivo.isFile) return ResultadoComando("", "No existe el archivo $nombre\n", 1)
        val codigo = withContext(Dispatchers.IO) { archivo.readText() }
        val limite = preferencias.actuales().limiteEjecucionSegundos
        val r = if (esPython) {
            servicio.python.usarEntorno(proyectoId)
            servicio.ejecutarPython(codigo, "", limite)
        } else servicio.ejecutarJavaScript(codigo, limite)
        fun cerrar(t: String) = if (t.isEmpty() || t.endsWith("\n")) t else "$t\n"
        return ResultadoComando(cerrar(r.salida), cerrar(r.errores), if (r.estado == EstadoEjecucion.EXITO) 0 else 1)
    }

    private suspend fun pip(proyectoId: Long, raiz: File, dir: File, args: List<String>): ResultadoComando {
        val destino = carpetaEntorno(proyectoId)
        val sub = args.firstOrNull().orEmpty()
        val resto = args.drop(1)
        return try {
            when (sub) {
                "install" -> {
                    val requisitos = if (resto.firstOrNull() == "-r") {
                        val f = File(dir, resto.getOrNull(1).orEmpty()).canonicalFile
                        if (!f.path.startsWith(raiz.canonicalPath + File.separator)) return ResultadoComando("", "El archivo de requisitos está fuera del proyecto\n", 1)
                        if (!f.isFile) return ResultadoComando("", "No existe el archivo de requisitos\n", 1)
                        f.readLines()
                    } else resto.filter { !it.startsWith("-") }
                    if (requisitos.isEmpty()) return ResultadoComando("", "Uso: pip install paquete[==version]\n", 2)
                    val nuevos = instalador.instalar(requisitos, destino)
                    ResultadoComando(if (nuevos.isEmpty()) "Nada que instalar\n" else "Instalados: ${nuevos.joinToString(", ") { "${it.nombre} ${it.version}" }}\n")
                }
                "list" -> ResultadoComando(instalador.listarInstalados(destino).joinToString("\n") { "${it.nombre} ${it.version}" }.let { if (it.isEmpty()) "Sin paquetes\n" else it + "\n" })
                "uninstall" -> {
                    val nombre = resto.firstOrNull { !it.startsWith("-") } ?: return ResultadoComando("", "Uso: pip uninstall paquete\n", 2)
                    if (instalador.desinstalar(destino, nombre)) ResultadoComando("Desinstalado $nombre\n") else ResultadoComando("", "No está instalado: $nombre\n", 1)
                }
                "show" -> {
                    val nombre = resto.firstOrNull { !it.startsWith("-") } ?: return ResultadoComando("", "Uso: pip show paquete\n", 2)
                    val p = instalador.listarInstalados(destino).firstOrNull { it.nombre.equals(nombre, true) }
                    if (p == null) ResultadoComando("", "No está instalado: $nombre\n", 1) else ResultadoComando("Name: ${p.nombre}\nVersion: ${p.version}\nLocation: ${destino.absolutePath}\n")
                }
                else -> ResultadoComando("", "pip $sub: subcomando no disponible. Disponibles: install, list, uninstall, show\n", 1)
            }
        } catch (e: ErrorPip) {
            ResultadoComando("", "${e.message}\n", 1)
        } catch (e: java.io.IOException) {
            ResultadoComando("", "Error de red: ${e.message}\n", 1)
        }
    }
}
