package pe.redactcode.aplicacion.datos

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.ProveedorAgenteTipo
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.aplicacion.nucleo.seguridad.AlmacenCredenciales
import pe.redactcode.nucleo.agentes.ErrorAgente
import pe.redactcode.nucleo.agentes.MensajeAgente
import pe.redactcode.nucleo.agentes.PermisosAgente
import pe.redactcode.nucleo.agentes.ProveedorAgente
import pe.redactcode.nucleo.agentes.ProveedorCompatibleOpenAI
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

class ProveedorAgenteAnthropic(private val clave: String, private val modelo: String) : ProveedorAgente {
    override val nombre = "Claude · $modelo"

    override suspend fun completar(sistema: String, mensajes: List<MensajeAgente>): String = withContext(Dispatchers.IO) {
        val cliente = AnthropicOkHttpClient.builder().apiKey(clave).timeout(Duration.ofSeconds(240)).maxRetries(2).build()
        try {
            val constructor = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(16000L)
                .system(sistema)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            mensajes.forEach { m ->
                if (m.rol == MensajeAgente.ASISTENTE) constructor.addAssistantMessage(m.texto) else constructor.addUserMessage(m.texto)
            }
            val respuesta = cliente.messages().create(constructor.build())
            if (respuesta.stopReason().map { it == StopReason.REFUSAL }.orElse(false)) throw ErrorAgente("El proveedor rechazó la solicitud")
            if (respuesta.stopReason().map { it == StopReason.MAX_TOKENS }.orElse(false)) throw ErrorAgente("La respuesta se cortó por longitud; pide cambios más pequeños o en menos archivos")
            respuesta.content().mapNotNull { b -> b.text().map { it.text() }.orElse(null) }.joinToString("\n").trim()
                .ifEmpty { throw ErrorAgente("El proveedor no devolvió texto") }
        } catch (e: UnauthorizedException) {
            throw ErrorAgente("La clave de API no es válida")
        } catch (e: PermissionDeniedException) {
            throw ErrorAgente("La clave no tiene permiso para usar el modelo $modelo")
        } catch (e: RateLimitException) {
            throw ErrorAgente("Se alcanzó el límite de solicitudes; intenta más tarde")
        } catch (e: AnthropicServiceException) {
            throw ErrorAgente("El servicio respondió con el código ${e.statusCode()}")
        } catch (e: AnthropicIoException) {
            throw ErrorAgente("No hay conexión con el servicio de IA")
        } finally {
            cliente.close()
        }
    }
}

data class EstadoProveedorAgente(val proveedor: ProveedorAgente?, val motivo: String?)

@Singleton
class FabricaProveedoresAgente @Inject constructor(
    private val credenciales: AlmacenCredenciales,
    private val preferencias: RepositorioPreferencias
) {
    suspend fun obtener(): EstadoProveedorAgente {
        val p = preferencias.actuales()
        if (!p.consentimientoIA) return EstadoProveedorAgente(null, "Activa el consentimiento para enviar código a un proveedor de IA en Ajustes › Inteligencia artificial")
        return when (p.proveedorAgente) {
            ProveedorAgenteTipo.ANTHROPIC -> {
                val clave = credenciales.leer(AlmacenCredenciales.CLAVE_IA) ?: return EstadoProveedorAgente(null, "Configura tu clave de API de Anthropic en Ajustes › Inteligencia artificial")
                EstadoProveedorAgente(ProveedorAgenteAnthropic(clave, p.modeloIA.ifBlank { PreferenciasUsuario.MODELO_IA_PREDETERMINADO }), null)
            }
            ProveedorAgenteTipo.COMPATIBLE_OPENAI -> {
                if (p.urlCompatible.isBlank() || p.modeloCompatible.isBlank()) return EstadoProveedorAgente(null, "Configura la dirección y el modelo del proveedor compatible en Ajustes › Inteligencia artificial")
                ProveedorCompatibleOpenAI.validarUrl(p.urlCompatible)?.let { return EstadoProveedorAgente(null, it) }
                val clave = credenciales.leer(AlmacenCredenciales.CLAVE_COMPATIBLE)
                EstadoProveedorAgente(ProveedorCompatibleOpenAI("${p.modeloCompatible} (compatible)", p.urlCompatible, { clave }, p.modeloCompatible), null)
            }
        }
    }

    suspend fun permisos(): PermisosAgente {
        val p = preferencias.actuales()
        return PermisosAgente(p.agenteLeerProyecto, p.agenteCrear, p.agenteModificar, p.agenteEliminar, p.agenteMaxArchivos)
    }
}
