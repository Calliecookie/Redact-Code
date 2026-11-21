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
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.aplicacion.nucleo.seguridad.AlmacenCredenciales
import pe.redactcode.nucleo.mentor.ConstructorPrompt
import pe.redactcode.nucleo.mentor.ProveedorIA
import pe.redactcode.nucleo.mentor.RespuestaMentor
import pe.redactcode.nucleo.mentor.SolicitudMentor
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProveedorAnthropic @Inject constructor(
    private val credenciales: AlmacenCredenciales,
    private val preferencias: RepositorioPreferencias
) : ProveedorIA {
    override val id = PreferenciasUsuario.PROVEEDOR_IA_ANTHROPIC
    override val nombre = "Claude (Anthropic)"
    override val requiereRed = true

    override suspend fun disponible(): Boolean {
        val p = preferencias.actuales()
        return p.proveedorIA == id && p.consentimientoIA && credenciales.leer(AlmacenCredenciales.CLAVE_IA) != null
    }

    override suspend fun responder(solicitud: SolicitudMentor): RespuestaMentor = withContext(Dispatchers.IO) {
        val clave = credenciales.leer(AlmacenCredenciales.CLAVE_IA) ?: throw IllegalStateException("No hay una clave de API configurada")
        val modelo = preferencias.actuales().modeloIA.ifBlank { PreferenciasUsuario.MODELO_IA_PREDETERMINADO }
        val cliente = AnthropicOkHttpClient.builder()
            .apiKey(clave)
            .timeout(Duration.ofSeconds(120))
            .maxRetries(2)
            .build()
        try {
            val parametros = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(8000L)
                .system(ConstructorPrompt.sistema(solicitud.contexto))
                .addUserMessage(ConstructorPrompt.usuario(solicitud))
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()
            val mensaje = cliente.messages().create(parametros)
            if (mensaje.stopReason().map { it == StopReason.REFUSAL }.orElse(false)) {
                throw IllegalStateException("El proveedor rechazó la solicitud")
            }
            val texto = mensaje.content().mapNotNull { bloque -> bloque.text().map { it.text() }.orElse(null) }.joinToString("\n").trim()
            if (texto.isEmpty()) throw IllegalStateException("El proveedor no devolvió texto")
            RespuestaMentor(
                titulo = solicitud.tipo.nombreVisible,
                secciones = ConstructorPrompt.interpretar(texto),
                proveedor = "$nombre · ${mensaje.model().asString()}",
                esLocal = false,
                aviso = "Respuesta generada por un servicio externo de IA. Verifica la información antes de aplicarla."
            )
        } catch (e: UnauthorizedException) {
            throw IllegalStateException("La clave de API no es válida")
        } catch (e: PermissionDeniedException) {
            throw IllegalStateException("La clave no tiene permiso para usar el modelo $modelo")
        } catch (e: RateLimitException) {
            throw IllegalStateException("Se alcanzó el límite de solicitudes; intenta más tarde")
        } catch (e: AnthropicServiceException) {
            throw IllegalStateException("El servicio respondió con el código ${e.statusCode()}")
        } catch (e: AnthropicIoException) {
            throw IllegalStateException("No hay conexión con el servicio de IA")
        } finally {
            cliente.close()
        }
    }
}
