package pe.redactcode.nucleo.terminal

enum class NivelRiesgo { BAJO, MEDIO, ALTO, BLOQUEADO }

data class EvaluacionRiesgo(val nivel: NivelRiesgo, val motivo: String)

object ClasificadorRiesgo {
    private val bajos = setOf(
        "ls", "cat", "pwd", "echo", "grep", "head", "tail", "wc", "sort", "uniq", "stat", "date", "which", "whoami",
        "help", "history", "clear", "printf", "true", "false", "basename", "dirname", "tree", "find", "diff", "cut", "tr", "du", "df", "env", "uname", "id"
    )
    private val medios = setOf("mkdir", "touch", "mv", "cp", "tee", "python", "python3", "node", "pip", "pip3", "npm", "git", "redact", "cd", "sed", "awk", "export", "ln")
    private val bloqueados = setOf("dd", "mkfs", "su", "sudo", "reboot", "shutdown", "halt", "poweroff", "eval", "chroot", "mount", "umount", "insmod", "rmmod", "setprop", "pm", "am", "svc", "settings", "su", "nohup", "setsid", "exec")
    private val altos = setOf("curl", "wget", "ssh", "scp", "nc", "ncat", "telnet", "kill", "killall", "pkill", "chmod", "chown", "rmdir", "ftp", "sh", "bash", "mksh", "source", ".")
    private val prefijos = setOf("env", "command", "time", "xargs", "builtin")

    fun clasificar(linea: String, nombreProyecto: String = ""): EvaluacionRiesgo {
        val bruto = linea.trim()
        if (bruto.isEmpty()) return EvaluacionRiesgo(NivelRiesgo.BAJO, "Línea vacía")
        if (bruto.contains(":(){") || bruto.contains(":() {")) return EvaluacionRiesgo(NivelRiesgo.BLOQUEADO, "Bomba de procesos")
        val segmentos = dividir(bruto)
        var peor = EvaluacionRiesgo(NivelRiesgo.BAJO, "Solo lectura")
        var salioDelProyecto = false
        for (seg in segmentos) {
            val r = evaluarSegmento(seg, salioDelProyecto)
            if (r.salio) salioDelProyecto = true
            if (r.evaluacion.nivel.ordinal > peor.nivel.ordinal) peor = r.evaluacion
        }
        if (Regex("""\|\s*(sudo\s+)?(ba|mk|z)?sh\b""").containsMatchIn(bruto)) {
            return EvaluacionRiesgo(NivelRiesgo.BLOQUEADO, "Descarga o texto enviado directamente a un intérprete")
        }
        return peor
    }

    private data class ResultadoSegmento(val evaluacion: EvaluacionRiesgo, val salio: Boolean)

    internal fun dividir(linea: String): List<String> {
        val limpio = linea.replace("\\\n", " ")
        val internos = Regex("""\$\(([^()]*)\)|`([^`]*)`|<\(([^()]*)\)""").findAll(limpio).flatMap { m -> m.groupValues.drop(1).filter { it.isNotEmpty() }.flatMap(::dividir) }.toList()
        val sinInternos = limpio.replace(Regex("""\$\(([^()]*)\)|`([^`]*)`|<\(([^()]*)\)"""), " ")
        val partes = sinInternos.split(Regex("""&&|\|\||[;&|\n]"""))
        return partes.map { it.trim() }.filter { it.isNotEmpty() } + internos
    }

    internal fun tokens(segmento: String): List<String> {
        val resultado = mutableListOf<String>()
        val actual = StringBuilder()
        var comilla: Char? = null
        var i = 0
        var hay = false
        while (i < segmento.length) {
            val c = segmento[i]
            when {
                comilla != null -> if (c == comilla) comilla = null else actual.append(c)
                c == '\'' || c == '"' -> { comilla = c; hay = true }
                c == '\\' && i + 1 < segmento.length -> { actual.append(segmento[i + 1]); i++; hay = true }
                c.isWhitespace() -> if (hay || actual.isNotEmpty()) { resultado += actual.toString(); actual.clear(); hay = false }
                else -> { actual.append(c); hay = true }
            }
            i++
        }
        if (hay || actual.isNotEmpty()) resultado += actual.toString()
        return resultado
    }

