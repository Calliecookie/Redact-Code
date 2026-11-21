package pe.redactcode.aplicacion.funcionalidades.github

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.datos.RepositorioGitHub
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoEntidad
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
import pe.redactcode.nucleo.github.EntradaContenido
import pe.redactcode.nucleo.github.RamaGitHub
import pe.redactcode.nucleo.github.RepositorioGitHub as RepoGitHub
import javax.inject.Inject

data class EstadoGitHub(
    val usuario: String? = null,
    val cargando: Boolean = false,
    val repositorios: List<RepoGitHub> = emptyList(),
    val repositorio: RepoGitHub? = null,
    val ramas: List<RamaGitHub> = emptyList(),
    val rama: String? = null,
    val ruta: String = "",
    val contenido: List<EntradaContenido> = emptyList(),
    val seleccionados: Set<String> = emptySet(),
    val mensaje: String? = null,
    val error: String? = null
)

@HiltViewModel
class GitHubViewModel @Inject constructor(private val repositorio: RepositorioGitHub) : ViewModel() {
    val estado = MutableStateFlow(EstadoGitHub())

    init {
        viewModelScope.launch {
            repositorio.cargarEstado()
            estado.update { it.copy(usuario = repositorio.usuario.value) }
            if (repositorio.usuario.value != null) cargarRepositorios()
        }
    }

    private fun ejecutar(bloque: suspend () -> Unit) {
        viewModelScope.launch {
            estado.update { it.copy(cargando = true, error = null) }
            try { bloque() } catch (e: Exception) { estado.update { it.copy(error = e.message ?: "Error de GitHub") } }
            estado.update { it.copy(cargando = false) }
        }
    }

    fun conectar(token: String) = ejecutar {
        val usuario = repositorio.conectar(token)
        estado.update { it.copy(usuario = usuario) }
        cargarRepositorios()
    }

    fun desconectar() = ejecutar {
        repositorio.desconectar()
        estado.value = EstadoGitHub()
    }

    fun cargarRepositorios() = ejecutar { estado.update { it.copy(repositorios = repositorio.repositorios()) } }

    fun elegirRepositorio(repo: RepoGitHub) = ejecutar {
        val ramas = repositorio.ramas(repo)
        val rama = repo.ramaPrincipal.takeIf { r -> ramas.any { it.name == r } } ?: ramas.firstOrNull()?.name ?: repo.ramaPrincipal
        estado.update { it.copy(repositorio = repo, ramas = ramas, rama = rama, ruta = "", seleccionados = emptySet()) }
        listar("")
    }

    fun elegirRama(rama: String) = ejecutar {
        estado.update { it.copy(rama = rama, ruta = "", seleccionados = emptySet()) }
        listar("")
    }

    fun navegar(ruta: String) = ejecutar { listar(ruta) }

    private suspend fun listar(ruta: String) {
        val e = estado.value
        val repo = e.repositorio ?: return
        val contenido = repositorio.listar(repo, ruta, e.rama ?: repo.ramaPrincipal)
        estado.update { it.copy(ruta = ruta, contenido = contenido) }
    }

    fun alternar(ruta: String) = estado.update { it.copy(seleccionados = if (ruta in it.seleccionados) it.seleccionados - ruta else it.seleccionados + ruta) }

    fun volverRepositorios() = estado.update { it.copy(repositorio = null, contenido = emptyList(), seleccionados = emptySet()) }

    fun descargar(alAbrir: (Long) -> Unit) = ejecutar {
        val e = estado.value
        val repo = e.repositorio ?: return@ejecutar
        val id = repositorio.descargarEnProyecto(repo, e.rama ?: repo.ramaPrincipal, e.seleccionados.toList(), null)
        estado.update { it.copy(seleccionados = emptySet(), mensaje = "Archivos descargados en un proyecto local") }
        alAbrir(id)
    }
}

@Composable
fun PantallaGitHub(alVolver: () -> Unit, alAbrirProyecto: (Long) -> Unit, vm: GitHubViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val ext = LocalColoresExtendidos.current
    Column(Modifier.fillMaxSize().imePadding()) {
        EncabezadoPantalla("GitHub", estado.usuario?.let { "Conectado como @$it" } ?: "Sin conectar", alVolver = if (estado.repositorio != null) vm::volverRepositorios else alVolver) {
            if (estado.usuario != null) IconButton(onClick = vm::desconectar) { Icon(Icons.AutoMirrored.Outlined.Logout, "Desconectar cuenta") }
        }
        estado.error?.let { Text(it, color = ext.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp)) }
        estado.mensaje?.let { Text(it, color = ext.exito, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp)) }
        if (estado.cargando) Cargando(Modifier.height(64.dp))
        when {
            estado.usuario == null -> Conexion(vm::conectar, estado.cargando)
            estado.repositorio == null -> LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (estado.repositorios.isEmpty() && !estado.cargando) item { EstadoVacio(Icons.Outlined.Cloud, "Sin repositorios", "Tu token no tiene acceso a repositorios o tu cuenta aún no tiene ninguno.") }
                items(estado.repositorios, key = { it.id }) { r ->
                    Superficie(alPulsar = { vm.elegirRepositorio(r) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.nombreCompleto, style = MaterialTheme.typography.titleSmall)
                                Text(r.description ?: "Sin descripción", style = MaterialTheme.typography.bodySmall, color = ext.textoTenue, maxLines = 1)
                            }
                            if (r.private) Icon(Icons.Outlined.Lock, "Privado", tint = ext.textoTenue, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    estado.ramas.forEach { rama -> Etiqueta(rama.name, seleccionada = rama.name == estado.rama, alPulsar = { vm.elegirRama(rama.name) }) }
                }
                Text("/${estado.ruta}", style = MaterialTheme.typography.labelMedium, color = ext.textoTenue, modifier = Modifier.padding(horizontal = 20.dp))
                LazyColumn(Modifier.weight(1f)) {
                    if (estado.ruta.isNotEmpty()) {
                        item {
                            Text("..", modifier = Modifier.fillMaxWidth().clickable { vm.navegar(estado.ruta.substringBeforeLast('/', "")) }.padding(horizontal = 20.dp, vertical = 14.dp))
                            Separador()
                        }
                    }
                    items(estado.contenido, key = { it.path }) { entrada ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (entrada.esCarpeta) vm.navegar(entrada.path) else if (entrada.esArchivo) vm.alternar(entrada.path) }.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (entrada.esCarpeta) Icons.Outlined.Folder else Icons.Outlined.Description, null, tint = ext.textoTenue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(entrada.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (entrada.esArchivo) Icon(if (entrada.path in estado.seleccionados) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, "Seleccionar ${entrada.name}", tint = ext.acento)
                        }
                        Separador()
                    }
                }
                BotonPrincipal("Descargar ${estado.seleccionados.size} archivo(s) a un proyecto", { vm.descargar(alAbrirProyecto) },
                    Modifier.fillMaxWidth().padding(20.dp), habilitado = estado.seleccionados.isNotEmpty() && !estado.cargando)
            }
        }
    }
}

