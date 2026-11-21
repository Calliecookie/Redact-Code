package pe.redactcode.aplicacion.funcionalidades.ajustes

import pe.redactcode.aplicacion.nucleo.diseno.anchoVentanaDp
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.BuildConfig
import pe.redactcode.aplicacion.datos.GestorCopiasSeguridad
import pe.redactcode.aplicacion.datos.RepositorioExtensiones
import pe.redactcode.aplicacion.datos.RepositorioPerfil
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.funcionalidades.espacio.ExtensionesViewModel
import pe.redactcode.aplicacion.funcionalidades.espacio.PanelExtensiones
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.base_datos.BaseDatosRedactCode
import pe.redactcode.aplicacion.nucleo.base_datos.PerfilEntidad
import pe.redactcode.aplicacion.nucleo.diseno.CampoTexto
import pe.redactcode.aplicacion.nucleo.diseno.DialogoConfirmacion
import pe.redactcode.aplicacion.nucleo.diseno.DialogoEntradaTexto
import pe.redactcode.aplicacion.nucleo.diseno.EncabezadoPantalla
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.FilaAjuste
import pe.redactcode.aplicacion.nucleo.diseno.FilaInterruptor
import pe.redactcode.aplicacion.nucleo.diseno.FuenteMonoJetBrains
import pe.redactcode.aplicacion.nucleo.diseno.LocalTokens
import pe.redactcode.aplicacion.nucleo.diseno.ModoTema
import pe.redactcode.aplicacion.nucleo.diseno.Separador
import pe.redactcode.aplicacion.nucleo.diseno.TituloSeccion
import pe.redactcode.aplicacion.nucleo.preferencias.FuenteEditor
import pe.redactcode.aplicacion.nucleo.preferencias.GuardadoAutomatico
import pe.redactcode.aplicacion.nucleo.preferencias.OrdenArchivos
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.ProveedorAgenteTipo
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.aplicacion.nucleo.seguridad.AlmacenCredenciales
import pe.redactcode.aplicacion.nucleo.seguridad.AutenticadorBiometrico
import pe.redactcode.aplicacion.nucleo.seguridad.GestorPin
import pe.redactcode.aplicacion.trabajos.TrabajadorRecordatorioRepaso
import pe.redactcode.nucleo.agentes.PanelAntigravity
import androidx.core.net.toUri
import pe.redactcode.nucleo.agentes.ProveedorCompatibleOpenAI
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import pe.redactcode.nucleo.control.ControlVersiones
import pe.redactcode.nucleo.escritorio.CompatibilidadEscritorio
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.NivelExperiencia
import java.time.LocalDate
import javax.inject.Inject

enum class SeccionAjustes(val titulo: String, val descripcion: String, val icono: ImageVector) {
    GENERAL("General", "Perfil y arranque", Icons.Outlined.Tune),
    APARIENCIA("Apariencia", "Tema, colores y paneles", Icons.Outlined.Palette),
    EDITOR("Editor", "Escritura, ayudas y validación", Icons.Outlined.Code),
    TIPOGRAFIA("Tipografía", "Tamaños y fuente del código", Icons.Outlined.TextFields),
    ARCHIVOS("Archivos", "Explorador, guardado y copias", Icons.Outlined.Folder),
    TERMINAL("Terminal", "Comandos y ejecución", Icons.Outlined.Terminal),
    LENGUAJES("Lenguajes", "Validación y motores", Icons.Outlined.Translate),
    IA("Inteligencia artificial", "Mentor y proveedores", Icons.Outlined.Psychology),
    AGENTES("Agentes", "Agente de código y permisos", Icons.Outlined.SmartToy),
    EXTENSIONES("Extensiones", "Fragmentos y temas instalados", Icons.Outlined.Extension),
    GIT("Git y GitHub", "Autor, ramas y publicación", Icons.Outlined.AccountTree),
    NOTIFICACIONES("Notificaciones", "Recordatorios de repaso", Icons.Outlined.Notifications),
    PRIVACIDAD("Privacidad y seguridad", "Bloqueo y datos", Icons.Outlined.Security),
    ACCESIBILIDAD("Accesibilidad", "Texto, contraste y toque", Icons.Outlined.Accessibility),
    AVANZADO("Avanzado", "Restablecer y borrar", Icons.Outlined.Build),
    ACERCA("Acerca de", "Versión y licencias", Icons.Outlined.Info)
}