    private fun fueraDelProyecto(ruta: String): Boolean {
        if (ruta == "/dev/null") return false
        if (ruta.startsWith("/") || ruta.startsWith("~") || ruta.startsWith("$")) return true
        return ruta.split('/').any { it == ".." }
    }

    private fun evaluarSegmento(segmento: String, salioAntes: Boolean): ResultadoSegmento {
        var t = tokens(segmento).toMutableList()
        while (t.isNotEmpty() && Regex("""^[A-Za-z_][A-Za-z0-9_]*=.*""").matches(t.first())) t.removeAt(0)
        while (t.isNotEmpty() && t.first().substringAfterLast('/') in prefijos) t.removeAt(0)
        if (t.isEmpty()) return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.BAJO, "Asignación de variable"), false)
        val nombre = t.first().substringAfterLast('/')
        val args = t.drop(1)
        val rutas = args.filter { !it.startsWith("-") }
        val banderas = args.filter { it.startsWith("-") }
        val destinos = Regex("""\d*>>?\s*([^\s;|&<>]+)""").findAll(segmento).map { it.groupValues[1].trim('"', '\'') }.toList()
        val escribeFuera = destinos.any { fueraDelProyecto(it) }
        if (escribeFuera) return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.BLOQUEADO, "Escribe fuera del proyecto"), false)
        if (nombre in bloqueados || nombre.startsWith("mkfs")) return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.BLOQUEADO, "Comando prohibido: $nombre"), false)
        if (nombre == "rm") {
            val recursivo = banderas.any { b -> b.startsWith("--") && (b == "--recursive" || b == "--force" || b == "--no-preserve-root") || (!b.startsWith("--") && b.any { it in "rRf" }) }
            val peligrosa = rutas.any { fueraDelProyecto(it) || it == "." || it == "*" || it == "./*" } || salioAntes || rutas.isEmpty()
            return when {
                recursivo || peligrosa -> ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.BLOQUEADO, "Borrado destructivo"), false)
                else -> ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.ALTO, "Borra archivos del proyecto"), false)
            }
        }
        if ((nombre == "chmod" || nombre == "chown") && banderas.any { it.contains('R') }) return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.BLOQUEADO, "Cambio recursivo de permisos"), false)
        if (nombre == "kill" && args.contains("-1")) return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.BLOQUEADO, "Mata todos los procesos"), false)
        if (nombre == "cd") {
            val destino = rutas.firstOrNull().orEmpty()
            return if (destino.isEmpty() || !fueraDelProyecto(destino)) ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.BAJO, "Cambio de directorio dentro del proyecto"), false)
            else ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.ALTO, "Sale del proyecto"), true)
        }
        if (nombre == "find" && args.any { it == "-delete" || it == "-exec" || it == "-execdir" || it == "-ok" }) {
            val inicio = args.indexOfFirst { it == "-exec" || it == "-execdir" || it == "-ok" }
            val interno = if (inicio >= 0) args.drop(inicio + 1).takeWhile { it != ";" && it != "+" }.filter { it != "{}" } else emptyList()
            val anidado = if (interno.isNotEmpty()) evaluarSegmento(interno.joinToString(" ") { if (it.contains(' ')) "'$it'" else it }, salioAntes).evaluacion else null
            if (anidado != null && anidado.nivel == NivelRiesgo.BLOQUEADO) return ResultadoSegmento(anidado, false)
            if (rutas.any { fueraDelProyecto(it) }) return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.BLOQUEADO, "find con acciones fuera del proyecto"), false)
            return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.ALTO, "find con acciones"), false)
        }
        if ((nombre == "mv" || nombre == "cp" || nombre == "ln" || nombre == "tee" || nombre == "touch" || nombre == "mkdir") && rutas.any { fueraDelProyecto(it) })
            return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.BLOQUEADO, "Modifica fuera del proyecto"), false)
        if (nombre == "git") {
            val sub = args.firstOrNull { !it.startsWith("-") }.orEmpty()
            return ResultadoSegmento(
                when {
                    sub in setOf("status", "log", "diff", "show", "branch") && !banderas.any { it == "-d" || it == "-D" } -> EvaluacionRiesgo(NivelRiesgo.BAJO, "Consulta de git")
                    sub == "push" || (sub == "reset" && args.contains("--hard")) || sub == "clean" || sub == "clone" || (sub == "branch" && banderas.isNotEmpty()) -> EvaluacionRiesgo(NivelRiesgo.ALTO, "git que publica o descarta cambios")
                    else -> EvaluacionRiesgo(NivelRiesgo.MEDIO, "git modifica el repositorio local")
                },
                false
            )
        }
        if ((nombre == "pip" || nombre == "pip3") && args.firstOrNull() == "uninstall") return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.ALTO, "Desinstala paquetes"), false)
        if ((nombre == "pip" || nombre == "pip3" || nombre == "npm") && args.firstOrNull() in setOf("list", "show", "ls")) return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.BAJO, "Consulta de paquetes"), false)
        if (nombre == "sed" && banderas.any { it.startsWith("-i") }) return ResultadoSegmento(EvaluacionRiesgo(NivelRiesgo.MEDIO, "Edita archivos en el lugar"), false)
        val redirige = Regex("""\d*>>?(?!\s*&)""").containsMatchIn(segmento) && !Regex("""\d*>>?\s*/dev/null""").containsMatchIn(segmento)
        val base = when {
            nombre in altos -> EvaluacionRiesgo(NivelRiesgo.ALTO, "Comando de red, procesos o permisos: $nombre")
            nombre in medios -> EvaluacionRiesgo(NivelRiesgo.MEDIO, "Modifica archivos o ejecuta código: $nombre")
            nombre in bajos -> EvaluacionRiesgo(NivelRiesgo.BAJO, "Solo lectura")
            else -> EvaluacionRiesgo(NivelRiesgo.ALTO, "Comando desconocido: $nombre")
        }
        val conRedireccion = if (redirige && base.nivel == NivelRiesgo.BAJO) EvaluacionRiesgo(NivelRiesgo.MEDIO, "Escribe en un archivo del proyecto") else base
        val final = if (conRedireccion.nivel.ordinal < NivelRiesgo.ALTO.ordinal && rutas.any { fueraDelProyecto(it) }) EvaluacionRiesgo(NivelRiesgo.ALTO, "Accede a rutas fuera del proyecto") else conRedireccion
        return ResultadoSegmento(final, false)
    }
}

