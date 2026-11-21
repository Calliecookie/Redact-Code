package pe.redactcode.aplicacion.funcionalidades.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.datos.ArchivoReciente
import pe.redactcode.aplicacion.datos.RepositorioActividad
import pe.redactcode.aplicacion.datos.RepositorioAprendizaje
import pe.redactcode.aplicacion.datos.RepositorioFragmentos
import pe.redactcode.aplicacion.datos.RepositorioPerfil
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.datos.ResumenActividad
import pe.redactcode.aplicacion.nucleo.base_datos.FragmentoConRepaso
import pe.redactcode.aplicacion.nucleo.base_datos.PerfilEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoEntidad
import pe.redactcode.aplicacion.nucleo.diseno.BarraProgreso
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.Metrica
import pe.redactcode.aplicacion.nucleo.diseno.Superficie
import pe.redactcode.aplicacion.nucleo.diseno.TituloSeccion
import pe.redactcode.nucleo.aprendizaje.Recomendacion
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class InicioViewModel @Inject constructor(
    perfil: RepositorioPerfil,
    proyectos: RepositorioProyectos,
    actividad: RepositorioActividad,
    fragmentos: RepositorioFragmentos,
    private val aprendizaje: RepositorioAprendizaje
) : ViewModel() {
    val perfil: StateFlow<PerfilEntidad?> = perfil.perfil.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val recientes: StateFlow<List<ArchivoReciente>> = proyectos.recientes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val proyectos: StateFlow<List<ProyectoEntidad>> = proyectos.proyectos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val resumen: StateFlow<ResumenActividad?> = actividad.resumen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val repasos: StateFlow<List<FragmentoConRepaso>> = fragmentos.pendientes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _recomendaciones = MutableStateFlow<List<Recomendacion>>(emptyList())
    val recomendaciones: StateFlow<List<Recomendacion>> = _recomendaciones.asStateFlow()

    fun actualizar() {
        viewModelScope.launch {
            runCatching { aprendizaje.recomendaciones(aprendizaje.calcular()) }.onSuccess { _recomendaciones.value = it }
        }
    }
}

