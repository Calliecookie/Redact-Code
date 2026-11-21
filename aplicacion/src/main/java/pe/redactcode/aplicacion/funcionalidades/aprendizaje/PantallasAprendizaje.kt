package pe.redactcode.aplicacion.funcionalidades.aprendizaje

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.datos.RepositorioActividad
import pe.redactcode.aplicacion.datos.RepositorioAprendizaje
import pe.redactcode.aplicacion.datos.RepositorioPerfil
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.datos.ResumenActividad
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.funcionalidades.inicio.GraficoSemana
import pe.redactcode.aplicacion.nucleo.base_datos.PerfilEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoEntidad
import pe.redactcode.aplicacion.nucleo.diseno.BarraProgreso
import pe.redactcode.aplicacion.nucleo.diseno.BloqueCodigo
import pe.redactcode.aplicacion.nucleo.diseno.BotonPrincipal
import pe.redactcode.aplicacion.nucleo.diseno.BotonSecundario
import pe.redactcode.aplicacion.nucleo.diseno.Cargando
import pe.redactcode.aplicacion.nucleo.diseno.EncabezadoPantalla
import pe.redactcode.aplicacion.nucleo.diseno.EstadoVacio
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.Metrica
import pe.redactcode.aplicacion.nucleo.diseno.Superficie
import pe.redactcode.aplicacion.nucleo.diseno.TituloSeccion
import pe.redactcode.nucleo.aprendizaje.EstadoDominio
import pe.redactcode.nucleo.aprendizaje.PerfilCalculado
import pe.redactcode.nucleo.aprendizaje.Recomendacion
import pe.redactcode.nucleo.proyectos.CatalogoProyectosGuiados
import pe.redactcode.nucleo.proyectos.ProyectoGuiado
import pe.redactcode.nucleo.proyectos.ResultadoPaso
import pe.redactcode.nucleo.proyectos.VerificadorProyecto
import pe.redactcode.nucleo.validacion.CatalogoExplicaciones
import javax.inject.Inject

@HiltViewModel
class AprenderViewModel @Inject constructor(proyectos: RepositorioProyectos) : ViewModel() {
    val catalogo: List<ProyectoGuiado> = CatalogoProyectosGuiados.proyectos
    val iniciados: StateFlow<Map<String, ProyectoEntidad>> = proyectos.observarGuiados()
        .map { lista -> lista.associateBy { it.proyectoGuiadoId!! } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

@Composable
fun PantallaAprender(alAbrirGuiado: (String) -> Unit, alVerProgreso: () -> Unit, vm: AprenderViewModel = hiltViewModel()) {
    val iniciados by vm.iniciados.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { EncabezadoPantalla("Aprender creando", "Proyectos guiados por niveles") }
        item {
            Superficie(alPulsar = alVerProgreso) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Insights, null, tint = LocalColoresExtendidos.current.acento)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tu perfil de aprendizaje", style = MaterialTheme.typography.titleSmall)
                        Text("Conceptos dominados, mapa de errores y recomendaciones", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                    }
                }
            }
        }
        item { TituloSeccion("Proyectos") }
        items(vm.catalogo, key = { it.id }) { p ->
            val proyecto = iniciados[p.id]
            Superficie(alPulsar = { alAbrirGuiado(p.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.titulo, style = MaterialTheme.typography.titleMedium)
                        Text(p.objetivo, style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Etiqueta(p.nivel.nombreVisible)
                        Etiqueta(p.lenguaje.nombreVisible)
                    }
                }
                if (proyecto != null) {
                    Spacer(Modifier.height(10.dp))
                    val completados = proyecto.pasoActual.coerceAtMost(p.pasos.size)
                    BarraProgreso(completados / p.pasos.size.toFloat())
                    Spacer(Modifier.height(4.dp))
                    Text(if (completados >= p.pasos.size) "Completado" else "Paso ${completados + 1} de ${p.pasos.size}", style = MaterialTheme.typography.labelSmall, color = LocalColoresExtendidos.current.textoTenue)
                }
            }
        }
    }
}

