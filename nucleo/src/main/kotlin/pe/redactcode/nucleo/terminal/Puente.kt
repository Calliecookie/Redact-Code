package pe.redactcode.nucleo.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.redactcode.nucleo.ide.ResultadoComando
import java.io.File

object PuenteComandos {
    val COMANDOS = listOf("python", "python3", "node", "git", "pip", "pip3", "npm", "redact")

    fun generarScript(): String = buildString {
        append(
            """
redact_pedir() {
  REDACT_N=$((${'$'}{REDACT_N:-0}+1))
  _i="${'$'}${'$'}-${'$'}REDACT_N"
  _d="${'$'}REDACT_PUENTE"
  if [ -z "${'$'}_d" ] || [ ! -d "${'$'}_d" ]; then
    echo "El puente de Redact Code no está disponible. Usa la pestaña Python o el botón Ejecutar." >&2
    return 127
  fi
  _c="${'$'}1"
  shift
  { printf '%s\n' "${'$'}_c"; printf '%s\n' "${'$'}PWD"; for _a in "${'$'}@"; do printf '%s\n' "${'$'}_a"; done; } > "${'$'}_d/${'$'}_i.tmp"
  mv "${'$'}_d/${'$'}_i.tmp" "${'$'}_d/${'$'}_i.solicitud"
  trap ': > "${'$'}_d/${'$'}_i.cancelar"' INT
  _o=0
  while [ ! -f "${'$'}_d/${'$'}_i.fin" ]; do
    _t=0
    if [ -f "${'$'}_d/${'$'}_i.salida" ]; then _t=$(wc -c < "${'$'}_d/${'$'}_i.salida" 2>/dev/null); fi
    _t=$((${'$'}{_t:-0}+0))
    if [ "${'$'}_t" -gt "${'$'}_o" ]; then
      tail -c +$((_o+1)) "${'$'}_d/${'$'}_i.salida" 2>/dev/null | head -c $((_t-_o))
      _o=${'$'}_t
    fi
    sleep 0.1
  done
  _t=0
  if [ -f "${'$'}_d/${'$'}_i.salida" ]; then _t=$(wc -c < "${'$'}_d/${'$'}_i.salida" 2>/dev/null); fi
  _t=$((${'$'}{_t:-0}+0))
  if [ "${'$'}_t" -gt "${'$'}_o" ]; then
    tail -c +$((_o+1)) "${'$'}_d/${'$'}_i.salida" 2>/dev/null
  fi
  _r=$(cat "${'$'}_d/${'$'}_i.fin" 2>/dev/null)
  rm -f "${'$'}_d/${'$'}_i.salida" "${'$'}_d/${'$'}_i.fin" "${'$'}_d/${'$'}_i.cancelar" "${'$'}_d/${'$'}_i.solicitud"
  trap - INT
  return $((${'$'}{_r:-1}+0))
}
""".trimStart()
        )
        COMANDOS.forEach { append("$it() { redact_pedir $it \"\$@\"; }\n") }
    }
}

class ServidorPuente(
    private val carpetaPuente: File,
    private val raizProyecto: File,
    private val alcance: CoroutineScope,
    private val intervaloMs: Long = 100,
    private val ejecutor: suspend (comando: String, argumentos: List<String>, directorio: File) -> ResultadoComando
) {
    private var trabajo: Job? = null
    private val enCurso = HashMap<String, Job>()
    private val raizCanonica = raizProyecto.canonicalFile

    fun iniciar() {
        if (trabajo?.isActive == true) return
        carpetaPuente.mkdirs()
        carpetaPuente.listFiles().orEmpty().forEach { it.delete() }
        trabajo = alcance.launch {
            while (isActive) {
                barrer()
                delay(intervaloMs)
            }
        }
    }

    fun detener() {
        trabajo?.cancel()
        enCurso.values.forEach { it.cancel() }
        enCurso.clear()
        carpetaPuente.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun barrer() {
        val archivos = carpetaPuente.listFiles().orEmpty()
        archivos.filter { it.name.endsWith(".cancelar") }.forEach { c ->
            val id = c.name.removeSuffix(".cancelar")
            enCurso[id]?.cancel()
            c.delete()
        }
        archivos.filter { it.name.endsWith(".solicitud") }.forEach { s ->
            val id = s.name.removeSuffix(".solicitud")
            if (enCurso.containsKey(id)) return@forEach
            val lineas = runCatching { s.readLines() }.getOrNull() ?: return@forEach
            enCurso[id] = alcance.launch { atender(id, lineas) }
            s.delete()
        }
        enCurso.entries.removeAll { !it.value.isActive }
    }

    private suspend fun atender(id: String, lineas: List<String>) {
        val salida = File(carpetaPuente, "$id.salida")
        val fin = File(carpetaPuente, "$id.fin")
        salida.writeText("")
        fun terminar(texto: String, codigo: Int) {
            salida.appendText(texto)
            val tmp = File(carpetaPuente, "$id.fin.tmp")
            tmp.writeText(codigo.toString())
            tmp.renameTo(fin)
        }
        val comando = lineas.getOrNull(0).orEmpty()
        val directorio = File(lineas.getOrNull(1).orEmpty()).let { runCatching { it.canonicalFile }.getOrNull() }
        val argumentos = lineas.drop(2)
        if (directorio == null || !(directorio == raizCanonica || directorio.path.startsWith(raizCanonica.path + File.separator))) {
            terminar("Redact Code: el directorio actual está fuera del proyecto\n", 126)
            return
        }
        if (comando !in PuenteComandos.COMANDOS) {
            terminar("Redact Code: comando no disponible: $comando\n", 127)
            return
        }
        try {
            val r = ejecutor(comando, argumentos, directorio)
            terminar(r.salida + r.error, r.codigo)
        } catch (e: kotlinx.coroutines.CancellationException) {
            terminar("Cancelado\n", 130)
            throw e
        } catch (e: Exception) {
            terminar("Error: ${e.message ?: e.javaClass.simpleName}\n", 1)
        }
    }
}
