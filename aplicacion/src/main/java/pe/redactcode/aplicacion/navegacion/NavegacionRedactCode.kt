package pe.redactcode.aplicacion.navegacion

import pe.redactcode.aplicacion.nucleo.diseno.anchoVentanaDp
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import pe.redactcode.aplicacion.nucleo.diseno.LocalTokens
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pe.redactcode.aplicacion.datos.RepositorioPerfil
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.funcionalidades.ajustes.PantallaAjustes
import pe.redactcode.aplicacion.funcionalidades.aprendizaje.PantallaAprender
import pe.redactcode.aplicacion.funcionalidades.aprendizaje.PantallaProgreso
import pe.redactcode.aplicacion.funcionalidades.aprendizaje.PantallaProyectoGuiado
import pe.redactcode.aplicacion.funcionalidades.bienvenida.PantallaBienvenida
import pe.redactcode.aplicacion.funcionalidades.bienvenida.PantallaBloqueo
import pe.redactcode.aplicacion.funcionalidades.espacio.PantallaEspacio
import pe.redactcode.aplicacion.funcionalidades.explorador.PantallaExplorador
import pe.redactcode.aplicacion.funcionalidades.explorador.PantallaProyectos
import pe.redactcode.aplicacion.funcionalidades.fragmentos.PantallaEditarFragmento
import pe.redactcode.aplicacion.funcionalidades.fragmentos.PantallaFragmentos
import pe.redactcode.aplicacion.funcionalidades.fragmentos.PantallaRepaso
import pe.redactcode.aplicacion.funcionalidades.github.PantallaGitHub
import pe.redactcode.aplicacion.funcionalidades.github.PantallaPublicarGitHub
import pe.redactcode.aplicacion.funcionalidades.inicio.PantallaInicio
import pe.redactcode.aplicacion.funcionalidades.mentor.PantallaMentor
import pe.redactcode.aplicacion.funcionalidades.practica.PantallaPractica
import pe.redactcode.aplicacion.funcionalidades.practica.PantallaReto
import pe.redactcode.aplicacion.nucleo.diseno.Cargando
import pe.redactcode.aplicacion.nucleo.diseno.EncabezadoPantalla
import pe.redactcode.aplicacion.nucleo.diseno.FilaAjuste
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.Separador
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import javax.inject.Inject

object Rutas {
    const val INICIO = "inicio"
    const val PROYECTOS = "proyectos"
    const val APRENDER = "aprender"
    const val PRACTICA = "practica"
    const val MAS = "mas"
    const val EXPLORADOR = "explorador/{proyectoId}"
    const val EDITOR = "editor/{proyectoId}?ruta={ruta}"
    const val RETO = "reto/{retoId}"
    const val GUIADO = "guiado/{guiadoId}"
    const val PROGRESO = "progreso"
    const val MENTOR = "mentor"
    const val FRAGMENTOS = "fragmentos"
    const val FRAGMENTO = "fragmento/{fragmentoId}"
    const val REPASO = "repaso"
    const val GITHUB = "github"
    const val PUBLICAR = "publicar/{proyectoId}?ruta={ruta}"
    const val AJUSTES = "ajustes"

    fun explorador(id: Long) = "explorador/$id"
    fun editor(id: Long, ruta: String?) = "editor/$id?ruta=${Uri.encode(ruta ?: "")}"
    fun reto(id: String) = "reto/${Uri.encode(id)}"
    fun guiado(id: String) = "guiado/${Uri.encode(id)}"
    fun fragmento(id: Long?) = "fragmento/${id ?: 0}"
    fun publicar(id: Long, ruta: String) = "publicar/$id?ruta=${Uri.encode(ruta)}"
}

sealed interface EstadoAcceso {
    data object Cargando : EstadoAcceso
    data object Bienvenida : EstadoAcceso
    data class Bloqueado(val biometria: Boolean) : EstadoAcceso
    data object Libre : EstadoAcceso
}

@HiltViewModel
class AccesoViewModel @Inject constructor(preferencias: RepositorioPreferencias, perfil: RepositorioPerfil) : ViewModel() {
    private val desbloqueado = MutableStateFlow(false)

