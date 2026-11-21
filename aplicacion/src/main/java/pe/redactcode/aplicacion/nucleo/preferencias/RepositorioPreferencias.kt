package pe.redactcode.aplicacion.nucleo.preferencias

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pe.redactcode.aplicacion.nucleo.diseno.ModoTema
import pe.redactcode.nucleo.agentes.PanelAntigravity
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.almacenPreferencias: DataStore<Preferences> by preferencesDataStore(name = "preferencias_redact")

enum class FuenteEditor(val nombreVisible: String) { JETBRAINS_MONO("JetBrains Mono"), MONOESPACIADA_SISTEMA("Monoespaciada del sistema") }

enum class OrdenArchivos(val nombreVisible: String) { NOMBRE("Nombre"), TIPO("Tipo"), MODIFICACION("Última modificación") }

enum class GuardadoAutomatico(val nombreVisible: String, val descripcion: String) {
    DESACTIVADO("Desactivado", "Guarda solo cuando lo pidas"),
    AL_CAMBIAR("Al cambiar de pestaña", "Guarda al cambiar de pestaña, ejecutar o salir"),
    CON_RETRASO("Tras una pausa", "Guarda 1,5 s después de dejar de escribir")
}

enum class ProveedorAgenteTipo(val nombreVisible: String) { ANTHROPIC("Claude (Anthropic)"), COMPATIBLE_OPENAI("Compatible con OpenAI") }

data class PreferenciasUsuario(
    val modoTema: ModoTema = ModoTema.OSCURO,
    val escalaTexto: Float = 1f,
    val tamanoFuenteEditor: Int = 14,
    val fuenteEditor: FuenteEditor = FuenteEditor.JETBRAINS_MONO,
    val interlineadoEditor: Float = 1.2f,
    val ligaduras: Boolean = false,
    val autocierre: Boolean = true,
    val sangriaEspacios: Int = 4,
    val ajusteLinea: Boolean = false,
    val validacionEnVivo: Boolean = true,
    val retrasoValidacionMs: Int = 400,
    val numerosLinea: Boolean = true,
    val resaltarLineaActual: Boolean = true,
    val mostrarInvisibles: Boolean = false,
    val autocompletado: Boolean = true,
    val barraSimbolos: Boolean = true,
    val formatearAlGuardar: Boolean = false,
    val stickyScroll: Boolean = true,
    val animaciones: Boolean = true,
    val altoContraste: Boolean = false,
    val objetivosGrandes: Boolean = false,
    val mostrarMigas: Boolean = true,
    val mostrarBarraEstado: Boolean = true,
    val bloqueoPin: Boolean = false,
    val bloqueoBiometrico: Boolean = false,
    val bloquearAlSalir: Boolean = false,
    val recordatoriosRepaso: Boolean = true,
    val horaRecordatorio: Int = 19,
    val consentimientoIA: Boolean = false,
    val proveedorIA: String = PROVEEDOR_IA_LOCAL,
    val modeloIA: String = MODELO_IA_PREDETERMINADO,
    val urlCompatible: String = "",
    val modeloCompatible: String = "",
    val proveedorAgente: ProveedorAgenteTipo = ProveedorAgenteTipo.ANTHROPIC,
    val agenteLeerProyecto: Boolean = true,
    val agenteCrear: Boolean = true,
    val agenteModificar: Boolean = true,
    val agenteEliminar: Boolean = false,
    val agenteMaxArchivos: Int = 30,
    val urlAntigravity: String = PanelAntigravity.URL_PREDETERMINADA,
    val mostrarExtensiones: Boolean = true,
    val ordenArchivos: OrdenArchivos = OrdenArchivos.NOMBRE,
    val confirmarEliminar: Boolean = true,
    val excluirBusqueda: String = "",
    val guardadoAutomatico: GuardadoAutomatico = GuardadoAutomatico.AL_CAMBIAR,
    val limiteEjecucionSegundos: Int = 10,
    val lenguajesSinValidacion: Set<String> = emptySet(),
    val tamanoFuenteTerminal: Int = 13,
    val limiteTerminalSegundos: Int = 30,
    val extensionesDesactivadas: Set<String> = emptySet(),
    val temaEditor: String = TEMA_EDITOR_PREDETERMINADO,
    val gitAutor: String = "",
    val gitCorreo: String = "",
    val ramaPredeterminada: String = "main",
    val confirmarPublicacion: Boolean = true,
    val abrirUltimoProyecto: Boolean = false,
    val ultimoProyectoId: Long = 0,
    val bienvenidaCompletada: Boolean = false
) {
    companion object {
        const val PROVEEDOR_IA_LOCAL = "local"
        const val PROVEEDOR_IA_ANTHROPIC = "anthropic"
        const val MODELO_IA_PREDETERMINADO = "claude-opus-5-5"
        const val TEMA_EDITOR_PREDETERMINADO = "predeterminado"
    }
}

