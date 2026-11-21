package pe.redactcode.aplicacion.funcionalidades.terminal

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pe.redactcode.nucleo.terminal.PuenteComandos
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EstadoSesion {
    data object Activa : EstadoSesion
    data class Terminada(val codigo: Int) : EstadoSesion
    data class Fallida(val motivo: String) : EstadoSesion
}

class SesionPty(
    val id: Int,
    var titulo: String,
    val sesion: TerminalSession?,
    inicial: EstadoSesion
) {
    private val _estado = MutableStateFlow(inicial)
    val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()
    @Volatile var alActualizar: (() -> Unit)? = null
    @Volatile var alTitulo: (() -> Unit)? = null
    @Volatile var alPegar: (() -> Unit)? = null
    @Volatile var alCopiar: ((String) -> Unit)? = null

    internal fun terminar(codigo: Int) { _estado.value = EstadoSesion.Terminada(codigo) }

    fun interrumpir() { sesion?.write(byteArrayOf(3), 0, 1) }

    fun escribir(texto: String) {
        val bytes = texto.toByteArray(Charsets.UTF_8)
        sesion?.write(bytes, 0, bytes.size)
    }

    fun cerrar() { sesion?.finishIfRunning() }

    fun transcripcion(): String = sesion?.emulator?.screen?.transcriptTextWithoutJoinedLines.orEmpty()
}

object ScriptTerminal {
    fun generar(conPuente: Boolean = false): String = listOf(
        "PS1='\${PWD##*/} \$ '",
        "alias ll='ls -la'",
        "alias la='ls -A'",
        "export TERM=xterm-256color"
    ).joinToString("\n", postfix = "\n") + if (conPuente) PuenteComandos.generarScript() else ""

    fun entorno(base: File, proyecto: File, rc: File, puente: File? = null): Array<String> = arrayOf(
        "TERM=xterm-256color",
        "HOME=${base.absolutePath}",
        "PWD=${proyecto.absolutePath}",
        "ENV=${rc.absolutePath}",
        "TMPDIR=${base.absolutePath}/tmp",
        "LANG=en_US.UTF-8",
        "PATH=/system/bin:/system/xbin",
        "COLORTERM=truecolor"
    ) + listOfNotNull(puente?.let { "REDACT_PUENTE=${it.absolutePath}" })
}

@Singleton
class GestorSesionesTerminal @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val puente: ServicioPuenteTerminal
) {
    private val porProyecto = HashMap<Long, MutableStateFlow<List<SesionPty>>>()
    private var contador = 0
    private val bloqueo = Any()
    internal var cargadorNativo: () -> Unit = {
        System.loadLibrary("termux")
        Class.forName("com.termux.terminal.JNI")
    }

    companion object {
        const val LIMITE_SESIONES = 8
        const val FILAS_HISTORIAL = 3000
    }

    fun sesiones(proyectoId: Long): StateFlow<List<SesionPty>> = synchronized(bloqueo) {
        porProyecto.getOrPut(proyectoId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    private fun flujo(proyectoId: Long) = synchronized(bloqueo) { porProyecto.getOrPut(proyectoId) { MutableStateFlow(emptyList()) } }

    fun crear(proyectoId: Long, carpetaProyecto: File): SesionPty? {
        val flujo = flujo(proyectoId)
        if (flujo.value.size >= LIMITE_SESIONES) return null
        val numero = synchronized(bloqueo) { ++contador }
        val base = File(contexto.filesDir, "terminal").apply { mkdirs() }
        File(base, "tmp").mkdirs()
        val rc = File(base, ".mkshrc").apply { writeText(ScriptTerminal.generar(conPuente = true)) }
        val carpetaPuente = puente.asegurar(proyectoId, carpetaProyecto)
        lateinit var sesionPty: SesionPty
        val cliente = ClienteSesion { sesionPty }
        val resultado = runCatching {
            cargadorNativo()
            TerminalSession(
                "/system/bin/sh", carpetaProyecto.absolutePath, arrayOf("sh"),
                ScriptTerminal.entorno(base, carpetaProyecto, rc, carpetaPuente), FILAS_HISTORIAL, cliente
            )
        }
        sesionPty = resultado.fold(
            { SesionPty(numero, "sh $numero", it, EstadoSesion.Activa) },
            {
                val causa = if (it is ExceptionInInitializerError) it.cause ?: it else it
                SesionPty(numero, "sh $numero", null, EstadoSesion.Fallida(causa.message ?: causa.javaClass.simpleName))
            }
        )
        flujo.update { it + sesionPty }
        return sesionPty
    }

    fun cerrar(proyectoId: Long, id: Int) {
        val flujo = flujo(proyectoId)
        flujo.value.firstOrNull { it.id == id }?.cerrar()
        flujo.update { lista -> lista.filterNot { it.id == id } }
        if (flujo.value.isEmpty()) puente.detener(proyectoId)
    }

    fun cerrarTodas(proyectoId: Long) {
        val flujo = flujo(proyectoId)
        flujo.value.forEach { it.cerrar() }
        flujo.value = emptyList()
        puente.detener(proyectoId)
    }
}

private class ClienteSesion(private val obtener: () -> SesionPty) : TerminalSessionClient {
    override fun onTextChanged(cambiada: TerminalSession) { obtener().alActualizar?.invoke() }
    override fun onTitleChanged(cambiada: TerminalSession) {
        cambiada.title?.takeIf { it.isNotBlank() }?.let { obtener().titulo = it }
        obtener().alTitulo?.invoke()
    }
    override fun onSessionFinished(terminada: TerminalSession) {
        obtener().terminar(terminada.exitStatus)
        obtener().alActualizar?.invoke()
    }
    override fun onCopyTextToClipboard(sesion: TerminalSession, texto: String) { obtener().alCopiar?.invoke(texto) }
    override fun onPasteTextFromClipboard(sesion: TerminalSession?) { obtener().alPegar?.invoke() }
    override fun onBell(sesion: TerminalSession) {}
    override fun onColorsChanged(sesion: TerminalSession) { obtener().alActualizar?.invoke() }
    override fun onTerminalCursorStateChange(habilitado: Boolean) {}
    override fun getTerminalCursorStyle(): Int? = null
    override fun logError(etiqueta: String?, mensaje: String?) {}
    override fun logWarn(etiqueta: String?, mensaje: String?) {}
    override fun logInfo(etiqueta: String?, mensaje: String?) {}
    override fun logDebug(etiqueta: String?, mensaje: String?) {}
    override fun logVerbose(etiqueta: String?, mensaje: String?) {}
    override fun logStackTraceWithMessage(etiqueta: String?, mensaje: String?, e: Exception?) {}
    override fun logStackTrace(etiqueta: String?, e: Exception?) {}
}
