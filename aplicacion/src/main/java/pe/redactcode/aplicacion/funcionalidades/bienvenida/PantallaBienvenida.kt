package pe.redactcode.aplicacion.funcionalidades.bienvenida

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.R
import pe.redactcode.aplicacion.datos.RepositorioPerfil
import pe.redactcode.aplicacion.nucleo.diseno.BotonPrincipal
import pe.redactcode.aplicacion.nucleo.diseno.BotonSecundario
import pe.redactcode.aplicacion.nucleo.diseno.CampoTexto
import pe.redactcode.aplicacion.nucleo.diseno.Etiqueta
import pe.redactcode.aplicacion.nucleo.diseno.FilaInterruptor
import pe.redactcode.aplicacion.nucleo.diseno.LocalColoresExtendidos
import pe.redactcode.aplicacion.nucleo.diseno.ModoTema
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.aplicacion.nucleo.seguridad.AutenticadorBiometrico
import pe.redactcode.aplicacion.nucleo.seguridad.GestorPin
import pe.redactcode.aplicacion.trabajos.TrabajadorRecordatorioRepaso
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.NivelExperiencia
import javax.inject.Inject

@HiltViewModel
class BienvenidaViewModel @Inject constructor(
    private val perfil: RepositorioPerfil,
    private val preferencias: RepositorioPreferencias,
    private val pin: GestorPin,
    val biometria: AutenticadorBiometrico,
    @dagger.hilt.android.qualifiers.ApplicationContext private val contexto: android.content.Context
) : ViewModel() {
    fun finalizar(
        nombre: String, nivel: NivelExperiencia, lenguajes: List<Lenguaje>, tema: ModoTema,
        pinNuevo: String?, usarBiometria: Boolean, recordatorios: Boolean, alTerminar: () -> Unit, alError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                perfil.crear(nombre, nivel, lenguajes)
                preferencias.modoTema(tema)
                if (!pinNuevo.isNullOrEmpty()) {
                    pin.establecer(pinNuevo)
                    preferencias.bloqueoPin(true)
                    preferencias.bloqueoBiometrico(usarBiometria && biometria.disponible())
                }
                preferencias.recordatoriosRepaso(recordatorios)
                if (recordatorios) TrabajadorRecordatorioRepaso.programar(contexto, preferencias.actuales().horaRecordatorio)
                preferencias.bienvenidaCompletada(true)
                alTerminar()
            } catch (e: Exception) {
                alError(e.message ?: "No se pudo crear el perfil")
            }
        }
    }

    fun pinValido(valor: String) = pin.esPinValido(valor)
}

private data class Diapositiva(val icono: ImageVector, val titulo: String, val texto: String)

@Composable
fun PantallaBienvenida(alTerminar: () -> Unit, vm: BienvenidaViewModel = hiltViewModel()) {
    var paso by remember { mutableIntStateOf(0) }
    val diapositivas = listOf(
        Diapositiva(Icons.Outlined.Code, "Un editor pensado para tu teléfono", "Resaltado de sintaxis, pestañas, autocompletado y un teclado de símbolos para escribir código cómodamente."),
        Diapositiva(Icons.Outlined.Psychology, "Errores que se entienden", "Cada error se explica en español, con su causa probable, un ejemplo corregido y una recomendación."),
        Diapositiva(Icons.Outlined.School, "Aprende creando", "Retos con pruebas automáticas, proyectos guiados y repaso espaciado de tus fragmentos de código."),
        Diapositiva(Icons.Outlined.Insights, "Tu progreso, en tu dispositivo", "Redact Code detecta tus dificultades y te recomienda qué practicar. Todo se guarda localmente.")
    )
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        AnimatedContent(targetState = paso, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "bienvenida") { actual ->
            if (actual < diapositivas.size) {
                Presentacion(diapositivas[actual], actual, diapositivas.size, alSiguiente = { paso++ }, alOmitir = { paso = diapositivas.size })
            } else {
                FormularioPerfil(vm, alTerminar)
            }
        }
    }
}

