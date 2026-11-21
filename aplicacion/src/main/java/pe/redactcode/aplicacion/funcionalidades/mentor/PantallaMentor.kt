package pe.redactcode.aplicacion.funcionalidades.mentor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.datos.RepositorioAprendizaje
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.nucleo.diseno.BloqueCodigo
import pe.redactcode.aplicacion.nucleo.diseno.BotonPrincipal
import pe.redactcode.aplicacion.nucleo.diseno.BotonSecundario
import pe.redactcode.aplicacion.nucleo.diseno.CampoTexto
import pe.redactcode.aplicacion.nucleo.diseno.Cargando
import pe.redactcode.aplicacion.nucleo.diseno.EncabezadoPantalla
import pe.redactcode.aplicacion.nucleo.diseno.EstadoVacio
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.Separador
import pe.redactcode.aplicacion.nucleo.diseno.Superficie
import pe.redactcode.aplicacion.nucleo.diseno.FuenteMonoJetBrains
import pe.redactcode.nucleo.mentor.RespuestaMentor
import pe.redactcode.nucleo.mentor.SolicitudMentor
import pe.redactcode.nucleo.mentor.TipoSolicitud
import pe.redactcode.nucleo.modelo.CategoriaConcepto
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.revision.Hallazgo
import javax.inject.Inject

data class EstadoMentor(
    val tipo: TipoSolicitud = TipoSolicitud.EXPLICAR_CODIGO,
    val lenguaje: Lenguaje = Lenguaje.PYTHON,
    val codigo: String = "",
    val pregunta: String = "",
    val concepto: CategoriaConcepto = CategoriaConcepto.VARIABLES,
    val diagnostico: Diagnostico? = null,
    val hallazgos: List<Hallazgo> = emptyList(),
    val cargando: Boolean = false,
    val respuesta: RespuestaMentor? = null,
    val error: String? = null
)

@HiltViewModel
class MentorViewModel @Inject constructor(
    private val servicio: ServicioCodigo,
    private val aprendizaje: RepositorioAprendizaje
) : ViewModel() {
    val estado = MutableStateFlow(EstadoMentor())

    init {
        SolicitudMentorCompartida.tomar()?.let { p ->
            estado.value = EstadoMentor(tipo = p.tipo, lenguaje = p.lenguaje, codigo = p.codigo, diagnostico = p.diagnostico, hallazgos = p.hallazgos)
            consultar()
        }
    }

    fun tipo(t: TipoSolicitud) = estado.update { it.copy(tipo = t, respuesta = null) }
    fun lenguaje(l: Lenguaje) = estado.update { it.copy(lenguaje = l) }
    fun codigo(c: String) = estado.update { it.copy(codigo = c, diagnostico = null, hallazgos = emptyList()) }
    fun pregunta(p: String) = estado.update { it.copy(pregunta = p) }
    fun concepto(c: CategoriaConcepto) = estado.update { it.copy(concepto = c) }

    fun consultar() {
        val e = estado.value
        viewModelScope.launch {
            estado.update { it.copy(cargando = true, error = null) }
            try {
                val contexto = aprendizaje.contexto()
                var diagnostico = e.diagnostico
                var hallazgos = e.hallazgos
                if ((e.tipo == TipoSolicitud.EXPLICAR_ERROR || e.tipo == TipoSolicitud.SUGERIR_CORRECCION) && diagnostico == null && e.codigo.isNotBlank()) {
                    diagnostico = servicio.validar(e.lenguaje, e.codigo).errores.firstOrNull()
                }
                if ((e.tipo == TipoSolicitud.RECOMENDAR_MEJORAS || e.tipo == TipoSolicitud.MALAS_PRACTICAS) && hallazgos.isEmpty() && e.codigo.isNotBlank()) {
                    val diagnosticos = servicio.validar(e.lenguaje, e.codigo).diagnosticos
                    hallazgos = servicio.revisar(e.lenguaje, e.codigo, diagnosticos).hallazgos
                }
                val respuesta = servicio.mentor.responder(
                    SolicitudMentor(e.tipo, e.lenguaje, e.codigo, contexto, diagnostico, if (e.tipo == TipoSolicitud.EXPLICAR_CONCEPTO || e.tipo == TipoSolicitud.PROPONER_EJERCICIO) e.concepto else null, hallazgos, e.pregunta)
                )
                estado.update { it.copy(cargando = false, respuesta = respuesta, diagnostico = diagnostico, hallazgos = hallazgos) }
            } catch (ex: Exception) {
                estado.update { it.copy(cargando = false, error = ex.message ?: "No se pudo generar la explicación") }
            }
        }
    }
}

