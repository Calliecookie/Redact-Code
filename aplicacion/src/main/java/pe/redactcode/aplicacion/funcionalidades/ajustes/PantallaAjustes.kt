package pe.redactcode.aplicacion.funcionalidades.ajustes

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restore
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import pe.redactcode.aplicacion.datos.RepositorioPerfil
import pe.redactcode.aplicacion.datos.ServicioCodigo
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
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.ModoTema
import pe.redactcode.aplicacion.nucleo.diseno.Separador
import pe.redactcode.aplicacion.nucleo.diseno.TituloSeccion
import pe.redactcode.aplicacion.nucleo.preferencias.FuenteEditor
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.aplicacion.nucleo.seguridad.AlmacenCredenciales
import pe.redactcode.aplicacion.nucleo.seguridad.AutenticadorBiometrico
import pe.redactcode.aplicacion.nucleo.seguridad.GestorPin
import pe.redactcode.aplicacion.trabajos.TrabajadorRecordatorioRepaso
import pe.redactcode.nucleo.escritorio.CompatibilidadEscritorio
import pe.redactcode.nucleo.modelo.NivelExperiencia
import java.time.LocalDate
import javax.inject.Inject

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
    private val servicio: ServicioCodigo
) : ViewModel() {
    val preferencias: StateFlow<PreferenciasUsuario> = repositorio.preferencias.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenciasUsuario())
    val perfil: StateFlow<PerfilEntidad?> = perfiles.perfil.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mensaje = MutableStateFlow<String?>(null)
    val tieneClaveIA = MutableStateFlow(false)
    val versionPython = MutableStateFlow("cargando…")

    init {
        viewModelScope.launch { tieneClaveIA.value = credenciales.leer(AlmacenCredenciales.CLAVE_IA) != null }
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
        pin.eliminar(); repositorio.bloqueoPin(false); repositorio.bloqueoBiometrico(false); mensaje.value = "Bloqueo desactivado"
    }

    fun pinValido(v: String) = pin.esPinValido(v)

    fun recordatorios(activo: Boolean, hora: Int) = viewModelScope.launch {
        repositorio.recordatoriosRepaso(activo)
        repositorio.horaRecordatorio(hora)
        if (activo) TrabajadorRecordatorioRepaso.programar(contexto, hora) else TrabajadorRecordatorioRepaso.cancelar(contexto)
    }

    fun guardarClaveIA(clave: String) = viewModelScope.launch {
        credenciales.guardar(AlmacenCredenciales.CLAVE_IA, clave.trim())
        tieneClaveIA.value = true
        mensaje.value = "Clave guardada de forma cifrada"
    }

    fun eliminarClaveIA() = viewModelScope.launch {
        credenciales.eliminar(AlmacenCredenciales.CLAVE_IA)
        repositorio.proveedorIA(PreferenciasUsuario.PROVEEDOR_IA_LOCAL)
        repositorio.consentimientoIA(false)
        tieneClaveIA.value = false
    }

    fun exportar(uri: android.net.Uri) = viewModelScope.launch {
        mensaje.value = runCatching { copias.exportar(uri) }.fold({ "Copia creada: ${it.proyectos} proyectos, ${it.archivos} archivos, ${it.fragmentos} fragmentos" }, { "No se pudo exportar: ${it.message}" })
    }

    fun importar(uri: android.net.Uri) = viewModelScope.launch {
        mensaje.value = runCatching { copias.importar(uri) }.fold({ "Restaurados ${it.proyectos} proyectos y ${it.fragmentos} fragmentos" }, { "No se pudo importar: ${it.message}" })
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

@Composable
fun PantallaAjustes(alAbrirGitHub: () -> Unit, alReiniciar: () -> Unit, vm: AjustesViewModel = hiltViewModel()) {
    val p by vm.preferencias.collectAsStateWithLifecycle()
    val perfil by vm.perfil.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val tieneClave by vm.tieneClaveIA.collectAsStateWithLifecycle()
    val versionPython by vm.versionPython.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var dialogoPin by remember { mutableStateOf(false) }
    var dialogoNombre by remember { mutableStateOf(false) }
    var dialogoClave by remember { mutableStateOf(false) }
    var dialogoModelo by remember { mutableStateOf(false) }
    var confirmarBorrado by remember { mutableStateOf(false) }
    var confirmarIA by remember { mutableStateOf(false) }
    val exportador = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportar) }
    val importador = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importar) }
    LaunchedEffect(mensaje) { mensaje?.let { avisos.showSnackbar(it); vm.mensaje.value = null } }
    val ext = LocalColoresExtendidos.current

    Column(Modifier.fillMaxSize()) {
        EncabezadoPantalla("Ajustes")
        SnackbarHost(avisos)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Seccion("Perfil")
            FilaAjuste("Nombre", perfil?.nombre ?: "", Icons.Outlined.Person, alPulsar = { dialogoNombre = true })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NivelExperiencia.entries.forEach { n -> Etiqueta(n.nombreVisible, seleccionada = perfil?.nivel == n.name, alPulsar = { vm.actualizarPerfil(nivel = n) }) }
            }

            Seccion("Apariencia")
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Etiqueta("Oscuro", seleccionada = p.modoTema == ModoTema.OSCURO, alPulsar = { vm.cambiar { modoTema(ModoTema.OSCURO) } })
                Etiqueta("Claro", seleccionada = p.modoTema == ModoTema.CLARO, alPulsar = { vm.cambiar { modoTema(ModoTema.CLARO) } })
                Etiqueta("Sistema", seleccionada = p.modoTema == ModoTema.SISTEMA, alPulsar = { vm.cambiar { modoTema(ModoTema.SISTEMA) } })
            }
            Deslizador("Tamaño del texto de la interfaz", "${(p.escalaTexto * 100).toInt()} %", p.escalaTexto, 0.85f..1.35f, 9) { vm.cambiar { escalaTexto(it) } }
            FilaInterruptor("Animaciones", "Transiciones y cursor animado", p.animaciones, { v -> vm.cambiar { animaciones(v) } })
            FilaInterruptor("Alto contraste", "Mejora la legibilidad de textos secundarios", p.altoContraste, { v -> vm.cambiar { altoContraste(v) } })

            Seccion("Editor")
            Deslizador("Tamaño de fuente del editor", "${p.tamanoFuenteEditor} sp", p.tamanoFuenteEditor.toFloat(), 10f..24f, 13) { vm.cambiar { tamanoFuenteEditor(it.toInt()) } }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FuenteEditor.entries.forEach { f -> Etiqueta(f.nombreVisible, seleccionada = p.fuenteEditor == f, alPulsar = { vm.cambiar { fuenteEditor(f) } }) }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sangría:", style = MaterialTheme.typography.bodyMedium)
                listOf(2, 4).forEach { n -> Etiqueta("$n espacios", seleccionada = p.sangriaEspacios == n, alPulsar = { vm.cambiar { sangriaEspacios(n) } }) }
            }
            FilaInterruptor("Cierre automático", "Cierra paréntesis, llaves, corchetes y comillas", p.autocierre, { v -> vm.cambiar { autocierre(v) } })
            FilaInterruptor("Ajuste de línea", "Divide las líneas largas en lugar de desplazar horizontalmente", p.ajusteLinea, { v -> vm.cambiar { ajusteLinea(v) } })
            FilaInterruptor("Validación mientras escribes", "Analiza el código 400 ms después de cada cambio", p.validacionEnVivo, { v -> vm.cambiar { validacionEnVivo(v) } })
            FilaInterruptor("Mostrar extensiones", "En el explorador de archivos", p.mostrarExtensiones, { v -> vm.cambiar { mostrarExtensiones(v) } })
            Deslizador("Límite de tiempo de ejecución", "${p.limiteEjecucionSegundos} s", p.limiteEjecucionSegundos.toFloat(), 3f..60f, 56) { vm.cambiar { limiteEjecucion(it.toInt()) } }

            Seccion("Mentor e inteligencia artificial")
            Text(
                "El mentor funciona sin conexión con explicaciones locales. Opcionalmente puedes usar Claude (Anthropic) con tu propia clave de API: " +
                    "el código que consultes se enviará a api.anthropic.com, el uso tiene costo según la tarifa de Anthropic y la clave se guarda cifrada en tu dispositivo.",
                style = MaterialTheme.typography.bodySmall, color = ext.textoTenue, modifier = Modifier.padding(horizontal = 20.dp)
            )
            FilaAjuste("Clave de API", if (tieneClave) "Configurada (cifrada)" else "Sin configurar", Icons.Outlined.Psychology, alPulsar = { dialogoClave = true })
            if (tieneClave) {
                FilaInterruptor(
                    "Usar Claude para el mentor", if (p.consentimientoIA) "Consentimiento otorgado" else "Requiere tu consentimiento explícito",
                    p.proveedorIA == PreferenciasUsuario.PROVEEDOR_IA_ANTHROPIC && p.consentimientoIA,
                    { activo -> if (activo) confirmarIA = true else vm.cambiar { proveedorIA(PreferenciasUsuario.PROVEEDOR_IA_LOCAL); consentimientoIA(false) } }
                )
                FilaAjuste("Modelo", p.modeloIA, alPulsar = { dialogoModelo = true })
                FilaAjuste("Eliminar clave", null, alPulsar = { vm.eliminarClaveIA() })
            }

            Seccion("Seguridad y privacidad")
            FilaInterruptor("Bloqueo con PIN", if (p.bloqueoPin) "Activo" else "Desactivado", p.bloqueoPin, { activo -> if (activo) dialogoPin = true else vm.quitarPin() }, Icons.Outlined.Lock)
            if (p.bloqueoPin) {
                FilaAjuste("Cambiar PIN", alPulsar = { dialogoPin = true })
                FilaInterruptor("Desbloqueo biométrico", if (vm.biometria.disponible()) "Huella o rostro" else "No disponible en este dispositivo",
                    p.bloqueoBiometrico && vm.biometria.disponible(), { v -> vm.cambiar { bloqueoBiometrico(v) } }, Icons.Outlined.Fingerprint, vm.biometria.disponible())
            }
            Text(
                "Tus proyectos, progreso y fragmentos se guardan en el almacenamiento privado de la aplicación. Nada se envía a internet salvo que conectes GitHub o actives un proveedor de IA.",
                style = MaterialTheme.typography.bodySmall, color = ext.textoTenue, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Seccion("Recordatorios")
            FilaInterruptor("Recordatorio diario de repaso", "A las ${p.horaRecordatorio}:00", p.recordatoriosRepaso, { v -> vm.recordatorios(v, p.horaRecordatorio) }, Icons.Outlined.Notifications)
            if (p.recordatoriosRepaso) {
                Deslizador("Hora del recordatorio", "${p.horaRecordatorio}:00", p.horaRecordatorio.toFloat(), 0f..23f, 22) { vm.recordatorios(true, it.toInt()) }
            }

            Seccion("Integraciones")
            FilaAjuste("GitHub", "Repositorios, descarga de archivos y commits", Icons.Outlined.Cloud, alPulsar = alAbrirGitHub)

            Seccion("Copias de seguridad")
            FilaAjuste("Exportar copia", "Proyectos y fragmentos en un archivo .zip", Icons.Outlined.Backup, alPulsar = { exportador.launch("redact_code_copia_${LocalDate.now()}.zip") })
            FilaAjuste("Restaurar copia", "Importa un archivo .zip creado por Redact Code", Icons.Outlined.Restore, alPulsar = { importador.launch(arrayOf("application/zip", "application/octet-stream")) })

            Seccion("Acerca de")
            FilaAjuste("Redact Code ${BuildConfig.VERSION_NAME}", "Compilación ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_TYPE}", Icons.Outlined.Info)
            FilaAjuste("Python", "CPython $versionPython vía Chaquopy")
            FilaAjuste("JavaScript", vm.motorJavaScript)
            FilaAjuste("Conexión con PC", if (CompatibilidadEscritorio.DISPONIBLE_EN_ESTA_VERSION) "Disponible" else "Prevista para una próxima fase (no disponible en esta versión)")
            FilaAjuste("Licencias de terceros", "Sora Editor (LGPL-2.1), Acorn (MIT), Rhino (MPL-2.0), jsoup (MIT), Chaquopy (MIT), JetBrains Mono (OFL-1.1), AndroidX y Kotlin (Apache-2.0)")
            Separador()
            FilaAjuste("Borrar todos los datos", "Elimina perfil, proyectos, progreso y credenciales", Icons.Outlined.DeleteForever, alPulsar = { confirmarBorrado = true })
            Spacer(Modifier.height(32.dp))
        }
    }

    if (dialogoNombre) {
        DialogoEntradaTexto("Nombre", "¿Cómo te llamamos?", perfil?.nombre ?: "", "Guardar", alConfirmar = { vm.actualizarPerfil(nombre = it); dialogoNombre = false }, alCerrar = { dialogoNombre = false })
    }
    if (dialogoModelo) {
        DialogoEntradaTexto("Modelo de Claude", "Identificador del modelo", p.modeloIA, "Guardar", alConfirmar = { v -> vm.cambiar { modeloIA(v) }; dialogoModelo = false }, alCerrar = { dialogoModelo = false })
    }
    if (dialogoClave) {
        var clave by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogoClave = false },
            title = { Text("Clave de API de Anthropic") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Obtén tu clave en console.anthropic.com. Se guardará cifrada con Android Keystore y nunca se incluye en la aplicación.", style = MaterialTheme.typography.bodySmall)
                    CampoTexto(clave, { clave = it.trim() }, "Clave", transformacion = PasswordVisualTransformation())
                }
            },
            confirmButton = { TextButton(enabled = clave.length > 20, onClick = { vm.guardarClaveIA(clave); dialogoClave = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { dialogoClave = false }) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
    if (confirmarIA) {
        DialogoConfirmacion(
            "Enviar código a un servicio externo",
            "Al activar esta opción, el código y los errores que consultes en el mentor se enviarán a Anthropic para generar las explicaciones. No se envía nada de forma automática ni sin una consulta tuya. ¿Aceptas?",
            "Acepto",
            alConfirmar = { vm.cambiar { proveedorIA(PreferenciasUsuario.PROVEEDOR_IA_ANTHROPIC); consentimientoIA(true) }; confirmarIA = false },
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
    if (confirmarBorrado) {
        DialogoConfirmacion("Borrar todos los datos", "Se eliminarán de forma permanente tu perfil, proyectos, archivos, progreso, fragmentos y credenciales guardadas. Esta acción no se puede deshacer.", "Borrar todo", true,
            alConfirmar = { confirmarBorrado = false; vm.borrarTodo(alReiniciar) }, alCerrar = { confirmarBorrado = false })
    }
}

@Composable
private fun Seccion(titulo: String) {
    Spacer(Modifier.height(12.dp))
    TituloSeccion(titulo, Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun Deslizador(titulo: String, valorTexto: String, valor: Float, rango: ClosedFloatingPointRange<Float>, pasos: Int, alCambiar: (Float) -> Unit) {
    var local by remember(valor) { androidx.compose.runtime.mutableFloatStateOf(valor) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valorTexto, style = MaterialTheme.typography.labelLarge, color = LocalColoresExtendidos.current.acento)
        }
        Slider(
            value = local, onValueChange = { local = it }, onValueChangeFinished = { alCambiar(local) }, valueRange = rango, steps = pasos,
            colors = SliderDefaults.colors(thumbColor = LocalColoresExtendidos.current.acento, activeTrackColor = LocalColoresExtendidos.current.acento)
        )
    }
}
