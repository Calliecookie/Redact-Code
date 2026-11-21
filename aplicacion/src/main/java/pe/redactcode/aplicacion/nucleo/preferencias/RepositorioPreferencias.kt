package pe.redactcode.aplicacion.nucleo.preferencias

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pe.redactcode.aplicacion.nucleo.diseno.ModoTema
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.almacenPreferencias: DataStore<Preferences> by preferencesDataStore(name = "preferencias_redact")

enum class FuenteEditor(val nombreVisible: String) { JETBRAINS_MONO("JetBrains Mono"), MONOESPACIADA_SISTEMA("Monoespaciada del sistema") }

enum class OrdenArchivos(val nombreVisible: String) { NOMBRE("Nombre"), TIPO("Tipo"), MODIFICACION("Última modificación") }

data class PreferenciasUsuario(
    val modoTema: ModoTema = ModoTema.OSCURO,
    val escalaTexto: Float = 1f,
    val tamanoFuenteEditor: Int = 14,
    val fuenteEditor: FuenteEditor = FuenteEditor.JETBRAINS_MONO,
    val autocierre: Boolean = true,
    val sangriaEspacios: Int = 4,
    val ajusteLinea: Boolean = false,
    val validacionEnVivo: Boolean = true,
    val animaciones: Boolean = true,
    val altoContraste: Boolean = false,
    val bloqueoPin: Boolean = false,
    val bloqueoBiometrico: Boolean = false,
    val recordatoriosRepaso: Boolean = true,
    val horaRecordatorio: Int = 19,
    val consentimientoIA: Boolean = false,
    val proveedorIA: String = PROVEEDOR_IA_LOCAL,
    val modeloIA: String = MODELO_IA_PREDETERMINADO,
    val mostrarExtensiones: Boolean = true,
    val ordenArchivos: OrdenArchivos = OrdenArchivos.NOMBRE,
    val limiteEjecucionSegundos: Int = 10,
    val bienvenidaCompletada: Boolean = false
) {
    companion object {
        const val PROVEEDOR_IA_LOCAL = "local"
        const val PROVEEDOR_IA_ANTHROPIC = "anthropic"
        const val MODELO_IA_PREDETERMINADO = "claude-opus-5-5"
    }
}

@Singleton
class RepositorioPreferencias @Inject constructor(@ApplicationContext private val contexto: Context) {
    private object Claves {
        val modoTema = stringPreferencesKey("modo_tema")
        val escalaTexto = floatPreferencesKey("escala_texto")
        val tamanoFuenteEditor = intPreferencesKey("tamano_fuente_editor")
        val fuenteEditor = stringPreferencesKey("fuente_editor")
        val autocierre = booleanPreferencesKey("autocierre")
        val sangriaEspacios = intPreferencesKey("sangria_espacios")
        val ajusteLinea = booleanPreferencesKey("ajuste_linea")
        val validacionEnVivo = booleanPreferencesKey("validacion_en_vivo")
        val animaciones = booleanPreferencesKey("animaciones")
        val altoContraste = booleanPreferencesKey("alto_contraste")
        val bloqueoPin = booleanPreferencesKey("bloqueo_pin")
        val bloqueoBiometrico = booleanPreferencesKey("bloqueo_biometrico")
        val recordatoriosRepaso = booleanPreferencesKey("recordatorios_repaso")
        val horaRecordatorio = intPreferencesKey("hora_recordatorio")
        val consentimientoIA = booleanPreferencesKey("consentimiento_ia")
        val proveedorIA = stringPreferencesKey("proveedor_ia")
        val modeloIA = stringPreferencesKey("modelo_ia")
        val mostrarExtensiones = booleanPreferencesKey("mostrar_extensiones")
        val ordenArchivos = stringPreferencesKey("orden_archivos")
        val limiteEjecucion = intPreferencesKey("limite_ejecucion")
        val bienvenidaCompletada = booleanPreferencesKey("bienvenida_completada")
    }