@Singleton
class RepositorioPreferencias @Inject constructor(@ApplicationContext private val contexto: Context) {
    private object Claves {
        val modoTema = stringPreferencesKey("modo_tema")
        val escalaTexto = floatPreferencesKey("escala_texto")
        val tamanoFuenteEditor = intPreferencesKey("tamano_fuente_editor")
        val fuenteEditor = stringPreferencesKey("fuente_editor")
        val interlineado = floatPreferencesKey("interlineado_editor")
        val ligaduras = booleanPreferencesKey("ligaduras")
        val autocierre = booleanPreferencesKey("autocierre")
        val sangriaEspacios = intPreferencesKey("sangria_espacios")
        val ajusteLinea = booleanPreferencesKey("ajuste_linea")
        val validacionEnVivo = booleanPreferencesKey("validacion_en_vivo")
        val retrasoValidacion = intPreferencesKey("retraso_validacion")
        val numerosLinea = booleanPreferencesKey("numeros_linea")
        val resaltarLinea = booleanPreferencesKey("resaltar_linea")
        val invisibles = booleanPreferencesKey("mostrar_invisibles")
        val autocompletado = booleanPreferencesKey("autocompletado")
        val barraSimbolos = booleanPreferencesKey("barra_simbolos")
        val formatearAlGuardar = booleanPreferencesKey("formatear_al_guardar")
        val stickyScroll = booleanPreferencesKey("sticky_scroll")
        val animaciones = booleanPreferencesKey("animaciones")
        val altoContraste = booleanPreferencesKey("alto_contraste")
        val objetivosGrandes = booleanPreferencesKey("objetivos_grandes")
        val mostrarMigas = booleanPreferencesKey("mostrar_migas")
        val mostrarBarraEstado = booleanPreferencesKey("mostrar_barra_estado")
        val bloqueoPin = booleanPreferencesKey("bloqueo_pin")
        val bloqueoBiometrico = booleanPreferencesKey("bloqueo_biometrico")
        val bloquearAlSalir = booleanPreferencesKey("bloquear_al_salir")
        val recordatoriosRepaso = booleanPreferencesKey("recordatorios_repaso")
        val horaRecordatorio = intPreferencesKey("hora_recordatorio")
        val consentimientoIA = booleanPreferencesKey("consentimiento_ia")
        val proveedorIA = stringPreferencesKey("proveedor_ia")
        val modeloIA = stringPreferencesKey("modelo_ia")
        val urlCompatible = stringPreferencesKey("url_compatible")
        val modeloCompatible = stringPreferencesKey("modelo_compatible")
        val proveedorAgente = stringPreferencesKey("proveedor_agente")
        val agenteLeer = booleanPreferencesKey("agente_leer")
        val agenteCrear = booleanPreferencesKey("agente_crear")
        val agenteModificar = booleanPreferencesKey("agente_modificar")
        val agenteEliminar = booleanPreferencesKey("agente_eliminar")
        val agenteMaxArchivos = intPreferencesKey("agente_max_archivos")
        val urlAntigravity = stringPreferencesKey("url_antigravity")
        val mostrarExtensiones = booleanPreferencesKey("mostrar_extensiones")
        val ordenArchivos = stringPreferencesKey("orden_archivos")
        val confirmarEliminar = booleanPreferencesKey("confirmar_eliminar")
        val excluirBusqueda = stringPreferencesKey("excluir_busqueda")
        val guardadoAutomatico = stringPreferencesKey("guardado_automatico")
        val limiteEjecucion = intPreferencesKey("limite_ejecucion")
        val lenguajesSinValidacion = stringSetPreferencesKey("lenguajes_sin_validacion")
        val tamanoFuenteTerminal = intPreferencesKey("tamano_fuente_terminal")
        val limiteTerminal = intPreferencesKey("limite_terminal")
        val extensionesDesactivadas = stringSetPreferencesKey("extensiones_desactivadas")
        val temaEditor = stringPreferencesKey("tema_editor")
        val gitAutor = stringPreferencesKey("git_autor")
        val gitCorreo = stringPreferencesKey("git_correo")
        val ramaPredeterminada = stringPreferencesKey("rama_predeterminada")
        val confirmarPublicacion = booleanPreferencesKey("confirmar_publicacion")
        val abrirUltimoProyecto = booleanPreferencesKey("abrir_ultimo_proyecto")
        val ultimoProyectoId = longPreferencesKey("ultimo_proyecto_id")
        val bienvenidaCompletada = booleanPreferencesKey("bienvenida_completada")
    }