@HiltViewModel
class AjustesViewModel @Inject constructor(
    @ApplicationContext private val contexto: Context,
    val repositorio: RepositorioPreferencias,
    private val perfiles: RepositorioPerfil,
    private val pin: GestorPin,
    val biometria: AutenticadorBiometrico,
    private val credenciales: AlmacenCredenciales,
    private val copias: GestorCopiasSeguridad,
    private val baseDatos: BaseDatosRedactCode,
    private val archivos: GestorArchivos,
    private val servicio: ServicioCodigo,
    val extensiones: RepositorioExtensiones
) : ViewModel() {
    val preferencias: StateFlow<PreferenciasUsuario> = repositorio.preferencias.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasUsuario())
    val perfil: StateFlow<PerfilEntidad?> = perfiles.perfil.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mensaje = MutableStateFlow<String?>(null)
    val tieneClaveIA = MutableStateFlow(false)
    val tieneClaveCompatible = MutableStateFlow(false)
    val versionPython = MutableStateFlow("cargando…")

    init {
        viewModelScope.launch {
            tieneClaveIA.value = credenciales.leer(AlmacenCredenciales.CLAVE_IA) != null
            tieneClaveCompatible.value = credenciales.leer(AlmacenCredenciales.CLAVE_COMPATIBLE) != null
        }
        viewModelScope.launch { versionPython.value = runCatching { servicio.python.version() }.getOrElse { "no disponible" } }
    }

    val motorJavaScript: String get() = servicio.javaScript.nombreMotorEjecucion

    fun cambiar(bloque: suspend RepositorioPreferencias.() -> Unit) = viewModelScope.launch { repositorio.bloque() }

    fun actualizarPerfil(nombre: String? = null, nivel: NivelExperiencia? = null) {
        val p = perfil.value ?: return
        viewModelScope.launch { perfiles.actualizar(p.copy(nombre = nombre?.take(40) ?: p.nombre, nivel = nivel?.name ?: p.nivel)) }
    }

    fun establecerPin(valor: String) = viewModelScope.launch {
        runCatching { pin.establecer(valor); repositorio.bloqueoPin(true) }.onSuccess { mensaje.value = "PIN actualizado" }.onFailure { mensaje.value = it.message }
    }

    fun quitarPin() = viewModelScope.launch {
        pin.eliminar(); repositorio.bloqueoPin(false); repositorio.bloqueoBiometrico(false); repositorio.bloquearAlSalir(false); mensaje.value = "Bloqueo desactivado"
    }

    fun pinValido(v: String) = pin.esPinValido(v)

    fun recordatorios(activo: Boolean, hora: Int) = viewModelScope.launch {
        repositorio.recordatoriosRepaso(activo)
        repositorio.horaRecordatorio(hora)
        if (activo) TrabajadorRecordatorioRepaso.programar(contexto, hora) else TrabajadorRecordatorioRepaso.cancelar(contexto)
    }

    fun guardarClave(clave: String, valor: String) = viewModelScope.launch {
        credenciales.guardar(clave, valor.trim())
        if (clave == AlmacenCredenciales.CLAVE_IA) tieneClaveIA.value = true else tieneClaveCompatible.value = true
        mensaje.value = "Clave guardada de forma cifrada"
    }

    fun eliminarClaveIA() = viewModelScope.launch {
        credenciales.eliminar(AlmacenCredenciales.CLAVE_IA)
        repositorio.proveedorIA(PreferenciasUsuario.PROVEEDOR_IA_LOCAL)
        if (preferencias.value.proveedorAgente == ProveedorAgenteTipo.ANTHROPIC) repositorio.consentimientoIA(false)
        tieneClaveIA.value = false
    }

    fun eliminarClaveCompatible() = viewModelScope.launch {
        credenciales.eliminar(AlmacenCredenciales.CLAVE_COMPATIBLE)
        tieneClaveCompatible.value = false
    }

    fun exportar(uri: android.net.Uri) = viewModelScope.launch {
        mensaje.value = runCatching { copias.exportar(uri) }.fold({ "Copia creada: ${it.proyectos} proyectos, ${it.archivos} archivos, ${it.fragmentos} fragmentos" }, { "No se pudo exportar: ${it.message}" })
    }

    fun importar(uri: android.net.Uri) = viewModelScope.launch {
        mensaje.value = runCatching { copias.importar(uri) }.fold({ "Restaurados ${it.proyectos} proyectos y ${it.fragmentos} fragmentos" }, { "No se pudo importar: ${it.message}" })
    }

    fun restablecerAjustes() = viewModelScope.launch {
        repositorio.restablecerAjustes()
        mensaje.value = "Ajustes restablecidos a sus valores iniciales"
    }

    fun borrarTodo(alTerminar: () -> Unit) = viewModelScope.launch {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            baseDatos.clearAllTables()
            archivos.raiz.deleteRecursively()
        }
        credenciales.eliminarTodo()
        repositorio.restablecer()
        TrabajadorRecordatorioRepaso.cancelar(contexto)
        alTerminar()
    }
}

private data class EdicionTexto(
    val titulo: String,
    val etiqueta: String,
    val valor: String,
    val secreto: Boolean = false,
    val validar: (String) -> String? = { null },
    val guardar: (String) -> Unit
)

