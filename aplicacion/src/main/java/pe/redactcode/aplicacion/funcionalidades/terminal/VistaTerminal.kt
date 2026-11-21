package pe.redactcode.aplicacion.funcionalidades.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import pe.redactcode.aplicacion.R
import pe.redactcode.aplicacion.nucleo.diseno.LocalTokens

class ModificadoresTeclas {
    var control by mutableStateOf(false)
    var alternativa by mutableStateOf(false)
}

private data class AjusteVista(val oscuro: Boolean, val fondo: androidx.compose.ui.graphics.Color, val pixeles: Int, val sesion: Int)

object LimitesFuente {
    const val MINIMO = 10
    const val MAXIMO = 22
    fun acotar(valor: Int) = valor.coerceIn(MINIMO, MAXIMO)
}

private class ClienteVista(
    private val vista: () -> TerminalView?,
    private val modificadores: ModificadoresTeclas,
    private val tamano: () -> Int,
    private val alCambiarTamano: (Int) -> Unit
) : TerminalViewClient {
    private var escala = 1f
    override fun onScale(factor: Float): Float {
        escala *= factor
        if (escala > 1.15f || escala < 0.87f) {
            val nuevo = LimitesFuente.acotar(if (escala > 1f) tamano() + 1 else tamano() - 1)
            escala = 1f
            alCambiarTamano(nuevo)
        }
        return escala
    }
    override fun onSingleTapUp(evento: MotionEvent) {
        val v = vista() ?: return
        v.requestFocus()
        (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, 0)
    }
    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(activo: Boolean) {}
    override fun onKeyDown(codigo: Int, evento: KeyEvent, sesion: TerminalSession): Boolean = false
    override fun onKeyUp(codigo: Int, evento: KeyEvent): Boolean = false
    override fun onLongPress(evento: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = modificadores.control.also { if (it) modificadores.control = false }
    override fun readAltKey(): Boolean = modificadores.alternativa.also { if (it) modificadores.alternativa = false }
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(punto: Int, control: Boolean, sesion: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}
    override fun logError(etiqueta: String?, mensaje: String?) {}
    override fun logWarn(etiqueta: String?, mensaje: String?) {}
    override fun logInfo(etiqueta: String?, mensaje: String?) {}
    override fun logDebug(etiqueta: String?, mensaje: String?) {}
    override fun logVerbose(etiqueta: String?, mensaje: String?) {}
    override fun logStackTraceWithMessage(etiqueta: String?, mensaje: String?, e: Exception?) {}
    override fun logStackTrace(etiqueta: String?, e: Exception?) {}
}

@Composable
fun VistaTerminal(
    sesion: SesionPty,
    modificadores: ModificadoresTeclas,
    tamanoSp: Int,
    alCambiarTamano: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val recursos = LocalResources.current
    val tokens = LocalTokens.current
    val tamanoActual by rememberUpdatedState(tamanoSp)
    val cambiar by rememberUpdatedState(alCambiarTamano)
    val referencia = remember { arrayOfNulls<TerminalView>(1) }
    val tipografia = remember { ResourcesCompat.getFont(contexto, R.font.jetbrains_mono_regular) }

    DisposableEffect(sesion) {
        val portapapeles = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        sesion.alActualizar = { referencia[0]?.post { referencia[0]?.onScreenUpdated() } }
        sesion.alCopiar = { texto -> portapapeles.setPrimaryClip(ClipData.newPlainText("terminal", texto)) }
        sesion.alPegar = {
            val texto = portapapeles.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(contexto)?.toString()
            if (!texto.isNullOrEmpty()) sesion.sesion?.emulator?.paste(texto)
        }
        onDispose { sesion.alActualizar = null; sesion.alCopiar = null; sesion.alPegar = null }
    }

    AndroidView(
        modifier = modifier,
        factory = { c ->
            TerminalView(c, null).also { v ->
                referencia[0] = v
                v.setTerminalViewClient(ClienteVista({ referencia[0] }, modificadores, { tamanoActual }, { cambiar(it) }))
                val escala = recursos.displayMetrics.density * recursos.configuration.fontScale
                v.setTextSize((tamanoSp * escala).toInt().coerceAtLeast(1))
                tipografia?.let { v.setTypeface(it) }
                v.isFocusable = true
                v.isFocusableInTouchMode = true
                sesion.sesion?.let { v.attachSession(it) }
            }
        },
        update = { v ->
            val nativa = sesion.sesion
            if (nativa != null && v.currentSession !== nativa) v.attachSession(nativa)
            val escala = recursos.displayMetrics.density * recursos.configuration.fontScale
            val pixeles = (tamanoSp * escala).toInt().coerceAtLeast(1)
            val aplicado = AjusteVista(tokens.esOscuro, tokens.panelInferior, pixeles, sesion.id)
            if (v.tag != aplicado) {
                val anterior = v.tag as? AjusteVista
                TerminalColors.COLOR_SCHEME.updateWith(PaletaAnsi.aPropiedades(PaletaAnsi.crear(tokens)))
                nativa?.emulator?.mColors?.reset()
                if (anterior?.pixeles != pixeles) {
                    v.setTextSize(pixeles)
                    tipografia?.let { v.setTypeface(it) }
                }
                v.tag = aplicado
                v.contentDescription = "Terminal ${sesion.titulo}"
                v.onScreenUpdated()
            }
        }
    )
}
