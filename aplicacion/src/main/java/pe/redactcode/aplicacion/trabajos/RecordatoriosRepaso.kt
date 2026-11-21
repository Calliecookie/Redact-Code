package pe.redactcode.aplicacion.trabajos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import pe.redactcode.aplicacion.ActividadPrincipal
import pe.redactcode.aplicacion.R
import pe.redactcode.aplicacion.datos.RepositorioFragmentos
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@HiltWorker
class TrabajadorRecordatorioRepaso @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted parametros: WorkerParameters,
    private val fragmentos: RepositorioFragmentos,
    private val preferencias: RepositorioPreferencias
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        if (!preferencias.actuales().recordatoriosRepaso) return Result.success()
        val pendientes = fragmentos.contarPendientes()
        if (pendientes > 0) mostrarNotificacion(applicationContext, pendientes)
        return Result.success()
    }

    companion object {
        const val CANAL = "repasos"
        private const val NOMBRE_TRABAJO = "recordatorio_repaso_diario"

        fun crearCanal(contexto: Context) {
            val canal = NotificationChannel(CANAL, contexto.getString(R.string.canal_repasos_nombre), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = contexto.getString(R.string.canal_repasos_descripcion)
            }
            contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }

        fun programar(contexto: Context, hora: Int) {
            val ahora = LocalDateTime.now()
            var objetivo = ahora.withHour(hora).withMinute(0).withSecond(0).withNano(0)
            if (!objetivo.isAfter(ahora)) objetivo = objetivo.plusDays(1)
            val retraso = Duration.between(ahora, objetivo).toMinutes()
            val solicitud = PeriodicWorkRequestBuilder<TrabajadorRecordatorioRepaso>(1, TimeUnit.DAYS)
                .setInitialDelay(retraso, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(NOMBRE_TRABAJO, ExistingPeriodicWorkPolicy.UPDATE, solicitud)
        }

        fun cancelar(contexto: Context) = WorkManager.getInstance(contexto).cancelUniqueWork(NOMBRE_TRABAJO)

        private fun mostrarNotificacion(contexto: Context, pendientes: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val intencion = PendingIntent.getActivity(
                contexto, 0,
                Intent(contexto, ActividadPrincipal::class.java).putExtra(ActividadPrincipal.EXTRA_DESTINO, "repaso"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notificacion = NotificationCompat.Builder(contexto, CANAL)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle("Tienes repasos pendientes")
                .setContentText(if (pendientes == 1) "1 fragmento listo para repasar" else "$pendientes fragmentos listos para repasar")
                .setContentIntent(intencion)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(contexto).notify(1001, notificacion)
        }
    }
}