@HiltViewModel
class ProyectoGuiadoViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val proyectos: RepositorioProyectos,
    private val servicio: ServicioCodigo
) : ViewModel() {
    val guiado: ProyectoGuiado = checkNotNull(CatalogoProyectosGuiados.buscar(checkNotNull(estadoGuardado.get<String>("guiadoId"))))
    val proyecto = MutableStateFlow<ProyectoEntidad?>(null)
    val resultados = MutableStateFlow<Map<Int, ResultadoPaso>>(emptyMap())
    val verificando = MutableStateFlow<Int?>(null)
    val mensaje = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { proyecto.value = proyectos.proyectoDeGuiado(guiado.id) }
    }

    fun iniciar(alListo: (Long) -> Unit) {
        viewModelScope.launch {
            runCatching { proyectos.crearDesdeGuiado(guiado) }
                .onSuccess { id -> proyecto.value = proyectos.obtener(id); alListo(id) }
                .onFailure { mensaje.value = it.message }
        }
    }

    fun verificar(indice: Int) {
        val p = proyecto.value ?: return
        viewModelScope.launch {
            verificando.value = indice
            val verificador = VerificadorProyecto(servicio.ejecutoresPruebas) { ruta -> runCatching { proyectos.leer(p, ruta) }.getOrNull() }
            val resultado = verificador.verificar(guiado.pasos[indice])
            resultados.value = resultados.value + (indice to resultado)
            if (resultado.aprobado && p.pasoActual <= indice) {
                val actualizado = p.copy(pasoActual = indice + 1)
                proyectos.actualizar(actualizado)
                proyecto.value = actualizado
                mensaje.value = if (indice + 1 >= guiado.pasos.size) "¡Proyecto completado!" else "Paso completado"
            }
            verificando.value = null
        }
    }
}

@Composable
fun PantallaProyectoGuiado(alVolver: () -> Unit, alAbrirArchivo: (Long, String) -> Unit, vm: ProyectoGuiadoViewModel = hiltViewModel()) {
    val proyecto by vm.proyecto.collectAsStateWithLifecycle()
    val resultados by vm.resultados.collectAsStateWithLifecycle()
    val verificando by vm.verificando.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(mensaje) { mensaje?.let { avisos.showSnackbar(it); vm.mensaje.value = null } }
    val g = vm.guiado
    val ext = LocalColoresExtendidos.current
    var expandido by remember { androidx.compose.runtime.mutableIntStateOf(proyecto?.pasoActual ?: 0) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { EncabezadoPantalla(g.titulo, "${g.nivel.nombreVisible} · ${g.lenguaje.nombreVisible}", alVolver = alVolver) }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(g.objetivo, style = MaterialTheme.typography.titleMedium)
                Text(g.descripcion, style = MaterialTheme.typography.bodyMedium, color = ext.textoTenue)
                Text("Requisitos", style = MaterialTheme.typography.titleSmall)
                g.requisitos.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                Text("Archivos iniciales: ${g.archivosIniciales.keys.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = ext.textoTenue)
                Text("Resultado esperado", style = MaterialTheme.typography.titleSmall)
                Text(g.resultadoEsperado, style = MaterialTheme.typography.bodyMedium)
                val p = proyecto
                if (p == null) {
                    BotonPrincipal("Comenzar proyecto", { vm.iniciar { } }, Modifier.fillMaxWidth())
                } else {
                    BarraProgreso(p.pasoActual / g.pasos.size.toFloat())
                    Text("${p.pasoActual.coerceAtMost(g.pasos.size)} de ${g.pasos.size} pasos completados", style = MaterialTheme.typography.labelMedium, color = ext.textoTenue)
                }
                TituloSeccion("Pasos")
            }
        }
        items(g.pasos.size) { i ->
            val paso = g.pasos[i]
            val completado = (proyecto?.pasoActual ?: 0) > i
            val resultado = resultados[i]
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Superficie(alPulsar = { expandido = if (expandido == i) -1 else i }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (completado) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (completado) ext.exito else ext.textoTenue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("${i + 1}. ${paso.titulo}", style = MaterialTheme.typography.titleSmall)
                    }
                    AnimatedVisibility(expandido == i) {
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(paso.explicacion, style = MaterialTheme.typography.bodyMedium)
                            Text("Objetivo", style = MaterialTheme.typography.labelMedium, color = ext.acento)
                            Text(paso.objetivo, style = MaterialTheme.typography.bodyMedium)
                            Text("Pruebas de este paso", style = MaterialTheme.typography.labelMedium, color = ext.textoTenue)
                            paso.verificaciones.forEach { v ->
                                val r = resultado?.resultados?.firstOrNull { it.verificacion == v }
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        if (r?.aprobada == true) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                                        tint = when (r?.aprobada) { true -> ext.exito; false -> ext.error; null -> ext.textoTenue }, modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("${v.descripcion} (${v.archivo})", style = MaterialTheme.typography.bodySmall)
                                        r?.takeIf { !it.aprobada }?.let { Text(it.detalle, style = MaterialTheme.typography.bodySmall, color = ext.error) }
                                    }
                                }
                            }
                            var pista by remember { mutableStateOf(false) }
                            if (paso.pista.isNotBlank()) {
                                if (pista) BloqueCodigo(paso.pista) else TextButton(onClick = { pista = true }) { Text("Ver pista") }
                            }
                            val p = proyecto
                            if (p != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BotonSecundario("Abrir código", { alAbrirArchivo(p.id, paso.verificaciones.first().archivo) }, icono = Icons.Outlined.Code)
                                    BotonPrincipal(if (verificando == i) "Verificando…" else "Verificar", { vm.verificar(i) }, habilitado = verificando == null)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SnackbarHost(avisos) }
    }
}