@Composable
fun PantallaInicio(
    alAbrirArchivo: (Long, String) -> Unit,
    alAbrirProyecto: (Long) -> Unit,
    alVerProyectos: () -> Unit,
    alAbrirMentor: () -> Unit,
    alAbrirReto: (String) -> Unit,
    alVerPractica: () -> Unit,
    alRepasar: () -> Unit,
    alVerProgreso: () -> Unit,
    vm: InicioViewModel = hiltViewModel()
) {
    val perfil by vm.perfil.collectAsStateWithLifecycle()
    val recientes by vm.recientes.collectAsStateWithLifecycle()
    val proyectos by vm.proyectos.collectAsStateWithLifecycle()
    val resumen by vm.resumen.collectAsStateWithLifecycle()
    val repasos by vm.repasos.collectAsStateWithLifecycle()
    val recomendaciones by vm.recomendaciones.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.actualizar() }

    val hora = LocalTime.now().hour
    val saludo = when (hora) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(saludo + (perfil?.nombre?.let { ", $it" } ?: ""), style = MaterialTheme.typography.headlineMedium)
                Text("¿Qué vamos a construir hoy?", style = MaterialTheme.typography.bodyMedium, color = LocalColoresExtendidos.current.textoTenue)
            }
        }
        item {
            val ultimo = recientes.firstOrNull()
            if (ultimo != null) {
                Superficie(alPulsar = { alAbrirArchivo(ultimo.proyecto.id, ultimo.rutaRelativa) }) {
                    Text("CONTINUAR", style = MaterialTheme.typography.labelSmall, color = LocalColoresExtendidos.current.acento)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ultimo.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${ultimo.proyecto.nombre} · ${ultimo.lenguaje.nombreVisible}", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Abrir en el editor")
                    }
                }
            } else {
                Superficie(alPulsar = alVerProyectos) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Add, null, tint = LocalColoresExtendidos.current.acento)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Crea tu primer proyecto", style = MaterialTheme.typography.titleMedium)
                            Text("Python, JavaScript o una página web", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccesoRapido(Icons.Outlined.Code, "Editor", Modifier.weight(1f), alVerProyectos)
                AccesoRapido(Icons.Outlined.Psychology, "Mentor", Modifier.weight(1f), alAbrirMentor)
                AccesoRapido(Icons.Outlined.Bolt, "Retos", Modifier.weight(1f), alVerPractica)
            }
        }
        item { TituloSeccion("Actividad", accion = "Ver progreso", alPulsarAccion = alVerProgreso) }
        item {
            val r = resumen
            val objetivo = perfil?.objetivoMinutosDiarios ?: 20
            Superficie {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = if ((r?.rachaActual ?: 0) > 0) LocalColoresExtendidos.current.advertencia else LocalColoresExtendidos.current.textoTenue)
                    Spacer(Modifier.width(8.dp))
                    Metrica("${r?.rachaActual ?: 0} días", "Racha actual", Modifier.weight(1f))
                    Metrica("${r?.minutosHoy ?: 0}/$objetivo min", "Práctica de hoy", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                BarraProgreso((r?.minutosHoy ?: 0) / objetivo.toFloat())
                Spacer(Modifier.height(16.dp))
                GraficoSemana(r)
            }
        }
        if (recomendaciones.isNotEmpty()) {
            item { TituloSeccion("Retos recomendados", accion = "Ver todos", alPulsarAccion = alVerPractica) }
            recomendaciones.forEach { rec ->
                item {
                    Superficie(alPulsar = rec.retoId?.let { id -> { alAbrirReto(id) } }) {
                        Text(rec.titulo, style = MaterialTheme.typography.titleSmall)
                        Text(rec.detalle, style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                    }
                }
            }
        }
        item { TituloSeccion("Próximos repasos", accion = if (repasos.isNotEmpty()) "Repasar" else null, alPulsarAccion = alRepasar) }
        item {
            Superficie(alPulsar = if (repasos.isNotEmpty()) alRepasar else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Replay, null, tint = LocalColoresExtendidos.current.textoTenue)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (repasos.isEmpty()) "Nada pendiente por hoy" else "${repasos.size} fragmento(s) para repasar", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (repasos.isEmpty()) "Guarda fragmentos desde el editor para repasarlos con repetición espaciada." else repasos.take(3).joinToString(" · ") { it.fragmento.titulo },
                            style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (proyectos.isNotEmpty()) {
            item { TituloSeccion("Proyectos recientes", accion = "Ver todos", alPulsarAccion = alVerProyectos) }
            proyectos.take(4).forEach { p ->
                item {
                    Superficie(alPulsar = { alAbrirProyecto(p.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.descripcion.ifBlank { "Sin descripción" }, style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (p.proyectoGuiadoId != null) Etiqueta("Guiado")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccesoRapido(icono: ImageVector, texto: String, modifier: Modifier, alPulsar: () -> Unit) {
    Superficie(modifier, alPulsar = alPulsar, relleno = PaddingValues(vertical = 16.dp, horizontal = 12.dp)) {
        Icon(icono, contentDescription = null, tint = LocalColoresExtendidos.current.acento, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GraficoSemana(resumen: ResumenActividad?) {
    val dias = resumen?.semana.orEmpty()
    val maximo = (dias.maxOfOrNull { it.second } ?: 0).coerceAtLeast(10)
    val descripcion = dias.joinToString(", ") { "${it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es", "PE"))}: ${it.second} minutos" }
    Row(Modifier.fillMaxWidth().height(84.dp).semantics { contentDescription = "Minutos de práctica de la semana: $descripcion" }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        dias.forEach { (dia, minutos) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier.fillMaxWidth().height((56 * (minutos.toFloat() / maximo)).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (minutos > 0) LocalColoresExtendidos.current.acento else MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                Spacer(Modifier.height(6.dp))
                Text(dia.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "PE")).uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalColoresExtendidos.current.textoTenue)
            }
        }
    }
}
