package pe.redactcode.aplicacion.funcionalidades.ejecucion

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.File

object ConfiguracionVistaPrevia {
    const val DOMINIO = "appassets.androidplatform.net"
    const val RUTA = "/proyecto/"

    fun url(archivo: String): String = "https://$DOMINIO$RUTA${archivo.trim('/')}"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VistaPreviaWeb(
    carpetaProyecto: File,
    archivoHtml: String,
    version: Int,
    alMensajeConsola: (nivel: String, mensaje: String, linea: Int) -> Unit,
    alNavegacionBloqueada: (String) -> Unit,
    alProcesoDetenido: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val cargador = remember(carpetaProyecto) {
        WebViewAssetLoader.Builder()
            .setDomain(ConfiguracionVistaPrevia.DOMINIO)
            .addPathHandler(ConfiguracionVistaPrevia.RUTA, WebViewAssetLoader.InternalStoragePathHandler(contexto, carpetaProyecto))
            .build()
    }
    val vista = remember {
        WebView(contexto).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                setGeolocationEnabled(false)
                javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportMultipleWindows(false)
            }
        }
    }
    DisposableEffect(vista) {
        onDispose {
            vista.stopLoading()
            vista.destroy()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            vista.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    cargador.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.host == ConfiguracionVistaPrevia.DOMINIO) return false
                    alNavegacionBloqueada(request.url.toString())
                    return true
                }

                override fun onRenderProcessGone(view: WebView, detalle: RenderProcessGoneDetail): Boolean {
                    alProcesoDetenido()
                    return true
                }
            }
            vista.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(mensaje: ConsoleMessage): Boolean {
                    val nivel = when (mensaje.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> "error"
                        ConsoleMessage.MessageLevel.WARNING -> "aviso"
                        else -> "log"
                    }
                    alMensajeConsola(nivel, mensaje.message(), mensaje.lineNumber())
                    return true
                }
            }
            vista
        },
        update = { web ->
            val url = ConfiguracionVistaPrevia.url(archivoHtml) + "?v=$version"
            if (web.tag != url) {
                web.tag = url
                web.clearCache(true)
                web.loadUrl(url)
            }
        }
    )
}