object CensorSecretos {
    const val MARCA = "[SECRETO]"
    private val patrones = listOf(
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----"""),
        Regex("""sk-ant-[A-Za-z0-9_\-]{8,}"""),
        Regex("""sk-[A-Za-z0-9_\-]{16,}"""),
        Regex("""github_pat_[A-Za-z0-9_]{20,}"""),
        Regex("""gh[pousr]_[A-Za-z0-9]{20,}"""),
        Regex("""AKIA[0-9A-Z]{16}"""),
        Regex("""AIza[0-9A-Za-z_\-]{30,}"""),
        Regex("""xox[abprs]-[A-Za-z0-9\-]{10,}"""),
        Regex("""eyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}"""),
        Regex("""(?i)(bearer|basic)\s+[A-Za-z0-9._~+/=\-]{12,}"""),
        Regex("""(?i)(authorization|x-api-key|api-key)\s*[:=]\s*\S+"""),
        Regex("""(?i)\b([A-Za-z0-9_]*(api[_-]?key|secret|token|password|passwd|clave)[A-Za-z0-9_]*)\s*[:=]\s*["']?[^\s"']{4,}["']?""")
    )

    fun censurar(texto: String): String = patrones.fold(texto) { acc, p -> p.replace(acc, MARCA) }

    fun contieneSecretos(texto: String): Boolean = patrones.any { it.containsMatchIn(texto) }
}
