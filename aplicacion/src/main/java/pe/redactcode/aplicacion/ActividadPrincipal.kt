package pe.redactcode.aplicacion

import android.animation.ValueAnimator
import android.app.UiModeManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import android.view.animation.PathInterpolator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import pe.redactcode.aplicacion.navegacion.EstadoAcceso
import pe.redactcode.aplicacion.nucleo.diseno.EntradaMarca
import pe.redactcode.aplicacion.nucleo.diseno.entradaSuave
import pe.redactcode.aplicacion.nucleo.diseno.ModoTema
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pe.redactcode.aplicacion.navegacion.AccesoViewModel
import pe.redactcode.aplicacion.navegacion.AppRedactCode
import pe.redactcode.aplicacion.nucleo.diseno.TemaRedactCode
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import javax.inject.Inject

@AndroidEntryPoint
class ActividadPrincipal : FragmentActivity() {
    @Inject lateinit var repositorioPreferencias: RepositorioPreferencias

    private val destinoSolicitado = mutableStateOf<String?>(null)
    private val accesoViewModel: AccesoViewModel by viewModels()
    private var instanteSegundoPlano = 0L

    private var tiempoAgotado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val ajustesArranque = getSharedPreferences(ARCHIVO_ARRANQUE, MODE_PRIVATE)
        val animar = EntradaMarca.debeAnimarEntrada(
            savedInstanceState == null,
            runCatching { ajustesArranque.getBoolean(CLAVE_ANIMACIONES, true) }.getOrDefault(true),
            ValueAnimator.areAnimatorsEnabled()
        )
        Handler(Looper.getMainLooper()).postDelayed({ tiempoAgotado = true }, TIEMPO_MAXIMO_SPLASH_MS)
        splash.setKeepOnScreenCondition { accesoViewModel.estado.value == EstadoAcceso.Cargando && !tiempoAgotado }
        splash.setOnExitAnimationListener { proveedor ->
            val icono = runCatching { proveedor.iconView }.getOrNull()
            if (!animar || icono == null) {
                proveedor.remove()
            } else {
                val interpolador = PathInterpolator(0.2f, 0f, 0f, 1f)
                icono.animate().scaleX(0.92f).scaleY(0.92f).alpha(0f).setDuration(220).setInterpolator(interpolador).start()
                proveedor.view.animate().alpha(0f).setStartDelay(40).setDuration(180).setInterpolator(interpolador)
                    .withEndAction { proveedor.remove() }.start()
            }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        destinoSolicitado.value = intent?.getStringExtra(EXTRA_DESTINO)
        setContent {
            val preferencias by repositorioPreferencias.preferencias.collectAsStateWithLifecycle(initialValue = PreferenciasUsuario())
            LaunchedEffect(preferencias.animaciones) {
                ajustesArranque.edit { putBoolean(CLAVE_ANIMACIONES, preferencias.animaciones) }
            }
            LaunchedEffect(preferencias.modoTema) { aplicarModoNocturno(preferencias.modoTema) }
            TemaRedactCode(
                modo = preferencias.modoTema,
                escalaTexto = preferencias.escalaTexto,
                altoContraste = preferencias.altoContraste,
                animaciones = preferencias.animaciones,
                objetivosGrandes = preferencias.objetivosGrandes
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Box(Modifier.fillMaxSize().entradaSuave(animar, retardoMs = 60, duracionMs = 200, desplazamiento = 8.dp, clave = "arranque")) {
                        AppRedactCode(
                            destinoInicial = destinoSolicitado.value,
                            alConsumirDestino = { destinoSolicitado.value = null }
                        )
                    }
                }
            }
        }
    }

    private fun aplicarModoNocturno(modo: ModoTema) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val destino = when (modo) {
            ModoTema.OSCURO -> UiModeManager.MODE_NIGHT_YES
            ModoTema.CLARO -> UiModeManager.MODE_NIGHT_NO
            ModoTema.SISTEMA -> UiModeManager.MODE_NIGHT_AUTO
        }
        val ajustes = getSharedPreferences(ARCHIVO_ARRANQUE, MODE_PRIVATE)
        if (ajustes.getInt(CLAVE_MODO_NOCTURNO, -1) == destino) return
        val gestor = getSystemService(UiModeManager::class.java) ?: return
        if (runCatching { gestor.setApplicationNightMode(destino) }.isSuccess) ajustes.edit { putInt(CLAVE_MODO_NOCTURNO, destino) }
    }

    override fun onStop() {
        super.onStop()
        instanteSegundoPlano = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val ausencia = SystemClock.elapsedRealtime() - instanteSegundoPlano
        if (instanteSegundoPlano > 0 && ausencia > RETRASO_BLOQUEO_MS) {
            lifecycleScope.launch {
                val p = repositorioPreferencias.actuales()
                if (p.bloqueoPin && p.bloquearAlSalir) accesoViewModel.bloquear()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        destinoSolicitado.value = intent.getStringExtra(EXTRA_DESTINO)
    }

    companion object {
        const val EXTRA_DESTINO = "destino"
        const val RETRASO_BLOQUEO_MS = 30_000L
        const val TIEMPO_MAXIMO_SPLASH_MS = 2_000L
        const val ARCHIVO_ARRANQUE = "ajustes_arranque"
        const val CLAVE_ANIMACIONES = "animaciones_cache"
        const val CLAVE_MODO_NOCTURNO = "modo_nocturno_aplicado"
    }
}