    val preferencias: Flow<PreferenciasUsuario> = contexto.almacenPreferencias.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val base = PreferenciasUsuario()
            PreferenciasUsuario(
                modoTema = p[Claves.modoTema]?.let { runCatching { ModoTema.valueOf(it) }.getOrNull() } ?: base.modoTema,
                escalaTexto = p[Claves.escalaTexto] ?: base.escalaTexto,
                tamanoFuenteEditor = p[Claves.tamanoFuenteEditor] ?: base.tamanoFuenteEditor,
                fuenteEditor = p[Claves.fuenteEditor]?.let { runCatching { FuenteEditor.valueOf(it) }.getOrNull() } ?: base.fuenteEditor,
                autocierre = p[Claves.autocierre] ?: base.autocierre,
                sangriaEspacios = p[Claves.sangriaEspacios] ?: base.sangriaEspacios,
                ajusteLinea = p[Claves.ajusteLinea] ?: base.ajusteLinea,
                validacionEnVivo = p[Claves.validacionEnVivo] ?: base.validacionEnVivo,
                animaciones = p[Claves.animaciones] ?: base.animaciones,
                altoContraste = p[Claves.altoContraste] ?: base.altoContraste,
                bloqueoPin = p[Claves.bloqueoPin] ?: base.bloqueoPin,
                bloqueoBiometrico = p[Claves.bloqueoBiometrico] ?: base.bloqueoBiometrico,
                recordatoriosRepaso = p[Claves.recordatoriosRepaso] ?: base.recordatoriosRepaso,
                horaRecordatorio = p[Claves.horaRecordatorio] ?: base.horaRecordatorio,
                consentimientoIA = p[Claves.consentimientoIA] ?: base.consentimientoIA,
                proveedorIA = p[Claves.proveedorIA] ?: base.proveedorIA,
                modeloIA = p[Claves.modeloIA] ?: base.modeloIA,
                mostrarExtensiones = p[Claves.mostrarExtensiones] ?: base.mostrarExtensiones,
                ordenArchivos = p[Claves.ordenArchivos]?.let { runCatching { OrdenArchivos.valueOf(it) }.getOrNull() } ?: base.ordenArchivos,
                limiteEjecucionSegundos = p[Claves.limiteEjecucion] ?: base.limiteEjecucionSegundos,
                bienvenidaCompletada = p[Claves.bienvenidaCompletada] ?: base.bienvenidaCompletada
            )
        }

    suspend fun actuales(): PreferenciasUsuario = preferencias.first()

    suspend fun modoTema(valor: ModoTema) = contexto.almacenPreferencias.edit { it[Claves.modoTema] = valor.name }
    suspend fun escalaTexto(valor: Float) = contexto.almacenPreferencias.edit { it[Claves.escalaTexto] = valor.coerceIn(0.85f, 1.35f) }
    suspend fun tamanoFuenteEditor(valor: Int) = contexto.almacenPreferencias.edit { it[Claves.tamanoFuenteEditor] = valor.coerceIn(10, 24) }
    suspend fun fuenteEditor(valor: FuenteEditor) = contexto.almacenPreferencias.edit { it[Claves.fuenteEditor] = valor.name }
    suspend fun autocierre(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.autocierre] = valor }
    suspend fun sangriaEspacios(valor: Int) = contexto.almacenPreferencias.edit { it[Claves.sangriaEspacios] = valor.coerceIn(2, 8) }
    suspend fun ajusteLinea(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.ajusteLinea] = valor }
    suspend fun validacionEnVivo(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.validacionEnVivo] = valor }
    suspend fun animaciones(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.animaciones] = valor }
    suspend fun altoContraste(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.altoContraste] = valor }
    suspend fun bloqueoPin(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.bloqueoPin] = valor }
    suspend fun bloqueoBiometrico(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.bloqueoBiometrico] = valor }
    suspend fun recordatoriosRepaso(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.recordatoriosRepaso] = valor }
    suspend fun horaRecordatorio(valor: Int) = contexto.almacenPreferencias.edit { it[Claves.horaRecordatorio] = valor.coerceIn(0, 23) }
    suspend fun consentimientoIA(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.consentimientoIA] = valor }
    suspend fun proveedorIA(valor: String) = contexto.almacenPreferencias.edit { it[Claves.proveedorIA] = valor }
    suspend fun modeloIA(valor: String) = contexto.almacenPreferencias.edit { it[Claves.modeloIA] = valor.trim() }
    suspend fun mostrarExtensiones(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.mostrarExtensiones] = valor }
    suspend fun ordenArchivos(valor: OrdenArchivos) = contexto.almacenPreferencias.edit { it[Claves.ordenArchivos] = valor.name }
    suspend fun limiteEjecucion(valor: Int) = contexto.almacenPreferencias.edit { it[Claves.limiteEjecucion] = valor.coerceIn(3, 60) }
    suspend fun bienvenidaCompletada(valor: Boolean) = contexto.almacenPreferencias.edit { it[Claves.bienvenidaCompletada] = valor }

    suspend fun restablecer() = contexto.almacenPreferencias.edit { it.clear() }
}
