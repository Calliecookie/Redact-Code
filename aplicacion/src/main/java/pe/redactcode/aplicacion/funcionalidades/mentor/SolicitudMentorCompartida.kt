package pe.redactcode.aplicacion.funcionalidades.mentor

import pe.redactcode.nucleo.mentor.TipoSolicitud
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.revision.Hallazgo

data class SolicitudPendiente(
    val tipo: TipoSolicitud,
    val lenguaje: Lenguaje,
    val codigo: String,
    val diagnostico: Diagnostico?,
    val hallazgos: List<Hallazgo>
)

object SolicitudMentorCompartida {
    @Volatile
    private var pendiente: SolicitudPendiente? = null

    fun establecer(tipo: TipoSolicitud, lenguaje: Lenguaje, codigo: String, diagnostico: Diagnostico?, hallazgos: List<Hallazgo>) {
        pendiente = SolicitudPendiente(tipo, lenguaje, codigo, diagnostico, hallazgos)
    }

    fun tomar(): SolicitudPendiente? = pendiente.also { pendiente = null }
}