    private inline fun <reified T : Enum<T>> enumDe(valor: String?, porDefecto: T): T =
        valor?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: porDefecto

    val preferencias: Flow<PreferenciasUsuario> = contexto.almacenPreferencias.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val b = PreferenciasUsuario()
            PreferenciasUsuario(
                modoTema = enumDe(p[Claves.modoTema], b.modoTema),
                escalaTexto = p[Claves.escalaTexto] ?: b.escalaTexto,
                tamanoFuenteEditor = p[Claves.tamanoFuenteEditor] ?: b.tamanoFuenteEditor,
                fuenteEditor = enumDe(p[Claves.fuenteEditor], b.fuenteEditor),
                interlineadoEditor = p[Claves.interlineado] ?: b.interlineadoEditor,
                ligaduras = p[Claves.ligaduras] ?: b.ligaduras,
                autocierre = p[Claves.autocierre] ?: b.autocierre,
                sangriaEspacios = p[Claves.sangriaEspacios] ?: b.sangriaEspacios,
                ajusteLinea = p[Claves.ajusteLinea] ?: b.ajusteLinea,
                validacionEnVivo = p[Claves.validacionEnVivo] ?: b.validacionEnVivo,
                retrasoValidacionMs = p[Claves.retrasoValidacion] ?: b.retrasoValidacionMs,
                numerosLinea = p[Claves.numerosLinea] ?: b.numerosLinea,
                resaltarLineaActual = p[Claves.resaltarLinea] ?: b.resaltarLineaActual,
                mostrarInvisibles = p[Claves.invisibles] ?: b.mostrarInvisibles,
                autocompletado = p[Claves.autocompletado] ?: b.autocompletado,
                barraSimbolos = p[Claves.barraSimbolos] ?: b.barraSimbolos,
                formatearAlGuardar = p[Claves.formatearAlGuardar] ?: b.formatearAlGuardar,
                stickyScroll = p[Claves.stickyScroll] ?: b.stickyScroll,
                animaciones = p[Claves.animaciones] ?: b.animaciones,
                altoContraste = p[Claves.altoContraste] ?: b.altoContraste,
                objetivosGrandes = p[Claves.objetivosGrandes] ?: b.objetivosGrandes,
                mostrarMigas = p[Claves.mostrarMigas] ?: b.mostrarMigas,
                mostrarBarraEstado = p[Claves.mostrarBarraEstado] ?: b.mostrarBarraEstado,
                bloqueoPin = p[Claves.bloqueoPin] ?: b.bloqueoPin,
                bloqueoBiometrico = p[Claves.bloqueoBiometrico] ?: b.bloqueoBiometrico,
                bloquearAlSalir = p[Claves.bloquearAlSalir] ?: b.bloquearAlSalir,
                recordatoriosRepaso = p[Claves.recordatoriosRepaso] ?: b.recordatoriosRepaso,
                horaRecordatorio = p[Claves.horaRecordatorio] ?: b.horaRecordatorio,
                consentimientoIA = p[Claves.consentimientoIA] ?: b.consentimientoIA,
                proveedorIA = p[Claves.proveedorIA] ?: b.proveedorIA,
                modeloIA = p[Claves.modeloIA] ?: b.modeloIA,
                urlCompatible = p[Claves.urlCompatible] ?: b.urlCompatible,
                modeloCompatible = p[Claves.modeloCompatible] ?: b.modeloCompatible,
                proveedorAgente = enumDe(p[Claves.proveedorAgente], b.proveedorAgente),
                agenteLeerProyecto = p[Claves.agenteLeer] ?: b.agenteLeerProyecto,
                agenteCrear = p[Claves.agenteCrear] ?: b.agenteCrear,
                agenteModificar = p[Claves.agenteModificar] ?: b.agenteModificar,
                agenteEliminar = p[Claves.agenteEliminar] ?: b.agenteEliminar,
                agenteMaxArchivos = p[Claves.agenteMaxArchivos] ?: b.agenteMaxArchivos,
                urlAntigravity = p[Claves.urlAntigravity] ?: b.urlAntigravity,
                mostrarExtensiones = p[Claves.mostrarExtensiones] ?: b.mostrarExtensiones,
                ordenArchivos = enumDe(p[Claves.ordenArchivos], b.ordenArchivos),
                confirmarEliminar = p[Claves.confirmarEliminar] ?: b.confirmarEliminar,
                excluirBusqueda = p[Claves.excluirBusqueda] ?: b.excluirBusqueda,
                guardadoAutomatico = enumDe(p[Claves.guardadoAutomatico], b.guardadoAutomatico),
                limiteEjecucionSegundos = p[Claves.limiteEjecucion] ?: b.limiteEjecucionSegundos,
                lenguajesSinValidacion = p[Claves.lenguajesSinValidacion] ?: b.lenguajesSinValidacion,
                tamanoFuenteTerminal = p[Claves.tamanoFuenteTerminal] ?: b.tamanoFuenteTerminal,
                limiteTerminalSegundos = p[Claves.limiteTerminal] ?: b.limiteTerminalSegundos,
                extensionesDesactivadas = p[Claves.extensionesDesactivadas] ?: b.extensionesDesactivadas,
                temaEditor = p[Claves.temaEditor] ?: b.temaEditor,
                gitAutor = p[Claves.gitAutor] ?: b.gitAutor,
                gitCorreo = p[Claves.gitCorreo] ?: b.gitCorreo,
                ramaPredeterminada = p[Claves.ramaPredeterminada] ?: b.ramaPredeterminada,
                confirmarPublicacion = p[Claves.confirmarPublicacion] ?: b.confirmarPublicacion,
                abrirUltimoProyecto = p[Claves.abrirUltimoProyecto] ?: b.abrirUltimoProyecto,
                ultimoProyectoId = p[Claves.ultimoProyectoId] ?: b.ultimoProyectoId,
                bienvenidaCompletada = p[Claves.bienvenidaCompletada] ?: b.bienvenidaCompletada
            )
        }

    suspend fun actuales(): PreferenciasUsuario = preferencias.first()

    private suspend fun editar(bloque: (MutablePreferences) -> Unit) { contexto.almacenPreferencias.edit { bloque(it) } }

    suspend fun modoTema(valor: ModoTema) = editar { it[Claves.modoTema] = valor.name }
    suspend fun escalaTexto(valor: Float) = editar { it[Claves.escalaTexto] = valor.coerceIn(0.85f, 1.5f) }
    suspend fun tamanoFuenteEditor(valor: Int) = editar { it[Claves.tamanoFuenteEditor] = valor.coerceIn(10, 28) }
    suspend fun fuenteEditor(valor: FuenteEditor) = editar { it[Claves.fuenteEditor] = valor.name }
    suspend fun interlineadoEditor(valor: Float) = editar { it[Claves.interlineado] = valor.coerceIn(1f, 2f) }
    suspend fun ligaduras(valor: Boolean) = editar { it[Claves.ligaduras] = valor }
    suspend fun autocierre(valor: Boolean) = editar { it[Claves.autocierre] = valor }
    suspend fun sangriaEspacios(valor: Int) = editar { it[Claves.sangriaEspacios] = valor.coerceIn(2, 8) }
    suspend fun ajusteLinea(valor: Boolean) = editar { it[Claves.ajusteLinea] = valor }
    suspend fun validacionEnVivo(valor: Boolean) = editar { it[Claves.validacionEnVivo] = valor }
    suspend fun retrasoValidacion(valor: Int) = editar { it[Claves.retrasoValidacion] = valor.coerceIn(150, 2000) }
    suspend fun numerosLinea(valor: Boolean) = editar { it[Claves.numerosLinea] = valor }
    suspend fun resaltarLineaActual(valor: Boolean) = editar { it[Claves.resaltarLinea] = valor }
    suspend fun mostrarInvisibles(valor: Boolean) = editar { it[Claves.invisibles] = valor }
    suspend fun autocompletado(valor: Boolean) = editar { it[Claves.autocompletado] = valor }
    suspend fun barraSimbolos(valor: Boolean) = editar { it[Claves.barraSimbolos] = valor }
    suspend fun formatearAlGuardar(valor: Boolean) = editar { it[Claves.formatearAlGuardar] = valor }
    suspend fun stickyScroll(valor: Boolean) = editar { it[Claves.stickyScroll] = valor }
    suspend fun animaciones(valor: Boolean) = editar { it[Claves.animaciones] = valor }
    suspend fun altoContraste(valor: Boolean) = editar { it[Claves.altoContraste] = valor }
    suspend fun objetivosGrandes(valor: Boolean) = editar { it[Claves.objetivosGrandes] = valor }
    suspend fun mostrarMigas(valor: Boolean) = editar { it[Claves.mostrarMigas] = valor }
    suspend fun mostrarBarraEstado(valor: Boolean) = editar { it[Claves.mostrarBarraEstado] = valor }
    suspend fun bloqueoPin(valor: Boolean) = editar { it[Claves.bloqueoPin] = valor }
    suspend fun bloqueoBiometrico(valor: Boolean) = editar { it[Claves.bloqueoBiometrico] = valor }
    suspend fun bloquearAlSalir(valor: Boolean) = editar { it[Claves.bloquearAlSalir] = valor }
    suspend fun recordatoriosRepaso(valor: Boolean) = editar { it[Claves.recordatoriosRepaso] = valor }
    suspend fun horaRecordatorio(valor: Int) = editar { it[Claves.horaRecordatorio] = valor.coerceIn(0, 23) }
    suspend fun consentimientoIA(valor: Boolean) = editar { it[Claves.consentimientoIA] = valor }
    suspend fun proveedorIA(valor: String) = editar { it[Claves.proveedorIA] = valor }
    suspend fun modeloIA(valor: String) = editar { it[Claves.modeloIA] = valor.trim() }
    suspend fun urlCompatible(valor: String) = editar { it[Claves.urlCompatible] = valor.trim() }
    suspend fun modeloCompatible(valor: String) = editar { it[Claves.modeloCompatible] = valor.trim() }
    suspend fun proveedorAgente(valor: ProveedorAgenteTipo) = editar { it[Claves.proveedorAgente] = valor.name }
    suspend fun agenteLeerProyecto(valor: Boolean) = editar { it[Claves.agenteLeer] = valor }
    suspend fun agenteCrear(valor: Boolean) = editar { it[Claves.agenteCrear] = valor }
    suspend fun agenteModificar(valor: Boolean) = editar { it[Claves.agenteModificar] = valor }
    suspend fun agenteEliminar(valor: Boolean) = editar { it[Claves.agenteEliminar] = valor }
    suspend fun agenteMaxArchivos(valor: Int) = editar { it[Claves.agenteMaxArchivos] = valor.coerceIn(1, 100) }
    suspend fun urlAntigravity(valor: String) = editar { if (PanelAntigravity.validar(valor) == null) it[Claves.urlAntigravity] = valor.trim() }
    suspend fun mostrarExtensiones(valor: Boolean) = editar { it[Claves.mostrarExtensiones] = valor }
    suspend fun ordenArchivos(valor: OrdenArchivos) = editar { it[Claves.ordenArchivos] = valor.name }
    suspend fun confirmarEliminar(valor: Boolean) = editar { it[Claves.confirmarEliminar] = valor }
    suspend fun excluirBusqueda(valor: String) = editar { it[Claves.excluirBusqueda] = valor.trim() }
    suspend fun guardadoAutomatico(valor: GuardadoAutomatico) = editar { it[Claves.guardadoAutomatico] = valor.name }
    suspend fun limiteEjecucion(valor: Int) = editar { it[Claves.limiteEjecucion] = valor.coerceIn(3, 60) }
    suspend fun validacionLenguaje(lenguaje: String, activa: Boolean) = editar {
        val actual = it[Claves.lenguajesSinValidacion].orEmpty()
        it[Claves.lenguajesSinValidacion] = if (activa) actual - lenguaje else actual + lenguaje
    }
    suspend fun tamanoFuenteTerminal(valor: Int) = editar { it[Claves.tamanoFuenteTerminal] = valor.coerceIn(10, 22) }
    suspend fun limiteTerminal(valor: Int) = editar { it[Claves.limiteTerminal] = valor.coerceIn(5, 300) }
    suspend fun extensionActiva(id: String, activa: Boolean) = editar {
        val actual = it[Claves.extensionesDesactivadas].orEmpty()
        it[Claves.extensionesDesactivadas] = if (activa) actual - id else actual + id
    }
    suspend fun temaEditor(valor: String) = editar { it[Claves.temaEditor] = valor }
    suspend fun gitAutor(valor: String) = editar { it[Claves.gitAutor] = valor.trim().take(80) }
    suspend fun gitCorreo(valor: String) = editar { it[Claves.gitCorreo] = valor.trim().take(120) }
    suspend fun ramaPredeterminada(valor: String) = editar { it[Claves.ramaPredeterminada] = valor.trim() }
    suspend fun confirmarPublicacion(valor: Boolean) = editar { it[Claves.confirmarPublicacion] = valor }
    suspend fun abrirUltimoProyecto(valor: Boolean) = editar { it[Claves.abrirUltimoProyecto] = valor }
    suspend fun ultimoProyecto(id: Long) = editar { it[Claves.ultimoProyectoId] = id }
    suspend fun bienvenidaCompletada(valor: Boolean) = editar { it[Claves.bienvenidaCompletada] = valor }

    suspend fun restablecerAjustes() = editar { p ->
        val bienvenida = p[Claves.bienvenidaCompletada]
        val pin = p[Claves.bloqueoPin]
        val bio = p[Claves.bloqueoBiometrico]
        p.clear()
        bienvenida?.let { p[Claves.bienvenidaCompletada] = it }
        pin?.let { p[Claves.bloqueoPin] = it }
        bio?.let { p[Claves.bloqueoBiometrico] = it }
    }

    suspend fun restablecer() = editar { it.clear() }
}