@Composable
fun PantallaAjustes(alAbrirGitHub: () -> Unit, alReiniciar: () -> Unit, vm: AjustesViewModel = hiltViewModel()) {
    val p by vm.preferencias.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var seccionClave by rememberSaveable { mutableStateOf<String?>(null) }
    val seccion = seccionClave?.let { c -> SeccionAjustes.entries.firstOrNull { it.name == c } }
    val ancha = anchoVentanaDp() >= 840
    LaunchedEffect(mensaje) { mensaje?.let { avisos.showSnackbar(it); vm.mensaje.value = null } }
    BackHandler(enabled = seccion != null && !ancha) { seccionClave = null }
    val t = LocalTokens.current
    Column(Modifier.fillMaxSize()) {
        EncabezadoPantalla(
            titulo = if (seccion != null && !ancha) seccion.titulo else "Ajustes",
            subtitulo = if (seccion != null && !ancha) seccion.descripcion else null,
            alVolver = if (seccion != null && !ancha) ({ seccionClave = null }) else null
        )
        SnackbarHost(avisos)
        if (ancha) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(300.dp).fillMaxHeight().background(t.panelLateral).verticalScroll(rememberScrollState())) {
                    SeccionAjustes.entries.forEach { s -> FilaSeccion(s, seccion == s) { seccionClave = s.name } }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(t.bordeSutil))
                Box(Modifier.weight(1f).fillMaxHeight()) { ContenidoSeccion(seccion ?: SeccionAjustes.GENERAL, p, vm, alAbrirGitHub, alReiniciar) }
            }
        } else if (seccion == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SeccionAjustes.entries.forEach { s -> FilaSeccion(s, false) { seccionClave = s.name } }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            ContenidoSeccion(seccion, p, vm, alAbrirGitHub, alReiniciar)
        }
    }
}

@Composable
private fun FilaSeccion(s: SeccionAjustes, activa: Boolean, alPulsar: () -> Unit) {
    val t = LocalTokens.current
    Box(Modifier.fillMaxWidth().background(if (activa) t.seleccionLista else androidx.compose.ui.graphics.Color.Transparent)) {
        FilaAjuste(s.titulo, s.descripcion, s.icono, alPulsar = alPulsar) {
            androidx.compose.material3.Icon(Icons.Outlined.ChevronRight, null, tint = t.textoTenue)
        }
    }
}