    val estado: StateFlow<EstadoAcceso> = combine(preferencias.preferencias, perfil.perfil, desbloqueado) { p: PreferenciasUsuario, perfilActual, libre ->
        when {
            !p.bienvenidaCompletada || perfilActual == null -> EstadoAcceso.Bienvenida
            p.bloqueoPin && !libre -> EstadoAcceso.Bloqueado(p.bloqueoBiometrico)
            else -> EstadoAcceso.Libre
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoAcceso.Cargando)

    fun desbloquear() { desbloqueado.value = true }
    fun bloquear() { desbloqueado.value = false }
}

@Composable
fun AppRedactCode(destinoInicial: String?, alConsumirDestino: () -> Unit, vm: AccesoViewModel = hiltViewModel()) {
    val acceso by vm.estado.collectAsStateWithLifecycle()
    when (val a = acceso) {
        EstadoAcceso.Cargando -> Cargando()
        EstadoAcceso.Bienvenida -> PantallaBienvenida(alTerminar = vm::desbloquear)
        is EstadoAcceso.Bloqueado -> PantallaBloqueo(a.biometria, vm::desbloquear)
        EstadoAcceso.Libre -> NavegacionPrincipal(destinoInicial, alConsumirDestino)
    }
}

private data class Pestana(val ruta: String, val etiqueta: String, val icono: ImageVector)

private val pestanas = listOf(
    Pestana(Rutas.INICIO, "Inicio", Icons.Outlined.Home),
    Pestana(Rutas.PROYECTOS, "Proyectos", Icons.Outlined.Folder),
    Pestana(Rutas.APRENDER, "Aprender", Icons.Outlined.School),
    Pestana(Rutas.PRACTICA, "Práctica", Icons.Outlined.Bolt),
    Pestana(Rutas.MAS, "Más", Icons.Outlined.MoreHoriz)
)

@HiltViewModel
class ArranqueViewModel @Inject constructor(private val preferencias: RepositorioPreferencias, private val proyectos: RepositorioProyectos) : ViewModel() {
    suspend fun ultimoProyecto(): Long? {
        val p = preferencias.actuales()
        return p.ultimoProyectoId.takeIf { p.abrirUltimoProyecto && it > 0 && proyectos.obtener(it) != null }
    }
}

@Composable
private fun NavegacionPrincipal(destinoInicial: String?, alConsumirDestino: () -> Unit) {
    val nav = rememberNavController()
    val arranque: ArranqueViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        if (destinoInicial == null) arranque.ultimoProyecto()?.let { nav.navigate(Rutas.editor(it, null)) }
    }
    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route
    LaunchedEffect(destinoInicial) {
        if (destinoInicial == "repaso") {
            nav.navigate(Rutas.REPASO)
            alConsumirDestino()
        }
    }
    val ancha = anchoVentanaDp() >= 840
    val t = LocalTokens.current
    val mostrarNavegacion = pestanas.any { it.ruta == rutaActual }
    val alElegir: (Pestana) -> Unit = { p ->
        nav.navigate(p.ruta) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    if (ancha) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (mostrarNavegacion) {
                NavigationRail(containerColor = t.barraActividad, modifier = Modifier.statusBarsPadding()) {
                    Spacer(Modifier.weight(1f))
                    pestanas.forEach { p ->
                        NavigationRailItem(
                            selected = rutaActual == p.ruta, onClick = { alElegir(p) },
                            icon = { Icon(p.icono, contentDescription = null) }, label = { Text(p.etiqueta) },
                            colors = NavigationRailItemDefaults.colors(selectedIconColor = t.texto, selectedTextColor = t.texto, indicatorColor = t.seleccionLista, unselectedIconColor = t.textoTenue, unselectedTextColor = t.textoTenue)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) { GrafoNavegacion(nav) }
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (mostrarNavegacion) {
                    Column {
                        Separador()
                        NavigationBar(containerColor = t.barraActividad, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                            pestanas.forEach { p ->
                                NavigationBarItem(
                                    selected = rutaActual == p.ruta,
                                    onClick = { alElegir(p) },
                                    icon = { Icon(p.icono, contentDescription = null) },
                                    label = { Text(p.etiqueta) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = t.texto, selectedTextColor = t.texto, indicatorColor = t.seleccionLista,
                                        unselectedIconColor = t.textoTenue, unselectedTextColor = t.textoTenue
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { relleno ->
            Box(Modifier.fillMaxSize().padding(bottom = relleno.calculateBottomPadding())) {
                GrafoNavegacion(nav)
            }
        }
    }
}

@Composable
private fun GrafoNavegacion(nav: NavHostController) {
    val abrirEditor: (Long, String) -> Unit = { id, ruta -> nav.navigate(Rutas.editor(id, ruta)) }
    val abrirReto: (String) -> Unit = { nav.navigate(Rutas.reto(it)) }
    NavHost(nav, startDestination = Rutas.INICIO, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
        composable(Rutas.INICIO) {
            PantallaInicio(
                alAbrirArchivo = abrirEditor,
                alAbrirProyecto = { nav.navigate(Rutas.explorador(it)) },
                alVerProyectos = { nav.navigate(Rutas.PROYECTOS) { launchSingleTop = true } },
                alAbrirMentor = { nav.navigate(Rutas.MENTOR) },
                alAbrirReto = abrirReto,
                alVerPractica = { nav.navigate(Rutas.PRACTICA) { launchSingleTop = true } },
                alRepasar = { nav.navigate(Rutas.REPASO) },
                alVerProgreso = { nav.navigate(Rutas.PROGRESO) },
                alAbrirGitHub = { nav.navigate(Rutas.GITHUB) },
                alAbrirFragmentos = { nav.navigate(Rutas.FRAGMENTOS) },
                alAbrirAjustes = { nav.navigate(Rutas.AJUSTES) }
            )
        }
        composable(Rutas.PROYECTOS) { PantallaProyectos(alAbrirProyecto = { nav.navigate(Rutas.explorador(it)) }) }
        composable(Rutas.APRENDER) { PantallaAprender(alAbrirGuiado = { nav.navigate(Rutas.guiado(it)) }, alVerProgreso = { nav.navigate(Rutas.PROGRESO) }) }
        composable(Rutas.PRACTICA) { PantallaPractica(alAbrirReto = abrirReto) }
        composable(Rutas.MAS) { PantallaMas(nav) }
        composable(Rutas.EXPLORADOR, arguments = listOf(navArgument("proyectoId") { type = NavType.LongType })) {
            PantallaExplorador(alVolver = { nav.popBackStack() }, alAbrirArchivo = abrirEditor)
        }
        composable(
            Rutas.EDITOR,
            arguments = listOf(navArgument("proyectoId") { type = NavType.LongType }, navArgument("ruta") { type = NavType.StringType; defaultValue = "" })
        ) {
            PantallaEspacio(
                alVolver = { nav.popBackStack() },
                alAbrirMentor = { nav.navigate(Rutas.MENTOR) },
                alPublicarGitHub = { id, ruta -> nav.navigate(Rutas.publicar(id, ruta)) },
                alAbrirAjustes = { nav.navigate(Rutas.AJUSTES) },
                alAbrirGitHub = { nav.navigate(Rutas.GITHUB) }
            )
        }
        composable(Rutas.RETO, arguments = listOf(navArgument("retoId") { type = NavType.StringType })) { PantallaReto(alVolver = { nav.popBackStack() }) }
        composable(Rutas.GUIADO, arguments = listOf(navArgument("guiadoId") { type = NavType.StringType })) {
            PantallaProyectoGuiado(alVolver = { nav.popBackStack() }, alAbrirArchivo = abrirEditor)
        }
        composable(Rutas.PROGRESO) { PantallaProgreso(alVolver = { nav.popBackStack() }, alAbrirReto = abrirReto) }
        composable(Rutas.MENTOR) { PantallaMentor(alVolver = { nav.popBackStack() }, alAbrirReto = abrirReto) }
        composable(Rutas.FRAGMENTOS) {
            PantallaFragmentos(alEditar = { nav.navigate(Rutas.fragmento(it)) }, alRepasar = { nav.navigate(Rutas.REPASO) }, alAbrirArchivo = abrirEditor)
        }
        composable(Rutas.FRAGMENTO, arguments = listOf(navArgument("fragmentoId") { type = NavType.LongType })) { PantallaEditarFragmento(alVolver = { nav.popBackStack() }) }
        composable(Rutas.REPASO) { PantallaRepaso(alVolver = { nav.popBackStack() }) }
        composable(Rutas.GITHUB) { PantallaGitHub(alVolver = { nav.popBackStack() }, alAbrirProyecto = { nav.navigate(Rutas.explorador(it)) }) }
        composable(
            Rutas.PUBLICAR,
            arguments = listOf(navArgument("proyectoId") { type = NavType.LongType }, navArgument("ruta") { type = NavType.StringType; defaultValue = "" })
        ) { PantallaPublicarGitHub(alVolver = { nav.popBackStack() }, alConectar = { nav.navigate(Rutas.GITHUB) }) }
        composable(Rutas.AJUSTES) {
            PantallaAjustes(alAbrirGitHub = { nav.navigate(Rutas.GITHUB) }, alReiniciar = { nav.popBackStack(Rutas.INICIO, false) })
        }
    }
}

@Composable
private fun PantallaMas(nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        EncabezadoPantalla("Más")
        FilaAjuste("Mentor de programación", "Explicaciones, correcciones y ejercicios", Icons.Outlined.Psychology, alPulsar = { nav.navigate(Rutas.MENTOR) })
        FilaAjuste("Biblioteca de fragmentos", "Tu código reutilizable y repasos", Icons.Outlined.CollectionsBookmark, alPulsar = { nav.navigate(Rutas.FRAGMENTOS) })
        FilaAjuste("Progreso", "Perfil de aprendizaje, mapa de errores y estadísticas", Icons.Outlined.Insights, alPulsar = { nav.navigate(Rutas.PROGRESO) })
        FilaAjuste("GitHub", "Repositorios y publicación de cambios", Icons.Outlined.Cloud, alPulsar = { nav.navigate(Rutas.GITHUB) })
        FilaAjuste("Ajustes", "Apariencia, editor, seguridad y copias de seguridad", Icons.Outlined.Settings, alPulsar = { nav.navigate(Rutas.AJUSTES) })
    }
}