@Composable
fun PantallaMentor(alVolver: (() -> Unit)?, alAbrirReto: (String) -> Unit, vm: MentorViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val ext = LocalColoresExtendidos.current
    val necesitaCodigo = estado.tipo != TipoSolicitud.EXPLICAR_CONCEPTO && estado.tipo != TipoSolicitud.PROPONER_EJERCICIO
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { EncabezadoPantalla("Mentor de programación", "Explicaciones adaptadas a tu nivel", alVolver = alVolver) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TipoSolicitud.entries.forEach { t -> Etiqueta(t.nombreVisible, seleccionada = estado.tipo == t, alPulsar = { vm.tipo(t) }) }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Lenguaje.entries.filter { it != Lenguaje.TEXTO }.forEach { l -> Etiqueta(l.nombreVisible, seleccionada = estado.lenguaje == l, alPulsar = { vm.lenguaje(l) }) }
            }
        }
        if (!necesitaCodigo) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoriaConcepto.entries.forEach { c -> Etiqueta(c.nombreVisible, seleccionada = estado.concepto == c, alPulsar = { vm.concepto(c) }) }
                }
            }
        } else {
            item { CampoTexto(estado.codigo, vm::codigo, "Código", unaLinea = false, monoespaciado = true, lineasMinimas = 6) }
            estado.diagnostico?.let { d ->
                item { Text("Error seleccionado: ${d.titulo} (línea ${d.linea})", style = MaterialTheme.typography.bodySmall, color = ext.error) }
            }
        }
        item { CampoTexto(estado.pregunta, vm::pregunta, "Pregunta adicional (opcional, se usa con IA externa)") }
        item { BotonPrincipal(if (estado.cargando) "Analizando…" else "Preguntar al mentor", vm::consultar, Modifier.fillMaxWidth(), habilitado = !estado.cargando && (!necesitaCodigo || estado.codigo.isNotBlank())) }
        if (estado.cargando) item { Cargando(Modifier.height(80.dp)) }
        estado.error?.let { item { Text(it, color = ext.error, style = MaterialTheme.typography.bodyMedium) } }
        val r = estado.respuesta
        if (r == null && !estado.cargando) {
            item { EstadoVacio(Icons.Outlined.Psychology, "¿En qué te ayudo?", "Pega tu código o ábrelo desde el editor. El mentor funciona sin internet con explicaciones locales; puedes activar un proveedor de IA en Ajustes.") }
        }
        if (r != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.titulo, style = MaterialTheme.typography.titleLarge)
                    Text(if (r.esLocal) "Fuente: ${r.proveedor} (sin conexión)" else "Fuente: ${r.proveedor}", style = MaterialTheme.typography.labelSmall, color = ext.textoTenue)
                    r.aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ext.advertencia) }
                }
            }
            items(r.secciones) { s ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.titulo, style = MaterialTheme.typography.titleSmall)
                    if (s.texto.isNotBlank()) Text(s.texto, style = MaterialTheme.typography.bodyMedium)
                    s.codigo?.let { BloqueCodigo(it) }
                }
            }
            if (r.lineas.isNotEmpty()) {
                item { Separador() }
                items(r.lineas) { l ->
                    Row(Modifier.fillMaxWidth().padding(start = (l.nivel * 12).dp), verticalAlignment = Alignment.Top) {
                        Text("${l.linea}".padStart(3), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FuenteMonoJetBrains), color = ext.textoTenue)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(l.codigo, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FuenteMonoJetBrains), fontWeight = FontWeight.Medium, maxLines = 2)
                            Text(l.explicacion, style = MaterialTheme.typography.bodySmall, color = ext.textoTenue)
                        }
                    }
                }
            }
            r.propuesta?.let { p ->
                item {
                    Superficie {
                        Text("Corrección propuesta · línea ${p.linea}", style = MaterialTheme.typography.titleSmall)
                        Text(p.descripcion, style = MaterialTheme.typography.bodySmall, color = ext.textoTenue)
                        Spacer(Modifier.height(8.dp))
                        BloqueCodigo(p.corregida.trim())
                    }
                }
            }
            r.retoSugerido?.let { reto ->
                item { BotonSecundario("Practicar: ${reto.titulo}", { alAbrirReto(reto.id) }, Modifier.fillMaxWidth()) }
            }
        }
    }
}
