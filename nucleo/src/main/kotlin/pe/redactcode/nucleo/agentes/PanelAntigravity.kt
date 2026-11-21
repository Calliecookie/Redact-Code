package pe.redactcode.nucleo.agentes

import java.net.URI

object PanelAntigravity {
    const val URL_PREDETERMINADA = "https://antigravity.google.com"
    private val hostsPermitidos = listOf("antigravity.google", "antigravity.google.com")

    fun validar(url: String): String? {
        val limpia = url.trim()
        val uri = runCatching { URI(limpia) }.getOrNull() ?: return "La dirección no es válida"
        if (uri.scheme != "https") return "La dirección debe empezar con https://"
        val host = uri.host?.lowercase() ?: return "La dirección no tiene servidor"
        if (uri.userInfo != null) return "La dirección no puede incluir usuario ni contraseña"
        if (hostsPermitidos.none { host == it || host.endsWith(".$it") }) return "Solo se permiten direcciones de antigravity.google o antigravity.google.com"
        return null
    }
}