@Composable
private fun Conexion(alConectar: (String) -> Unit, cargando: Boolean) {
    var token by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Conecta tu cuenta con un token personal", style = MaterialTheme.typography.titleMedium)
        Text(
            "1. En github.com abre Settings › Developer settings › Personal access tokens › Fine-grained tokens.\n" +
                "2. Crea un token con acceso a los repositorios que quieras usar y el permiso «Contents: Read and write».\n" +
                "3. Pégalo aquí. Se guarda cifrado con Android Keystore y solo se envía a api.github.com.",
            style = MaterialTheme.typography.bodyMedium, color = LocalColoresExtendidos.current.textoTenue
        )
        CampoTexto(token, { token = it.trim() }, "Token de GitHub", transformacion = PasswordVisualTransformation())
        BotonPrincipal(if (cargando) "Verificando…" else "Conectar", { alConectar(token) }, Modifier.fillMaxWidth(), habilitado = token.length >= 20 && !cargando)
    }
}

@HiltViewModel
class PublicarGitHubViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val github: RepositorioGitHub,
    private val proyectos: RepositorioProyectos
) : ViewModel() {
    private val proyectoId: Long = checkNotNull(estadoGuardado.get<Long>("proyectoId"))
    val ruta: String = checkNotNull(estadoGuardado.get<String>("ruta"))
    val proyecto = MutableStateFlow<ProyectoEntidad?>(null)
    val repositorio = MutableStateFlow("")
    val rama = MutableStateFlow("main")
    val mensajeCommit = MutableStateFlow("Actualiza ${ruta.substringAfterLast('/')} desde Redact Code")
    val estado = MutableStateFlow<String?>(null)
    val publicando = MutableStateFlow(false)
    val conectado = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            github.cargarEstado()
            conectado.value = github.usuario.value != null
            val p = proyectos.obtener(proyectoId)
            proyecto.value = p
            p?.githubRepositorio?.let { repositorio.value = it }
            p?.githubRama?.let { rama.value = it }
            if (p?.githubRepositorio == null) github.usuario.value?.let { repositorio.value = "$it/" }
        }
    }

    fun publicar() {
        val p = proyecto.value ?: return
        viewModelScope.launch {
            publicando.value = true
            estado.value = runCatching { github.publicar(p, ruta, mensajeCommit.value, repositorio.value.trim(), rama.value.trim()) }
                .fold({ "Commit ${it.commit.sha.take(7)} publicado en ${repositorio.value}@${rama.value}" }, { "Error: ${it.message}" })
            publicando.value = false
        }
    }
}

@Composable
fun PantallaPublicarGitHub(alVolver: () -> Unit, alConectar: () -> Unit, vm: PublicarGitHubViewModel = hiltViewModel()) {
    val repo by vm.repositorio.collectAsStateWithLifecycle()
    val rama by vm.rama.collectAsStateWithLifecycle()
    val mensaje by vm.mensajeCommit.collectAsStateWithLifecycle()
    val estado by vm.estado.collectAsStateWithLifecycle()
    val publicando by vm.publicando.collectAsStateWithLifecycle()
    val conectado by vm.conectado.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding()) {
        EncabezadoPantalla("Publicar en GitHub", vm.ruta, alVolver = alVolver)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!conectado) {
                Text("Primero conecta tu cuenta de GitHub.", style = MaterialTheme.typography.bodyMedium)
                BotonSecundario("Conectar cuenta", alConectar)
                return@Column
            }
            Text("Se creará un commit que agrega o actualiza este archivo en la ruta indicada del repositorio.", style = MaterialTheme.typography.bodyMedium, color = LocalColoresExtendidos.current.textoTenue)
            CampoTexto(repo, { vm.repositorio.value = it }, "Repositorio (usuario/repositorio)")
            CampoTexto(rama, { vm.rama.value = it }, "Rama")
            CampoTexto(mensaje, { vm.mensajeCommit.value = it }, "Mensaje del commit", unaLinea = false)
            BotonPrincipal(if (publicando) "Publicando…" else "Crear commit", vm::publicar, Modifier.fillMaxWidth(), habilitado = !publicando && repo.contains('/') && rama.isNotBlank() && mensaje.isNotBlank())
            estado?.let { Text(it, color = if (it.startsWith("Error")) LocalColoresExtendidos.current.error else LocalColoresExtendidos.current.exito) }
            TextButton(onClick = alVolver) { Text("Volver al editor") }
        }
    }
}
