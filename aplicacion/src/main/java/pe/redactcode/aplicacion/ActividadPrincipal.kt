package pe.redactcode.aplicacion

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        destinoSolicitado.value = intent?.getStringExtra(EXTRA_DESTINO)
        setContent {
            val preferencias by repositorioPreferencias.preferencias.collectAsStateWithLifecycle(initialValue = PreferenciasUsuario())
            TemaRedactCode(
                modo = preferencias.modoTema,
                escalaTexto = preferencias.escalaTexto,
                altoContraste = preferencias.altoContraste,
                animaciones = preferencias.animaciones,
                objetivosGrandes = preferencias.objetivosGrandes
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AppRedactCode(
                        destinoInicial = destinoSolicitado.value,
                        alConsumirDestino = { destinoSolicitado.value = null }
                    )
                }
            }
        }
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
    }
}
