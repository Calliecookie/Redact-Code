package pe.redactcode.aplicacion.datos

import android.content.Context
import android.net.Uri
import androidx.core.graphics.toColorInt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.redactcode.aplicacion.nucleo.diseno.ColoresEditor
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.nucleo.extensiones.ErrorExtension
import pe.redactcode.nucleo.extensiones.FragmentoExtension
import pe.redactcode.nucleo.extensiones.FragmentosBase
import pe.redactcode.nucleo.extensiones.LectorExtensiones
import pe.redactcode.nucleo.extensiones.ManifiestoExtension
import pe.redactcode.nucleo.extensiones.TemaExtension
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ExtensionInstalada(val manifiesto: ManifiestoExtension, val integrada: Boolean, val activa: Boolean)

@Singleton
class RepositorioExtensiones @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val preferencias: RepositorioPreferencias
) {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val carpeta: File by lazy { File(contexto.filesDir, "extensiones").apply { mkdirs() } }
    private val _instaladas = MutableStateFlow<List<ManifiestoExtension>>(emptyList())
    val manifiestos: StateFlow<List<ManifiestoExtension>> = _instaladas.asStateFlow()

    val extensiones: StateFlow<List<ExtensionInstalada>> = combine(_instaladas, preferencias.preferencias) { lista, p ->
        (listOf(FragmentosBase.manifiesto) + lista).map { ExtensionInstalada(it, it.id == FragmentosBase.ID, it.id !in p.extensionesDesactivadas) }
    }.stateIn(alcance, SharingStarted.Eagerly, emptyList())

    init { alcance.launch { recargar() } }

    suspend fun recargar() = withContext(Dispatchers.IO) {
        _instaladas.value = carpeta.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            runCatching { LectorExtensiones.validar(kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<ManifiestoExtension>(f.readText())) }.getOrNull()
        }.sortedBy { it.nombre.lowercase() }
    }

    suspend fun instalar(uri: Uri): ManifiestoExtension = withContext(Dispatchers.IO) {
        val bytes = contexto.contentResolver.openInputStream(uri)?.use { entrada ->
            val datos = LectorExtensiones.leerLimitado(entrada, LectorExtensiones.TAMANO_MAXIMO_PAQUETE + 1)
            if (datos.size > LectorExtensiones.TAMANO_MAXIMO_PAQUETE) throw ErrorExtension("El archivo supera 20 MB")
            datos
        } ?: throw ErrorExtension("No se pudo leer el archivo")
        instalarBytes(bytes)
    }

    suspend fun instalarBytes(bytes: ByteArray): ManifiestoExtension = withContext(Dispatchers.IO) {
        val esZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val manifiesto = if (esZip) LectorExtensiones.desdeVsix(bytes) else LectorExtensiones.desdeJsonRedact(bytes.toString(Charsets.UTF_8))
        if (manifiesto.id == FragmentosBase.ID) throw ErrorExtension("Ese identificador está reservado")
        val destino = File(carpeta, "${manifiesto.id}.json")
        val temporal = File(carpeta, ".${manifiesto.id}.tmp")
        temporal.writeText(LectorExtensiones.exportar(manifiesto))
        destino.delete()
        if (!temporal.renameTo(destino)) throw ErrorExtension("No se pudo guardar la extensión")
        recargar()
        manifiesto
    }

    suspend fun desinstalar(id: String) = withContext(Dispatchers.IO) {
        if (id == FragmentosBase.ID) throw ErrorExtension("La extensión integrada no se puede desinstalar; puedes desactivarla")
        File(carpeta, "$id.json").delete()
        val p = preferencias.actuales()
        if (p.temaEditor.startsWith("$id:")) preferencias.temaEditor(PreferenciasUsuario.TEMA_EDITOR_PREDETERMINADO)
        recargar()
    }

    fun fragmentosActivos(): List<FragmentoExtension> = extensiones.value.filter { it.activa }.flatMap { it.manifiesto.fragmentos }

    fun temas(): List<Pair<String, TemaExtension>> = extensiones.value.filter { it.activa }.flatMap { e -> e.manifiesto.temas.mapIndexed { i, t -> "${e.manifiesto.id}:$i" to t } }

    companion object {
        fun aplicarTema(base: ColoresEditor, tema: TemaExtension?): ColoresEditor {
            if (tema == null) return base
            fun c(clave: String, actual: androidx.compose.ui.graphics.Color) =
                tema.colores[clave]?.let { runCatching { androidx.compose.ui.graphics.Color(it.toColorInt()) }.getOrNull() } ?: actual
            return base.copy(
                fondo = c("fondo", base.fondo), fondoNumeros = c("fondo", base.fondoNumeros), texto = c("texto", base.texto),
                lineaActual = c("lineaActual", base.lineaActual), numeroLinea = c("numeroLinea", base.numeroLinea),
                seleccion = c("seleccion", base.seleccion), cursor = c("cursor", base.cursor), palabraClave = c("palabraClave", base.palabraClave),
                cadena = c("cadena", base.cadena), numero = c("numero", base.numero), comentario = c("comentario", base.comentario),
                funcion = c("funcion", base.funcion), tipo = c("tipo", base.tipo), operador = c("operador", base.operador),
                etiqueta = c("etiqueta", base.etiqueta), atributo = c("atributo", base.atributo)
            )
        }
    }
}