@Composable
private fun Presentacion(d: Diapositiva, indice: Int, total: Int, alSiguiente: () -> Unit, alOmitir: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = alOmitir) { Text("Omitir", color = LocalColoresExtendidos.current.textoTenue) }
        }
        Spacer(Modifier.weight(1f))
        if (indice == 0) {
            Image(painterResource(R.drawable.logo_redact), contentDescription = "Logo de Redact Code", modifier = Modifier.size(132.dp))
            Spacer(Modifier.height(20.dp))
            Text("Redact Code", style = MaterialTheme.typography.headlineLarge)
            Text("Programa, aprende y crea desde cualquier lugar.", style = MaterialTheme.typography.bodyMedium, color = LocalColoresExtendidos.current.textoTenue)
            Spacer(Modifier.height(40.dp))
        }
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(d.icono, contentDescription = null, tint = LocalColoresExtendidos.current.acento)
        }
        Spacer(Modifier.height(20.dp))
        Text(d.titulo, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(d.texto, style = MaterialTheme.typography.bodyLarge, color = LocalColoresExtendidos.current.textoTenue, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                Box(Modifier.size(width = if (i == indice) 20.dp else 6.dp, height = 6.dp).clip(CircleShape).background(if (i == indice) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
        Spacer(Modifier.height(24.dp))
        BotonPrincipal(if (indice == total - 1) "Crear mi perfil" else "Continuar", alSiguiente, Modifier.fillMaxWidth())
    }
}

@Composable
private fun FormularioPerfil(vm: BienvenidaViewModel, alTerminar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var nivel by remember { mutableStateOf(NivelExperiencia.PRINCIPIANTE) }
    val lenguajes = remember { mutableStateListOf(Lenguaje.PYTHON) }
    var tema by remember { mutableStateOf(ModoTema.OSCURO) }
    var usarPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    var biometria by remember { mutableStateOf(false) }
    var recordatorios by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    val permisoNotificaciones = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val alcance = rememberCoroutineScope()

    val errorPin = when {
        !usarPin -> null
        pin.isNotEmpty() && !vm.pinValido(pin) -> "Usa entre 4 y 8 dígitos"
        confirmacion.isNotEmpty() && confirmacion != pin -> "Los PIN no coinciden"
        else -> null
    }
    val valido = nombre.isNotBlank() && lenguajes.isNotEmpty() && (!usarPin || (vm.pinValido(pin) && pin == confirmacion))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Tu perfil", style = MaterialTheme.typography.headlineMedium)
        Text("Se guarda solo en este dispositivo. No necesitas crear una cuenta.", style = MaterialTheme.typography.bodyMedium, color = LocalColoresExtendidos.current.textoTenue)
        CampoTexto(nombre, { nombre = it.take(40) }, "¿Cómo te llamamos?")
        Text("Experiencia", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NivelExperiencia.entries.forEach { Etiqueta(it.nombreVisible, seleccionada = nivel == it, alPulsar = { nivel = it }) }
        }
        Text("Lenguajes que te interesan", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Lenguaje.PYTHON, Lenguaje.JAVASCRIPT, Lenguaje.HTML, Lenguaje.CSS).forEach { l ->
                Etiqueta(l.nombreVisible, seleccionada = l in lenguajes, alPulsar = { if (l in lenguajes) lenguajes.remove(l) else lenguajes.add(l) })
            }
        }
        Text("Tema", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Etiqueta("Oscuro", seleccionada = tema == ModoTema.OSCURO, alPulsar = { tema = ModoTema.OSCURO })
            Etiqueta("Claro", seleccionada = tema == ModoTema.CLARO, alPulsar = { tema = ModoTema.CLARO })
            Etiqueta("Sistema", seleccionada = tema == ModoTema.SISTEMA, alPulsar = { tema = ModoTema.SISTEMA })
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            FilaInterruptor("Proteger con PIN", "Opcional. Se pedirá al abrir la aplicación.", usarPin, { usarPin = it }, Icons.Outlined.Lock)
            if (usarPin) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoTexto(pin, { pin = it.filter(Char::isDigit).take(8) }, "PIN (4 a 8 dígitos)", opcionesTeclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), transformacion = PasswordVisualTransformation())
                    CampoTexto(confirmacion, { confirmacion = it.filter(Char::isDigit).take(8) }, "Confirma tu PIN", error = errorPin, opcionesTeclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), transformacion = PasswordVisualTransformation())
                }
                FilaInterruptor(
                    "Desbloqueo biométrico",
                    if (vm.biometria.disponible()) "Usa tu huella o rostro además del PIN" else "Este dispositivo no tiene biometría configurada",
                    biometria && vm.biometria.disponible(), { biometria = it }, habilitado = vm.biometria.disponible()
                )
            }
            FilaInterruptor("Recordatorios de repaso", "Una notificación diaria cuando tengas fragmentos por repasar", recordatorios, { recordatorios = it })
        }
        error?.let { Text(it, color = LocalColoresExtendidos.current.error, style = MaterialTheme.typography.bodySmall) }
        BotonPrincipal(if (guardando) "Guardando…" else "Empezar", {
            guardando = true
            if (recordatorios && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            alcance.launch {
                vm.finalizar(nombre, nivel, lenguajes.toList(), tema, if (usarPin) pin else null, biometria, recordatorios, alTerminar) { error = it; guardando = false }
            }
        }, Modifier.fillMaxWidth(), habilitado = valido && !guardando)
        Spacer(Modifier.height(8.dp))
    }
}

