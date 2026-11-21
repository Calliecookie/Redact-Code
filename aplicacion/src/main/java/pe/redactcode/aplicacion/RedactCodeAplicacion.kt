package pe.redactcode.aplicacion

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.datos.RepositorioGitHub
import pe.redactcode.aplicacion.datos.RepositorioRetos
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.aplicacion.trabajos.TrabajadorRecordatorioRepaso
import javax.inject.Inject

@HiltAndroidApp
class RedactCodeAplicacion : Application(), Configuration.Provider {
    @Inject lateinit var fabricaTrabajadores: HiltWorkerFactory
    @Inject lateinit var retos: RepositorioRetos
    @Inject lateinit var github: RepositorioGitHub
    @Inject lateinit var preferencias: RepositorioPreferencias

    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaTrabajadores).build()

    override fun onCreate() {
        super.onCreate()
        TrabajadorRecordatorioRepaso.crearCanal(this)
        alcance.launch {
            runCatching { retos.sincronizarCatalogo() }
            runCatching { github.cargarEstado() }
            val p = preferencias.actuales()
            if (p.recordatoriosRepaso && p.bienvenidaCompletada) TrabajadorRecordatorioRepaso.programar(this@RedactCodeAplicacion, p.horaRecordatorio)
        }
    }
}
