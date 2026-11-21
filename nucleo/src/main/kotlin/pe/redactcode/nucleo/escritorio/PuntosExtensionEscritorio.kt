package pe.redactcode.nucleo.escritorio

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import pe.redactcode.nucleo.modelo.ResultadoEjecucion

@Serializable
enum class LenguajeRemoto { JAVA, C, CPP, PYTHON, JAVASCRIPT, KOTLIN }

@Serializable
data class DispositivoEscritorio(val id: String, val nombre: String, val sistemaOperativo: String, val versionAgente: String)

@Serializable
data class CapacidadesEscritorio(
    val lenguajes: Set<LenguajeRemoto>,
    val terminal: Boolean,
    val sincronizacion: Boolean,
    val transferenciaArchivos: Boolean
)

@Serializable
data class InstantaneaArchivo(val rutaRelativa: String, val sha256: String, val tamano: Long, val modificadoEn: Long)

@Serializable
data class InstantaneaProyecto(val proyectoId: String, val nombre: String, val archivos: List<InstantaneaArchivo>, val generadoEn: Long)

@Serializable
enum class TipoCambio { CREADO, MODIFICADO, ELIMINADO, MOVIDO }

@Serializable
data class CambioArchivo(val tipo: TipoCambio, val rutaRelativa: String, val rutaAnterior: String? = null, val sha256: String? = null)

sealed interface EstadoConexionEscritorio {
    data object Desconectado : EstadoConexionEscritorio
    data class Buscando(val desde: Long) : EstadoConexionEscritorio
    data class Conectado(val dispositivo: DispositivoEscritorio, val capacidades: CapacidadesEscritorio) : EstadoConexionEscritorio
    data class Error(val mensaje: String) : EstadoConexionEscritorio
}

interface DescubridorEscritorio {
    val estado: Flow<EstadoConexionEscritorio>
    suspend fun buscar(): List<DispositivoEscritorio>
    suspend fun emparejar(dispositivo: DispositivoEscritorio, codigoVerificacion: String): Boolean
    suspend fun desconectar()
}

interface SincronizadorProyectos {
    suspend fun instantaneaLocal(proyectoId: String): InstantaneaProyecto
    suspend fun calcularCambios(local: InstantaneaProyecto, remota: InstantaneaProyecto): List<CambioArchivo>
    suspend fun aplicar(proyectoId: String, cambios: List<CambioArchivo>, contenido: suspend (String) -> ByteArray)
}

interface EjecutorRemoto {
    val lenguajesSoportados: Set<LenguajeRemoto>
    suspend fun ejecutar(lenguaje: LenguajeRemoto, archivos: Map<String, String>, archivoPrincipal: String, entrada: String, limiteMs: Long): ResultadoEjecucion
}

interface TerminalRemota {
    fun salida(): Flow<String>
    suspend fun enviar(linea: String)
    suspend fun cerrar()
}

interface TransferenciaArchivos {
    suspend fun enviar(rutaRelativa: String, bytes: ByteArray)
    suspend fun recibir(rutaRelativa: String): ByteArray
}

object CompatibilidadEscritorio {
    const val VERSION_PROTOCOLO = 1
    const val DISPONIBLE_EN_ESTA_VERSION = false
}
