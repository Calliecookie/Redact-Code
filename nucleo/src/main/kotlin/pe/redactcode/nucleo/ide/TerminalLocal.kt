package pe.redactcode.nucleo.ide

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class ResultadoComando(
    val salida: String,
    val error: String = "",
    val codigo: Int = 0,
    val limpiar: Boolean = false,
    val duracionMs: Long = 0,
    val tiempoAgotado: Boolean = false
)

fun interface ComandoInterno {
    suspend operator fun invoke(argumentos: List<String>, directorio: File): ResultadoComando
}

class TerminalLocal(
    val raiz: File,
    private val shell: String = detectarShell(),
    private val internos: Map<String, ComandoInterno> = emptyMap(),
    private val limiteSalida: Int = 200_000
) {
    var directorioActual: File = raiz.canonicalFile
        private set
    private val historialInterno = ArrayDeque<String>()
    val historial: List<String> get() = historialInterno.toList()
    @Volatile private var proceso: Process? = null

    companion object {
        fun detectarShell(): String = listOf("/system/bin/sh", "/bin/sh").firstOrNull { File(it).canExecute() } ?: "sh"

        fun dividir(linea: String): List<String> {
            val partes = mutableListOf<String>()
            val actual = StringBuilder()
            var comilla: Char? = null
            var hay = false
            var i = 0
            while (i < linea.length) {
                val c = linea[i]
                when {
                    comilla != null && c == comilla -> comilla = null
                    comilla != null -> actual.append(c)
                    c == '"' || c == '\'' -> { comilla = c; hay = true }
                    c == '\\' && i + 1 < linea.length -> { actual.append(linea[i + 1]); i++; hay = true }
                    c.isWhitespace() -> if (actual.isNotEmpty() || hay) { partes += actual.toString(); actual.clear(); hay = false }
                    else -> { actual.append(c); hay = true }
                }
                i++
            }
            if (actual.isNotEmpty() || hay) partes += actual.toString()
            return partes
        }
    }

    val rutaVisible: String get() {
        val rel = directorioActual.relativeTo(raiz.canonicalFile).invariantSeparatorsPath
        return if (rel.isEmpty()) "~" else "~/$rel"
    }

    private fun dentroDeRaiz(f: File): Boolean {
        val base = raiz.canonicalFile
        val c = f.canonicalFile
        return c == base || c.path.startsWith(base.path + File.separator)
    }

    fun cancelar() { proceso?.destroyForcibly() }

    suspend fun ejecutar(linea: String, limiteSegundos: Int = 30): ResultadoComando {
        val texto = linea.trim()
        if (texto.isEmpty()) return ResultadoComando("")
        if (historialInterno.lastOrNull() != texto) historialInterno.addLast(texto)
        while (historialInterno.size > 200) historialInterno.removeFirst()
        val partes = dividir(texto)
        val comando = partes.firstOrNull().orEmpty()
        val argumentos = partes.drop(1)
        val sinOperadores = listOf("|", "&&", "||", ";", ">", "<", "`", "$(").none { texto.contains(it) }
        if (sinOperadores) {
            when (comando) {
                "cd" -> return cambiarDirectorio(argumentos.firstOrNull())
                "pwd" -> return ResultadoComando(rutaVisible + "\n")
                "clear", "cls", "limpiar" -> return ResultadoComando("", limpiar = true)
                "history", "historial" -> return ResultadoComando(historialInterno.mapIndexed { i, h -> "${i + 1}  $h" }.joinToString("\n") + "\n")
                "help", "ayuda" -> return ResultadoComando(ayuda())
            }
            internos[comando]?.let { manejador ->
                val inicio = System.nanoTime()
                val r = runCatching { manejador(argumentos, directorioActual) }.getOrElse { ResultadoComando("", it.message ?: "Error", 1) }
                return r.copy(duracionMs = (System.nanoTime() - inicio) / 1_000_000)
            }
        }
        return ejecutarShell(texto, limiteSegundos)
    }

    private fun cambiarDirectorio(destino: String?): ResultadoComando {
        val objetivo = when {
            destino.isNullOrBlank() || destino == "~" -> raiz.canonicalFile
            destino.startsWith("~/") -> File(raiz, destino.drop(2))
            destino.startsWith("/") -> File(raiz, destino.drop(1))
            else -> File(directorioActual, destino)
        }
        if (!dentroDeRaiz(objetivo)) return ResultadoComando("", "cd: no se permite salir de la carpeta del proyecto\n", 1)
        if (!objetivo.isDirectory) return ResultadoComando("", "cd: no existe la carpeta $destino\n", 1)
        directorioActual = objetivo.canonicalFile
        return ResultadoComando("")
    }

    private fun ayuda(): String = buildString {
        append("Terminal de Redact Code\n")
        append("Ejecuta comandos del shell del sistema ($shell) dentro de la carpeta del proyecto.\n")
        append("Comandos internos: cd, pwd, clear, history, help")
        if (internos.isNotEmpty()) append(", ").append(internos.keys.sorted().joinToString(", "))
        append("\nNo es una terminal interactiva: los programas que esperan entrada por teclado reciben fin de archivo.\n")
    }

    private suspend fun ejecutarShell(linea: String, limiteSegundos: Int): ResultadoComando = withContext(Dispatchers.IO) {
        val inicio = System.nanoTime()
        val constructor = ProcessBuilder(shell, "-c", linea).directory(directorioActual)
        constructor.environment().apply {
            put("HOME", raiz.canonicalPath)
            put("PWD", directorioActual.path)
            put("TERM", "dumb")
        }
        val p = runCatching { constructor.start() }.getOrElse {
            return@withContext ResultadoComando("", "No se pudo iniciar el shell: ${it.message}\n", 127)
        }
        proceso = p
        p.outputStream.close()
        val salida = StringBuilder()
        val error = StringBuilder()
        val hiloSalida = Thread { leer(p.inputStream, salida) }.apply { start() }
        val hiloError = Thread { leer(p.errorStream, error) }.apply { start() }
        val termino = p.waitFor(limiteSegundos.toLong(), TimeUnit.SECONDS)
        if (!termino) p.destroyForcibly()
        hiloSalida.join(2000)
        hiloError.join(2000)
        proceso = null
        val duracion = (System.nanoTime() - inicio) / 1_000_000
        if (!termino) ResultadoComando(salida.toString(), error.toString() + "Tiempo agotado: el comando superó $limiteSegundos s y fue detenido\n", 124, duracionMs = duracion, tiempoAgotado = true)
        else ResultadoComando(salida.toString(), error.toString(), p.exitValue(), duracionMs = duracion)
    }

    private fun leer(flujo: InputStream, destino: StringBuilder) {
        val buffer = ByteArray(4096)
        runCatching {
            flujo.use { s ->
                while (true) {
                    val n = s.read(buffer)
                    if (n < 0) break
                    synchronized(destino) {
                        if (destino.length < limiteSalida) destino.append(String(buffer, 0, n, Charsets.UTF_8).take(limiteSalida - destino.length))
                    }
                }
            }
        }
    }

    fun autocompletar(prefijo: String): List<String> {
        val partes = dividir(prefijo)
        val ultima = if (prefijo.endsWith(" ")) "" else partes.lastOrNull().orEmpty()
        val carpeta = if (ultima.contains('/')) File(directorioActual, ultima.substringBeforeLast('/')) else directorioActual
        val inicio = ultima.substringAfterLast('/')
        if (!dentroDeRaiz(carpeta)) return emptyList()
        return carpeta.listFiles()?.filter { it.name.startsWith(inicio) && !it.name.startsWith(".") }
            ?.map { (if (ultima.contains('/')) ultima.substringBeforeLast('/') + "/" else "") + it.name + if (it.isDirectory) "/" else "" }
            ?.sorted().orEmpty()
    }
}
