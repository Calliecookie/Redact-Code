package pe.redactcode.nucleo.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import pe.redactcode.nucleo.control.ControlVersiones
import pe.redactcode.nucleo.control.ErrorControlVersiones
import pe.redactcode.nucleo.ide.ResultadoComando
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface AccionesRemotasGit {
    suspend fun clonar(url: String, rama: String?): String
    suspend fun traer(): String
    suspend fun publicar(): String
}

class ComandoGit(
    private val raiz: File,
    private val identidad: () -> Pair<String, String>,
    private val remotas: AccionesRemotasGit? = null
) {
    private val control = ControlVersiones(raiz)
    private val archivoIndice = File(File(raiz, ControlVersiones.CARPETA_META), "indice.json")
    private val json = Json

    companion object {
        val SUBCOMANDOS = listOf("init", "status", "add", "commit", "log", "diff", "branch", "checkout", "switch", "clone", "pull", "push")
    }

    private fun leerIndice(): MutableSet<String> =
        runCatching { json.decodeFromString(ListSerializer(String.serializer()), archivoIndice.readText()).toMutableSet() }.getOrDefault(mutableSetOf())

    private fun guardarIndice(rutas: Set<String>) {
        archivoIndice.parentFile?.mkdirs()
        archivoIndice.writeText(json.encodeToString(ListSerializer(String.serializer()), rutas.sorted()))
    }

    private fun ok(texto: String) = ResultadoComando(if (texto.isEmpty() || texto.endsWith("\n")) texto else texto + "\n")
    private fun fallo(texto: String, codigo: Int = 1) = ResultadoComando("", "$texto\n", codigo)

    suspend fun ejecutar(argumentos: List<String>): ResultadoComando = withContext(Dispatchers.IO) {
        val sub = argumentos.firstOrNull() ?: return@withContext fallo("uso: git <subcomando>. Disponibles: ${SUBCOMANDOS.joinToString(", ")}", 1)
        val args = argumentos.drop(1)
        try {
            when (sub) {
                "init" -> { control.inicializar(); ok("Repositorio inicializado en la rama ${control.ramaActual()}") }
                "status" -> estado(args)
                "add" -> agregar(args)
                "commit" -> confirmar(args)
                "log" -> historial(args)
                "diff" -> diferencia(args)
                "branch" -> ramas(args)
                "checkout", "switch" -> cambiar(args)
                "clone" -> clonar(args)
                "pull" -> remotas?.let { ok(it.traer()) } ?: fallo("git pull no está disponible en este contexto")
                "push" -> remotas?.let { ok(it.publicar()) } ?: fallo("git push no está disponible en este contexto")
                else -> fallo("git $sub: subcomando no disponible en Redact Code. Disponibles: ${SUBCOMANDOS.joinToString(", ")}", 1)
            }
        } catch (e: ErrorControlVersiones) {
            fallo(e.message.orEmpty())
        } catch (e: Exception) {
            fallo("git $sub falló: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun estado(args: List<String>): ResultadoComando {
        val cambios = control.estado()
        val indice = leerIndice()
        if (args.contains("-s") || args.contains("--short")) {
            return ok(cambios.joinToString("\n") { "${if (it.ruta in indice) it.estado.letra else " "}${if (it.ruta in indice) " " else it.estado.letra} ${it.ruta}" })
        }
        return ok(buildString {
            append("En la rama ${control.ramaActual()}\n")
            if (cambios.isEmpty()) append("Sin cambios\n")
            else {
                val preparados = cambios.filter { it.ruta in indice }
                val resto = cambios.filter { it.ruta !in indice }
                if (preparados.isNotEmpty()) { append("Preparados:\n"); preparados.forEach { append("  ${it.estado.nombreVisible.lowercase()}: ${it.ruta}\n") } }
                if (resto.isNotEmpty()) { append("Sin preparar:\n"); resto.forEach { append("  ${it.estado.nombreVisible.lowercase()}: ${it.ruta}\n") } }
            }
        })
    }

    private fun agregar(args: List<String>): ResultadoComando {
        if (args.isEmpty()) return fallo("git add: indica rutas o .")
        val cambios = control.estado().map { it.ruta }
        val indice = leerIndice()
        for (a in args) {
            if (a == "." || a == "-A" || a == "--all") { indice.addAll(cambios); continue }
            val limpia = a.removePrefix("./").trimEnd('/')
            if (limpia.split('/').any { it == ".." } || limpia.startsWith("/")) return fallo("git add: ruta fuera del proyecto: $a")
            val coincidencias = cambios.filter { it == limpia || it.startsWith("$limpia/") }
            if (coincidencias.isEmpty()) return fallo("git add: no hay cambios en $a")
            indice.addAll(coincidencias)
        }
        guardarIndice(indice)
        return ok("")
    }

    private fun confirmar(args: List<String>): ResultadoComando {
        var mensaje: String? = null
        var todos = false
        var i = 0
        while (i < args.size) {
            when (val a = args[i]) {
                "-m", "--message" -> { mensaje = args.getOrNull(i + 1); i++ }
                "-a", "--all" -> todos = true
                "-am", "-ma" -> { todos = true; mensaje = args.getOrNull(i + 1); i++ }
                else -> if (a.startsWith("--message=")) mensaje = a.removePrefix("--message=") else return fallo("git commit: opción no admitida: $a")
            }
            i++
        }
        if (mensaje.isNullOrBlank()) return fallo("git commit: usa -m \"mensaje\"")
        val indice = leerIndice()
        val rutas: Set<String>? = if (todos) null else indice
        if (!todos && indice.isEmpty()) return fallo("git commit: no hay nada preparado. Usa git add o git commit -a")
        val (autor, correo) = identidad()
        val c = control.confirmar(mensaje, autor, correo, rutas)
        guardarIndice(emptySet())
        return ok("[${control.ramaActual()} ${c.id.take(7)}] ${c.mensaje}")
    }

    private fun historial(args: List<String>): ResultadoComando {
        var limite = 20
        val una = args.contains("--oneline")
        args.forEachIndexed { i, a ->
            if (a == "-n") args.getOrNull(i + 1)?.toIntOrNull()?.let { limite = it }
            else if (a.startsWith("-n") && a.length > 2) a.drop(2).toIntOrNull()?.let { limite = it }
            else if (a.startsWith("-") && a.drop(1).toIntOrNull() != null) limite = a.drop(1).toInt()
        }
        val formato = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val lista = control.historial(limite.coerceIn(1, 500))
        if (lista.isEmpty()) return ok("Sin commits")
        return ok(lista.joinToString(if (una) "\n" else "\n\n") { c ->
            if (una) "${c.id.take(7)} ${c.mensaje.lineSequence().first()}"
            else "commit ${c.id}\nAutor: ${c.autor}${if (c.correo.isNotEmpty()) " <${c.correo}>" else ""}\nFecha: ${formato.format(Date(c.fecha))}\n\n    ${c.mensaje.lineSequence().first()}"
        })
    }

    private fun diferencia(args: List<String>): ResultadoComando {
        val rutas = args.filter { !it.startsWith("-") }
        val objetivo = if (rutas.isEmpty()) control.estado().map { it.ruta } else rutas.map { it.removePrefix("./") }
        val salida = objetivo.mapNotNull { ruta ->
            control.diferenciaTrabajo(ruta)?.let { d ->
                if (d.esBinario) "Archivos binarios $ruta difieren" else d.diferencias?.unificado("a/$ruta", "b/$ruta")
            }
        }
        return ok(salida.joinToString("\n"))
    }

    private fun ramas(args: List<String>): ResultadoComando {
        val actual = control.ramaActual()
        val nombres = args.filter { !it.startsWith("-") }
        if (args.contains("-d") || args.contains("-D")) {
            val n = nombres.firstOrNull() ?: return fallo("git branch -d: indica la rama")
            control.eliminarRama(n)
            return ok("Rama $n eliminada")
        }
        if (nombres.isEmpty()) return ok(control.ramas().joinToString("\n") { (if (it == actual) "* " else "  ") + it })
        control.crearRama(nombres.first(), cambiar = false)
        return ok("")
    }

    private fun cambiar(args: List<String>): ResultadoComando {
        val crear = args.contains("-b") || args.contains("-c")
        val nombre = args.firstOrNull { !it.startsWith("-") } ?: return fallo("git checkout: indica la rama")
        if (crear) { control.crearRama(nombre, cambiar = true); return ok("Cambiado a la rama nueva '$nombre'") }
        control.cambiarRama(nombre)
        return ok("Cambiado a la rama '$nombre'")
    }

    private suspend fun clonar(args: List<String>): ResultadoComando {
        val acciones = remotas ?: return fallo("git clone no está disponible en este contexto")
        val url = args.firstOrNull { !it.startsWith("-") } ?: return fallo("git clone: indica la URL de GitHub")
        if (!Regex("""^https://github\.com/[A-Za-z0-9_.\-]+/[A-Za-z0-9_.\-]+(\.git)?/?$""").matches(url)) return fallo("git clone: solo se admiten URL https://github.com/<usuario>/<repositorio>")
        val rama = args.indexOf("--branch").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) } ?: args.indexOf("-b").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
        return ok(acciones.clonar(url, rama))
    }
}

