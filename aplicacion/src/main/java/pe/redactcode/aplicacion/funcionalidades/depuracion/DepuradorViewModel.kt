package pe.redactcode.aplicacion.funcionalidades.depuracion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.funcionalidades.espacio.ViewModelProyecto
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.nucleo.depuracion.EstadoDepuracion
import pe.redactcode.nucleo.depuracion.EventoSesion
import pe.redactcode.nucleo.depuracion.FaseSesion
import pe.redactcode.nucleo.depuracion.PuntosInterrupcion
import pe.redactcode.nucleo.depuracion.ReductorSesion
import pe.redactcode.nucleo.modelo.Lenguaje
import javax.inject.Inject

@HiltViewModel
class DepuradorViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    proyectos: RepositorioProyectos,
    private val servicio: ServicioCodigo,
    private val preferencias: RepositorioPreferencias
) : ViewModelProyecto(estadoGuardado, proyectos) {
    private val _estado = MutableStateFlow(EstadoDepuracion())
    val estado: StateFlow<EstadoDepuracion> = _estado.asStateFlow()
    private val puntosInternos = PuntosInterrupcion()
    private val _puntos = MutableStateFlow<Map<String, List<Int>>>(emptyMap())
    val puntos: StateFlow<Map<String, List<Int>>> = _puntos.asStateFlow()
    private val _archivo = MutableStateFlow<String?>(null)
    val archivo: StateFlow<String?> = _archivo.asStateFlow()
    private var trabajo: Job? = null

    val activa: Boolean get() = _estado.value.fase.let { it != FaseSesion.INACTIVA && it != FaseSesion.TERMINADA }

    fun alternarPunto(ruta: String, linea: Int) {
        puntosInternos.alternar(ruta, linea)
        _puntos.value = puntosInternos.todos()
    }

    fun quitarPuntos(ruta: String) {
        puntosInternos.limpiar(ruta)
        _puntos.value = puntosInternos.todos()
    }

    fun iniciar(ruta: String, lenguaje: Lenguaje, codigo: String, depurar: Boolean) {
        if (activa) return
        if (lenguaje != Lenguaje.PYTHON) { avisar("La ejecución interactiva y la depuración solo están disponibles para Python"); return }
        trabajo = viewModelScope.launch {
            val limite = preferencias.actuales().limiteEjecucionSegundos
            servicio.python.usarEntorno(proyectoId)
            val puntos = if (depurar) puntosInternos.de(ruta) else emptyList()
            _archivo.value = ruta
            _estado.value = ReductorSesion.iniciar(depurar)
            val iniciada = runCatching { servicio.python.iniciarSesion(codigo, depurar, puntos, limite) }.getOrElse { avisar("No se pudo iniciar Python: ${it.message}"); false }
            if (!iniciada) {
                _estado.value = EstadoDepuracion()
                avisar("Ya hay una sesión de Python en curso")
                return@launch
            }
            var fallos = 0
            while (isActive) {
                val texto = try {
                    servicio.python.siguienteEvento(200).also { fallos = 0 }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fallos++
                    if (fallos >= 5) {
                        _estado.update { ReductorSesion.aplicar(it, EventoSesion(tipo = "fin", estado = "ERROR", mensaje = "Se perdió la comunicación con Python: ${e.message}")) }
                        break
                    }
                    delay(200)
                    ""
                }
                val evento = ReductorSesion.interpretar(texto) ?: continue
                _estado.update { ReductorSesion.aplicar(it, evento) }
                if (evento.tipo == "fin") break
            }
        }
    }

    fun enviarEntrada(texto: String) {
        if (_estado.value.fase != FaseSesion.ESPERANDO_ENTRADA) return
        _estado.update { ReductorSesion.alEnviarEntrada(it) }
        enSegundoPlano { servicio.python.enviarEntrada(texto) }
    }

    private fun enSegundoPlano(accion: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { accion() } catch (e: CancellationException) { throw e } catch (e: Exception) { avisar("Python no respondió: ${e.message}") }
        }
    }

    fun comando(nombre: String) {
        if (_estado.value.fase != FaseSesion.PAUSADA) return
        _estado.update { ReductorSesion.alReanudar(it) }
        enSegundoPlano { servicio.python.comandoDepurador(nombre) }
    }

    fun evaluar(expresion: String) {
        if (_estado.value.fase != FaseSesion.PAUSADA || expresion.isBlank()) return
        _estado.update { ReductorSesion.alEvaluar(it, expresion.trim()) }
        val limpia = expresion.trim()
        enSegundoPlano { servicio.python.comandoDepurador("evaluar", limpia) }
    }

    fun detener() {
        if (activa) enSegundoPlano { servicio.python.detenerSesion() }
    }

    fun limpiar() {
        if (!activa) _estado.value = EstadoDepuracion()
    }

    override fun onCleared() {
        if (activa) {
            val python = servicio.python
            Thread { runCatching { python.detenerSesion() } }.start()
        }
    }
}