data class EstadoProgreso(
    val perfil: PerfilCalculado? = null,
    val recomendaciones: List<Recomendacion> = emptyList(),
    val cargando: Boolean = true
)

@HiltViewModel
class ProgresoViewModel @Inject constructor(
    private val aprendizaje: RepositorioAprendizaje,
    actividad: RepositorioActividad,
    private val repositorioPerfil: RepositorioPerfil
) : ViewModel() {
    val estado = MutableStateFlow(EstadoProgreso())
    val resumen: StateFlow<ResumenActividad?> = actividad.resumen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val retosResueltos: StateFlow<Int> = aprendizaje.retosResueltos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val perfilUsuario: StateFlow<PerfilEntidad?> = repositorioPerfil.perfil.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cargar() {
        viewModelScope.launch {
            val perfil = aprendizaje.calcular()
            estado.value = EstadoProgreso(perfil, aprendizaje.recomendaciones(perfil), false)
        }
    }

    fun objetivos(minutos: Int, retos: Int) {
        val p = perfilUsuario.value ?: return
        viewModelScope.launch { repositorioPerfil.actualizar(p.copy(objetivoMinutosDiarios = minutos.coerceIn(5, 240), objetivoRetosSemanales = retos.coerceIn(1, 50))) }
    }
}

@Composable
fun PantallaProgreso(alVolver: () -> Unit, alAbrirReto: (String) -> Unit, vm: ProgresoViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val resumen by vm.resumen.collectAsStateWithLifecycle()
    val resueltos by vm.retosResueltos.collectAsStateWithLifecycle()
    val perfilUsuario by vm.perfilUsuario.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.cargar() }
    val ext = LocalColoresExtendidos.current
    if (estado.cargando) {
        Column { EncabezadoPantalla("Progreso", alVolver = alVolver); Cargando() }
        return
    }
    val perfil = estado.perfil!!
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { EncabezadoPantalla("Progreso", "Nivel estimado: ${perfil.nivelSugerido.nombreVisible}", alVolver = alVolver) }
        item {
            Superficie {
                Row {
                    Metrica("${resumen?.rachaActual ?: 0}", "Racha (días)", Modifier.weight(1f))
                    Metrica("${resumen?.mejorRacha ?: 0}", "Mejor racha", Modifier.weight(1f))
                    Metrica("$resueltos", "Retos resueltos", Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Row {
                    Metrica("${resumen?.minutosSemana ?: 0}", "Min. esta semana", Modifier.weight(1f))
                    Metrica("${resumen?.retosSemana ?: 0}/${perfilUsuario?.objetivoRetosSemanales ?: 5}", "Retos de la semana", Modifier.weight(1f))
                    Metrica("${resumen?.diasActivos ?: 0}", "Días activos", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                GraficoSemana(resumen)
            }
        }
        item {
            val pu = perfilUsuario
            if (pu != null) {
                Superficie {
                    Text("Objetivos personales", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Minutos diarios: ${pu.objetivoMinutosDiarios}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { vm.objetivos(pu.objetivoMinutosDiarios - 5, pu.objetivoRetosSemanales) }) { Text("−") }
                        TextButton(onClick = { vm.objetivos(pu.objetivoMinutosDiarios + 5, pu.objetivoRetosSemanales) }) { Text("+") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Retos por semana: ${pu.objetivoRetosSemanales}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { vm.objetivos(pu.objetivoMinutosDiarios, pu.objetivoRetosSemanales - 1) }) { Text("−") }
                        TextButton(onClick = { vm.objetivos(pu.objetivoMinutosDiarios, pu.objetivoRetosSemanales + 1) }) { Text("+") }
                    }
                }
            }
        }
        if (!perfil.tieneDatos) {
            item { EstadoVacio(Icons.Outlined.School, "Aún no hay datos", "Resuelve retos y escribe código: tu perfil se construirá con tu actividad real.") }
        } else {
            if (estado.recomendaciones.isNotEmpty()) {
                item { TituloSeccion("Recomendaciones") }
                items(estado.recomendaciones) { r ->
                    Superficie(alPulsar = r.retoId?.let { { alAbrirReto(it) } }) {
                        Text(r.titulo, style = MaterialTheme.typography.titleSmall)
                        Text(r.detalle, style = MaterialTheme.typography.bodySmall, color = ext.textoTenue)
                    }
                }
            }
            item { TituloSeccion("Dominio por concepto") }
            items(perfil.dominios.filter { it.estado != EstadoDominio.SIN_DATOS }) { d ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.categoria.nombreVisible, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(d.estado.nombreVisible, style = MaterialTheme.typography.labelSmall, color = when (d.estado) {
                            EstadoDominio.DOMINADO -> ext.exito
                            EstadoDominio.DIFICULTAD -> ext.error
                            else -> ext.textoTenue
                        })
                    }
                    Spacer(Modifier.height(4.dp))
                    BarraProgreso(d.puntaje / 100f, color = if (d.estado == EstadoDominio.DIFICULTAD) ext.error else ext.acento)
                    Text("${d.aprobados} retos superados · ${d.errores} errores en 30 días", style = MaterialTheme.typography.labelSmall, color = ext.textoTenue)
                }
            }
            item { TituloSeccion("Mapa de errores") }
            if (perfil.erroresFrecuentes.isEmpty()) item { Text("Sin errores registrados.", style = MaterialTheme.typography.bodySmall, color = ext.textoTenue) }
            items(perfil.erroresFrecuentes) { (codigo, cantidad) ->
                val explicacion = CatalogoExplicaciones.buscar(codigo)
                val maximo = perfil.erroresFrecuentes.first().second
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row {
                        Text(explicacion?.titulo ?: codigo.replace("RETO_", "Reto fallido: ").replace('_', ' '), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("$cantidad", style = MaterialTheme.typography.labelLarge)
                    }
                    BarraProgreso(cantidad / maximo.toFloat(), color = ext.error)
                    Text(codigo + (explicacion?.let { " · ${it.lenguaje.nombreVisible}" } ?: ""), style = MaterialTheme.typography.labelSmall, color = ext.textoTenue)
                }
            }
            if (perfil.lenguajesUsados.isNotEmpty()) {
                item { TituloSeccion("Lenguajes más usados") }
                items(perfil.lenguajesUsados) { (lenguaje, cantidad) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(lenguaje.nombreVisible, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("$cantidad", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item { TituloSeccion("Evolución del rendimiento (puntaje promedio semanal)") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    perfil.evolucionSemanal.forEach { (semana, puntaje) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (puntaje < 0) "–" else "$puntaje", style = MaterialTheme.typography.labelMedium)
                            Text("${semana.dayOfMonth}/${semana.monthValue}", style = MaterialTheme.typography.labelSmall, color = ext.textoTenue)
                        }
                    }
                }
            }
        }
    }
}