@Composable
private fun ContenidoSeccion(seccion: SeccionAjustes, p: PreferenciasUsuario, vm: AjustesViewModel, alAbrirGitHub: () -> Unit, alReiniciar: () -> Unit) {
    var edicion by remember { mutableStateOf<EdicionTexto?>(null) }
    var dialogoPin by remember { mutableStateOf(false) }
    var confirmarBorrado by remember { mutableStateOf(false) }
    var confirmarRestablecer by remember { mutableStateOf(false) }
    var confirmarIA by remember { mutableStateOf(false) }
    val exportador = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportar) }
    val importador = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importar) }
    val perfil by vm.perfil.collectAsStateWithLifecycle()
    val tieneClave by vm.tieneClaveIA.collectAsStateWithLifecycle()
    val tieneClaveCompatible by vm.tieneClaveCompatible.collectAsStateWithLifecycle()
    val versionPython by vm.versionPython.collectAsStateWithLifecycle()
    val ext = LocalTokens.current
    val contexto = androidx.compose.ui.platform.LocalContext.current

    if (seccion == SeccionAjustes.EXTENSIONES) {
        val extVm: ExtensionesViewModel = hiltViewModel()
        PanelExtensiones(extVm)
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        when (seccion) {
            SeccionAjustes.GENERAL -> {
                TituloSeccion("Perfil", Modifier.padding(horizontal = 20.dp))
                FilaAjuste("Nombre", perfil?.nombre ?: "", Icons.Outlined.Person, alPulsar = { edicion = EdicionTexto("Nombre", "¿Cómo te llamamos?", perfil?.nombre ?: "", guardar = { vm.actualizarPerfil(nombre = it) }) })
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NivelExperiencia.entries.forEach { n -> Etiqueta(n.nombreVisible, seleccionada = perfil?.nivel == n.name, alPulsar = { vm.actualizarPerfil(nivel = n) }) }
                }
                TituloSeccion("Arranque", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaInterruptor("Abrir el último proyecto al iniciar", "Entra directo al editor del último proyecto que usaste", p.abrirUltimoProyecto, { v -> vm.cambiar { abrirUltimoProyecto(v) } })
            }
            SeccionAjustes.APARIENCIA -> {
                TituloSeccion("Tema", Modifier.padding(horizontal = 20.dp))
                SelectorChips(listOf(ModoTema.OSCURO to "Oscuro", ModoTema.CLARO to "Claro", ModoTema.SISTEMA to "Sistema"), p.modoTema) { m -> vm.cambiar { modoTema(m) } }
                Texto("El modo oscuro es el predeterminado. «Sistema» sigue el tema de Android.")
                TituloSeccion("Interfaz", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaInterruptor("Animaciones", "Transiciones de paneles, pestañas y cursor", p.animaciones, { v -> vm.cambiar { animaciones(v) } })
                FilaInterruptor("Mostrar ruta de navegación", "Archivo y símbolo actual sobre el editor", p.mostrarMigas, { v -> vm.cambiar { mostrarMigas(v) } })
                FilaInterruptor("Mostrar barra de estado", "Línea, lenguaje y problemas", p.mostrarBarraEstado, { v -> vm.cambiar { mostrarBarraEstado(v) } })
                FilaInterruptor("Barra de símbolos", "Atajos táctiles sobre el teclado", p.barraSimbolos, { v -> vm.cambiar { barraSimbolos(v) } })
                FilaInterruptor("Alto contraste", "Refuerza textos secundarios y bordes", p.altoContraste, { v -> vm.cambiar { altoContraste(v) } })
                Texto("Temas del editor adicionales se instalan en Extensiones (se leen temas de paquetes de VS Code).")
            }
            SeccionAjustes.EDITOR -> {
                TituloSeccion("Escritura", Modifier.padding(horizontal = 20.dp))
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sangría:", style = MaterialTheme.typography.bodyMedium)
                    listOf(2, 4, 8).forEach { n -> Etiqueta("$n espacios", seleccionada = p.sangriaEspacios == n, alPulsar = { vm.cambiar { sangriaEspacios(n) } }) }
                }
                FilaInterruptor("Cierre automático", "Cierra paréntesis, llaves, corchetes y comillas", p.autocierre, { v -> vm.cambiar { autocierre(v) } })
                FilaInterruptor("Autocompletado", "Palabras clave, identificadores y fragmentos", p.autocompletado, { v -> vm.cambiar { autocompletado(v) } })
                FilaInterruptor("Ajuste de línea", "Divide las líneas largas en lugar de desplazar", p.ajusteLinea, { v -> vm.cambiar { ajusteLinea(v) } })
                FilaInterruptor("Formatear al guardar", "Solo JSON, CSS y HTML (los demás lenguajes no se reformatean)", p.formatearAlGuardar, { v -> vm.cambiar { formatearAlGuardar(v) } })
                TituloSeccion("Vista", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaInterruptor("Números de línea", null, p.numerosLinea, { v -> vm.cambiar { numerosLinea(v) } })
                FilaInterruptor("Resaltar línea actual", null, p.resaltarLineaActual, { v -> vm.cambiar { resaltarLineaActual(v) } })
                FilaInterruptor("Mostrar espacios", "Marca espacios y tabulaciones", p.mostrarInvisibles, { v -> vm.cambiar { mostrarInvisibles(v) } })
                FilaInterruptor("Encabezado fijo", "Mantiene visible la función o clase actual (Python y JavaScript)", p.stickyScroll, { v -> vm.cambiar { stickyScroll(v) } })
                TituloSeccion("Validación", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaInterruptor("Validar mientras escribes", "Analiza el código tras una pausa", p.validacionEnVivo, { v -> vm.cambiar { validacionEnVivo(v) } })
            }
            SeccionAjustes.TIPOGRAFIA -> {
                TituloSeccion("Interfaz", Modifier.padding(horizontal = 20.dp))
                Deslizador("Escala del texto de la interfaz", "${(p.escalaTexto * 100).toInt()} %", p.escalaTexto, 0.85f..1.5f, 12) { vm.cambiar { escalaTexto(it) } }
                Texto("Se suma al tamaño de fuente configurado en Android (Ajustes › Pantalla › Tamaño de fuente).")
                TituloSeccion("Código", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                SelectorChips(FuenteEditor.entries.map { it to it.nombreVisible }, p.fuenteEditor) { f -> vm.cambiar { fuenteEditor(f) } }
                Deslizador("Tamaño de fuente del editor", "${p.tamanoFuenteEditor} sp", p.tamanoFuenteEditor.toFloat(), 10f..28f, 17) { vm.cambiar { tamanoFuenteEditor(it.toInt()) } }
                Deslizador("Interlineado", "%.1f×".format(p.interlineadoEditor), p.interlineadoEditor, 1f..2f, 9) { vm.cambiar { interlineadoEditor(it) } }
                FilaInterruptor("Ligaduras de fuente", "Combina símbolos como => y != si la fuente lo permite", p.ligaduras, { v -> vm.cambiar { ligaduras(v) } })
                TituloSeccion("Vista previa", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Text(
                    "def saludar(nombre):\n    return f\"Hola, {nombre}\"  # != =>",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (p.fuenteEditor == FuenteEditor.JETBRAINS_MONO) FuenteMonoJetBrains else androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = p.tamanoFuenteEditor.sp, lineHeight = (p.tamanoFuenteEditor * p.interlineadoEditor * 1.15f).sp),
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(ext.superficie).padding(12.dp)
                )
            }
            SeccionAjustes.ARCHIVOS -> {
                TituloSeccion("Explorador", Modifier.padding(horizontal = 20.dp))
                SelectorChips(OrdenArchivos.entries.map { it to it.nombreVisible }, p.ordenArchivos) { o -> vm.cambiar { ordenArchivos(o) } }
                FilaInterruptor("Mostrar extensiones", "En el explorador de archivos", p.mostrarExtensiones, { v -> vm.cambiar { mostrarExtensiones(v) } })
                FilaInterruptor("Confirmar antes de eliminar", "Pide confirmación al borrar archivos o carpetas", p.confirmarEliminar, { v -> vm.cambiar { confirmarEliminar(v) } })
                TituloSeccion("Guardado", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                GuardadoAutomatico.entries.forEach { g ->
                    FilaAjuste(g.nombreVisible, g.descripcion, alPulsar = { vm.cambiar { guardadoAutomatico(g) } }) { androidx.compose.material3.RadioButton(selected = p.guardadoAutomatico == g, onClick = { vm.cambiar { guardadoAutomatico(g) } }) }
                }
                TituloSeccion("Búsqueda", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaAjuste("Excluir de la búsqueda", p.excluirBusqueda.ifBlank { "Nada excluido (ejemplo: *.json, node_modules/**)" }, alPulsar = { edicion = EdicionTexto("Excluir de la búsqueda", "Patrones separados por comas", p.excluirBusqueda, guardar = { v -> vm.cambiar { excluirBusqueda(v) } }) })
                TituloSeccion("Copias de seguridad", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaAjuste("Exportar copia", "Proyectos y fragmentos en un archivo .zip (sin cifrar)", Icons.Outlined.Backup, alPulsar = { exportador.launch("redact_code_copia_${LocalDate.now()}.zip") })
                FilaAjuste("Restaurar copia", "Importa un archivo .zip creado por Redact Code", Icons.Outlined.Restore, alPulsar = { importador.launch(arrayOf("application/zip", "application/octet-stream")) })
            }
            SeccionAjustes.TERMINAL -> {
                TituloSeccion("Terminal", Modifier.padding(horizontal = 20.dp))
                Deslizador("Tiempo máximo por comando", "${p.limiteTerminalSegundos} s", p.limiteTerminalSegundos.toFloat(), 5f..300f, 58) { vm.cambiar { limiteTerminal(it.toInt()) } }
                Deslizador("Tamaño de fuente de la terminal", "${p.tamanoFuenteTerminal} sp", p.tamanoFuenteTerminal.toFloat(), 10f..22f, 11) { vm.cambiar { tamanoFuenteTerminal(it.toInt()) } }
                TituloSeccion("Ejecución de programas", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Deslizador("Tiempo máximo de un programa", "${p.limiteEjecucionSegundos} s", p.limiteEjecucionSegundos.toFloat(), 3f..60f, 56) { vm.cambiar { limiteEjecucion(it.toInt()) } }
                Texto("La terminal ejecuta el shell de Android dentro de la carpeta del proyecto y no puede salir de ella con cd. Los comandos python y node usan los motores integrados. No es interactiva: los programas no reciben teclado.")
            }
            SeccionAjustes.LENGUAJES -> {
                TituloSeccion("Validación por lenguaje", Modifier.padding(horizontal = 20.dp))
                Lenguaje.entries.filter { it != Lenguaje.TEXTO }.forEach { l ->
                    FilaInterruptor(l.nombreVisible, "Subraya errores y llena el panel Problemas", l.name !in p.lenguajesSinValidacion, { v -> vm.cambiar { validacionLenguaje(l.name, v) } })
                }
                TituloSeccion("Motores", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaAjuste("Python", "CPython $versionPython vía Chaquopy")
                FilaAjuste("JavaScript", vm.motorJavaScript)
                FilaAjuste("HTML, CSS y JSON", "Analizadores integrados (jsoup y analizadores propios)")
                TituloSeccion("Retraso de validación", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Deslizador("Pausa antes de validar", "${p.retrasoValidacionMs} ms", p.retrasoValidacionMs.toFloat(), 150f..2000f, 36) { vm.cambiar { retrasoValidacion((it / 50).toInt() * 50) } }
            }
            SeccionAjustes.IA -> {
                Texto("El mentor funciona sin conexión con explicaciones locales. Opcionalmente puedes usar un proveedor de IA con tu propia clave: el código que consultes se enviará al proveedor, el uso puede tener costo y las claves se guardan cifradas en tu dispositivo.")
                TituloSeccion("Claude (Anthropic)", Modifier.padding(horizontal = 20.dp))
                FilaAjuste("Clave de API", if (tieneClave) "Configurada (cifrada)" else "Sin configurar", Icons.Outlined.Psychology, alPulsar = { edicion = EdicionTexto("Clave de API de Anthropic", "Clave", "", secreto = true, validar = { if (it.length > 20) null else "La clave es demasiado corta" }, guardar = { vm.guardarClave(AlmacenCredenciales.CLAVE_IA, it) }) })
                FilaAjuste("Modelo", p.modeloIA, alPulsar = { edicion = EdicionTexto("Modelo de Claude", "Identificador del modelo", p.modeloIA, guardar = { v -> vm.cambiar { modeloIA(v) } }) })
                if (tieneClave) FilaAjuste("Eliminar clave", null, alPulsar = { vm.eliminarClaveIA() })
                TituloSeccion("Proveedor compatible con OpenAI", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaAjuste("Dirección del servicio", p.urlCompatible.ifBlank { "Sin configurar (ejemplo: https://api.ejemplo.com/v1)" }, alPulsar = { edicion = EdicionTexto("Dirección del servicio", "URL base", p.urlCompatible, validar = { ProveedorCompatibleOpenAI.validarUrl(it) }, guardar = { v -> vm.cambiar { urlCompatible(v) } }) })
                FilaAjuste("Modelo", p.modeloCompatible.ifBlank { "Sin configurar" }, alPulsar = { edicion = EdicionTexto("Modelo", "Identificador del modelo", p.modeloCompatible, guardar = { v -> vm.cambiar { modeloCompatible(v) } }) })
                FilaAjuste("Clave de API", if (tieneClaveCompatible) "Configurada (cifrada)" else "Sin configurar (opcional en servidores locales)", alPulsar = { edicion = EdicionTexto("Clave del proveedor", "Clave", "", secreto = true, guardar = { vm.guardarClave(AlmacenCredenciales.CLAVE_COMPATIBLE, it) }) })
                if (tieneClaveCompatible) FilaAjuste("Eliminar clave", null, alPulsar = { vm.eliminarClaveCompatible() })
                Texto("Se admite https:// o una dirección de tu red local con http://. Funciona con servicios que implementan /chat/completions, incluidos servidores propios.")
                TituloSeccion("Consentimiento", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaInterruptor(
                    "Permitir enviar código a proveedores de IA", if (p.consentimientoIA) "Consentimiento otorgado" else "Requiere tu consentimiento explícito",
                    p.consentimientoIA, { activo -> if (activo) confirmarIA = true else vm.cambiar { consentimientoIA(false); proveedorIA(PreferenciasUsuario.PROVEEDOR_IA_LOCAL) } }
                )
                if (p.consentimientoIA) FilaInterruptor("Usar Claude en el mentor", "Si no, el mentor usa explicaciones locales", p.proveedorIA == PreferenciasUsuario.PROVEEDOR_IA_ANTHROPIC && tieneClave, { v -> vm.cambiar { proveedorIA(if (v) PreferenciasUsuario.PROVEEDOR_IA_ANTHROPIC else PreferenciasUsuario.PROVEEDOR_IA_LOCAL) } }, habilitado = tieneClave)
            }
            SeccionAjustes.AGENTES -> {
                Texto("El agente propone cambios en varios archivos y tú los revisas antes de aplicarlos. Estos permisos limitan lo que puede proponer; nada se guarda sin tu aceptación.")
                TituloSeccion("Proveedor", Modifier.padding(horizontal = 20.dp))
                SelectorChips(ProveedorAgenteTipo.entries.map { it to it.nombreVisible }, p.proveedorAgente) { v -> vm.cambiar { proveedorAgente(v) } }
                Texto(if (p.consentimientoIA) "El consentimiento para enviar código está activo." else "Activa el consentimiento en Inteligencia artificial para usar el agente.")
                TituloSeccion("Permisos", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaInterruptor("Leer el proyecto", "Envía el contenido de varios archivos como contexto. Si se desactiva, solo el archivo activo.", p.agenteLeerProyecto, { v -> vm.cambiar { agenteLeerProyecto(v) } })
                FilaInterruptor("Crear archivos", null, p.agenteCrear, { v -> vm.cambiar { agenteCrear(v) } })
                FilaInterruptor("Modificar archivos", null, p.agenteModificar, { v -> vm.cambiar { agenteModificar(v) } })
                FilaInterruptor("Eliminar archivos", "Desactivado por seguridad", p.agenteEliminar, { v -> vm.cambiar { agenteEliminar(v) } })
                Deslizador("Máximo de archivos de contexto", "${p.agenteMaxArchivos}", p.agenteMaxArchivos.toFloat(), 1f..100f, 98) { vm.cambiar { agenteMaxArchivos(it.toInt()) } }
                TituloSeccion("Google Antigravity", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Texto("Redact Code no intercambia datos con Antigravity. Este acceso abre en el navegador del teléfono el panel de control remoto de Antigravity, donde ves y diriges los agentes que corren en tu PC. Requisitos: en la PC, activa el control remoto (en la app: Ajustes › App; en la CLI: /remote-control o agy --remote-control) y en el teléfono inicia sesión con la misma cuenta de Google. La PC debe estar encendida.")
                FilaAjuste("Dirección del panel", p.urlAntigravity, alPulsar = { edicion = EdicionTexto("Dirección del panel de Antigravity", "Pega la dirección que muestra tu PC o deja la predeterminada", p.urlAntigravity, validar = { PanelAntigravity.validar(it) }, guardar = { v -> vm.cambiar { urlAntigravity(v) } }) })
                FilaAjuste("Abrir el panel en el navegador", "Se abre fuera de Redact Code", Icons.AutoMirrored.Outlined.OpenInNew, alPulsar = { abrirEnNavegador(contexto, p.urlAntigravity, { vm.mensaje.value = it }) })
            }
            SeccionAjustes.EXTENSIONES -> Unit
            SeccionAjustes.GIT -> {
                TituloSeccion("Identidad de los commits", Modifier.padding(horizontal = 20.dp))
                FilaAjuste("Nombre del autor", p.gitAutor.ifBlank { "Se usa tu usuario de GitHub" }, alPulsar = { edicion = EdicionTexto("Nombre del autor", "Nombre", p.gitAutor, guardar = { v -> vm.cambiar { gitAutor(v) } }) })
                FilaAjuste("Correo del autor", p.gitCorreo.ifBlank { "Sin correo (GitHub usará el de tu cuenta)" }, alPulsar = { edicion = EdicionTexto("Correo del autor", "Correo", p.gitCorreo, guardar = { v -> vm.cambiar { gitCorreo(v) } }) })
                TituloSeccion("Ramas", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaAjuste("Rama inicial", p.ramaPredeterminada, alPulsar = { edicion = EdicionTexto("Rama inicial", "Nombre de la rama", p.ramaPredeterminada, validar = { ControlVersiones.validarRama(it) }, guardar = { v -> vm.cambiar { ramaPredeterminada(v) } }) })
                TituloSeccion("Publicación", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FilaInterruptor("Avisar que el contenido saldrá del dispositivo", "Muestra un recordatorio al publicar", p.confirmarPublicacion, { v -> vm.cambiar { confirmarPublicacion(v) } })
                FilaAjuste("Cuenta de GitHub", "Token, repositorios y descarga de archivos", Icons.Outlined.Cloud, alPulsar = alAbrirGitHub)
            }
            SeccionAjustes.NOTIFICACIONES -> {
                FilaInterruptor("Recordatorio diario de repaso", "A las ${p.horaRecordatorio}:00", p.recordatoriosRepaso, { v -> vm.recordatorios(v, p.horaRecordatorio) }, Icons.Outlined.Notifications)
                if (p.recordatoriosRepaso) Deslizador("Hora del recordatorio", "${p.horaRecordatorio}:00", p.horaRecordatorio.toFloat(), 0f..23f, 22) { vm.recordatorios(true, it.toInt()) }
                Texto("Solo se notifican repasos pendientes de tu biblioteca de fragmentos. No se envía información a ningún servidor.")
            }
            SeccionAjustes.PRIVACIDAD -> {
                TituloSeccion("Bloqueo", Modifier.padding(horizontal = 20.dp))
                FilaInterruptor("Bloqueo con PIN", if (p.bloqueoPin) "Activo" else "Desactivado", p.bloqueoPin, { activo -> if (activo) dialogoPin = true else vm.quitarPin() }, Icons.Outlined.Lock)
                if (p.bloqueoPin) {
                    FilaAjuste("Cambiar PIN", alPulsar = { dialogoPin = true })
                    FilaInterruptor("Desbloqueo biométrico", if (vm.biometria.disponible()) "Huella o rostro" else "No disponible en este dispositivo", p.bloqueoBiometrico && vm.biometria.disponible(), { v -> vm.cambiar { bloqueoBiometrico(v) } }, Icons.Outlined.Fingerprint, vm.biometria.disponible())
                    FilaInterruptor("Bloquear al volver de segundo plano", "Pide el PIN si estuviste más de 30 s fuera de la aplicación", p.bloquearAlSalir, { v -> vm.cambiar { bloquearAlSalir(v) } })
                }
                TituloSeccion("Tus datos", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Texto("Tus proyectos, progreso y fragmentos se guardan en el almacenamiento privado de la aplicación. Nada se envía a internet salvo que conectes GitHub o actives un proveedor de IA. Las claves se cifran con Android Keystore. Las extensiones nunca ejecutan código.")
            }
            SeccionAjustes.ACCESIBILIDAD -> {
                Deslizador("Escala del texto de la interfaz", "${(p.escalaTexto * 100).toInt()} %", p.escalaTexto, 0.85f..1.5f, 12) { vm.cambiar { escalaTexto(it) } }
                FilaInterruptor("Alto contraste", "Refuerza textos secundarios y bordes", p.altoContraste, { v -> vm.cambiar { altoContraste(v) } })
                FilaInterruptor("Objetivos táctiles grandes", "Filas y botones más altos en el IDE", p.objetivosGrandes, { v -> vm.cambiar { objetivosGrandes(v) } })
                FilaInterruptor("Animaciones", "Desactívalas para reducir el movimiento", p.animaciones, { v -> vm.cambiar { animaciones(v) } })
                Texto("Los colores cumplen contraste WCAG 2.2 AA verificado con pruebas automáticas. Los controles tienen descripciones para TalkBack; la verificación con TalkBack en un dispositivo real queda pendiente.")
            }
            SeccionAjustes.AVANZADO -> {
                FilaAjuste("Restablecer ajustes", "Vuelve a los valores iniciales sin tocar tus proyectos, PIN ni claves", Icons.Outlined.RestartAlt, alPulsar = { confirmarRestablecer = true })
                Separador()
                FilaAjuste("Borrar todos los datos", "Elimina perfil, proyectos, progreso, extensiones y credenciales", Icons.Outlined.DeleteForever, alPulsar = { confirmarBorrado = true })
            }
            SeccionAjustes.ACERCA -> {
                FilaAjuste("Redact Code ${BuildConfig.VERSION_NAME}", "Compilación ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_TYPE}", Icons.Outlined.Info)
                FilaAjuste("Python", "CPython $versionPython vía Chaquopy")
                FilaAjuste("JavaScript", vm.motorJavaScript)
                FilaAjuste("Conexión con PC", if (CompatibilidadEscritorio.DISPONIBLE_EN_ESTA_VERSION) "Disponible" else "Prevista para una fase posterior (no disponible en esta versión)")
                FilaAjuste("Licencias de terceros", "Sora Editor (LGPL-2.1), Acorn (MIT), Rhino (MPL-2.0), jsoup (MIT), Chaquopy (MIT), OkHttp, Retrofit, kotlinx, AndroidX y Kotlin (Apache-2.0), SDK de Anthropic (MIT), JetBrains Mono (OFL-1.1)")
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    edicion?.let { e ->
        var valor by remember(e) { mutableStateOf(e.valor) }
        val error = if (valor.isEmpty()) null else e.validar(valor)
        AlertDialog(
            onDismissRequest = { edicion = null },
            title = { Text(e.titulo) },
            text = { CampoTexto(valor, { valor = it.let { v -> if (e.secreto) v.trim() else v } }, e.etiqueta, error = error, transformacion = if (e.secreto) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None) },
            confirmButton = { TextButton(enabled = (valor.isNotBlank() || (!e.secreto && e.valor.isNotEmpty())) && error == null, onClick = { e.guardar(valor.trim()); edicion = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { edicion = null }) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
    if (confirmarIA) {
        DialogoConfirmacion(
            "Enviar código a un servicio externo",
            "Al activar esta opción, el código y los errores que consultes en el mentor o el agente se enviarán al proveedor que elijas. No se envía nada de forma automática ni sin una consulta tuya. ¿Aceptas?",
            "Acepto",
            alConfirmar = { vm.cambiar { consentimientoIA(true) }; confirmarIA = false },
            alCerrar = { confirmarIA = false }
        )
    }
    if (dialogoPin) {
        var nuevo by remember { mutableStateOf("") }
        var confirmacion by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogoPin = false },
            title = { Text("Establecer PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoTexto(nuevo, { nuevo = it.filter(Char::isDigit).take(8) }, "PIN (4 a 8 dígitos)", opcionesTeclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), transformacion = PasswordVisualTransformation())
                    CampoTexto(confirmacion, { confirmacion = it.filter(Char::isDigit).take(8) }, "Confirmar PIN", error = if (confirmacion.isNotEmpty() && confirmacion != nuevo) "No coincide" else null,
                        opcionesTeclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), transformacion = PasswordVisualTransformation())
                }
            },
            confirmButton = { TextButton(enabled = vm.pinValido(nuevo) && nuevo == confirmacion, onClick = { vm.establecerPin(nuevo); dialogoPin = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { dialogoPin = false }) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
    if (confirmarRestablecer) {
        DialogoConfirmacion("Restablecer ajustes", "Todas las opciones volverán a sus valores iniciales. Tus proyectos, el PIN y las claves no se modifican.", "Restablecer", alConfirmar = { confirmarRestablecer = false; vm.restablecerAjustes() }, alCerrar = { confirmarRestablecer = false })
    }
    if (confirmarBorrado) {
        DialogoConfirmacion("Borrar todos los datos", "Se eliminarán de forma permanente tu perfil, proyectos, archivos, progreso, fragmentos y credenciales guardadas. Esta acción no se puede deshacer.", "Borrar todo", true,
            alConfirmar = { confirmarBorrado = false; vm.borrarTodo(alReiniciar) }, alCerrar = { confirmarBorrado = false })
    }
}

@Composable
private fun Texto(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = LocalTokens.current.textoTenue, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun <T> SelectorChips(opciones: List<Pair<T, String>>, actual: T, alElegir: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEach { (valor, etiqueta) -> Etiqueta(etiqueta, seleccionada = actual == valor, alPulsar = { alElegir(valor) }) }
    }
}

@Composable
private fun Deslizador(titulo: String, valorTexto: String, valor: Float, rango: ClosedFloatingPointRange<Float>, pasos: Int, alCambiar: (Float) -> Unit) {
    var local by remember(valor) { androidx.compose.runtime.mutableFloatStateOf(valor) }
    val acento = LocalTokens.current.acento
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valorTexto, style = MaterialTheme.typography.labelLarge, color = acento)
        }
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { alCambiar(local) }, valueRange = rango, steps = pasos, colors = SliderDefaults.colors(thumbColor = acento, activeTrackColor = acento))
    }
}

fun abrirEnNavegador(contexto: Context, url: String, alError: (String) -> Unit) {
    PanelAntigravity.validar(url)?.let { alError(it); return }
    val intento = android.content.Intent(android.content.Intent.ACTION_VIEW, url.trim().toUri())
        .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try { contexto.startActivity(intento) } catch (e: android.content.ActivityNotFoundException) { alError("No hay un navegador disponible para abrir el panel") }
}