@HiltViewModel
class BloqueoViewModel @Inject constructor(
    private val pin: GestorPin,
    val biometria: AutenticadorBiometrico,
    val preferencias: RepositorioPreferencias
) : ViewModel() {
    suspend fun verificar(valor: String): Boolean = pin.verificar(valor)
    fun segundosBloqueo() = pin.segundosBloqueo()
}

@Composable
fun PantallaBloqueo(usarBiometria: Boolean, alDesbloquear: () -> Unit, vm: BloqueoViewModel = hiltViewModel()) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    val actividad = androidx.activity.compose.LocalActivity.current as? androidx.fragment.app.FragmentActivity
    val intentarBiometria = {
        if (actividad != null && usarBiometria && vm.biometria.disponible()) {
            vm.biometria.autenticar(actividad, alDesbloquear) { }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { intentarBiometria() }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.logo_redact), contentDescription = "Logo de Redact Code", modifier = Modifier.size(88.dp))
        Spacer(Modifier.height(24.dp))
        Text("Ingresa tu PIN", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        CampoTexto(pin, { pin = it.filter(Char::isDigit).take(8); error = null }, "PIN", error = error, opcionesTeclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), transformacion = PasswordVisualTransformation(), modifier = Modifier.widthIn(max = 320.dp))
        Spacer(Modifier.height(16.dp))
        BotonPrincipal("Desbloquear", {
            alcance.launch {
                if (vm.verificar(pin)) alDesbloquear()
                else {
                    val espera = vm.segundosBloqueo()
                    error = if (espera > 0) "Demasiados intentos. Espera $espera s." else "PIN incorrecto"
                    pin = ""
                }
            }
        }, Modifier.widthIn(min = 200.dp), habilitado = pin.length >= 4)
        if (usarBiometria && vm.biometria.disponible()) {
            Spacer(Modifier.height(8.dp))
            BotonSecundario("Usar biometría", { intentarBiometria() })
        }
        Spacer(Modifier.height(16.dp))
        Text("Si olvidaste tu PIN, deberás borrar los datos de la aplicación desde los ajustes de Android.", style = MaterialTheme.typography.bodySmall, color = LocalColoresExtendidos.current.textoTenue, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(300.dp))
    }
}
