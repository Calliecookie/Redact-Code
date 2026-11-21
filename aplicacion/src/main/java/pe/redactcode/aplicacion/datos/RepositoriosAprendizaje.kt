package pe.redactcode.aplicacion.datos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.redactcode.aplicacion.nucleo.base_datos.ActividadDao
import pe.redactcode.aplicacion.nucleo.base_datos.ActividadDiariaEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.PerfilAprendizajeDao
import pe.redactcode.aplicacion.nucleo.base_datos.PerfilAprendizajeEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.PerfilDao
import pe.redactcode.aplicacion.nucleo.base_datos.PerfilEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.RegistroErrorDao
import pe.redactcode.aplicacion.nucleo.base_datos.RegistroErrorEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.RetoDao
import pe.redactcode.nucleo.aprendizaje.AnalizadorAprendizaje
import pe.redactcode.nucleo.aprendizaje.CalculadoraRacha
import pe.redactcode.nucleo.aprendizaje.IntentoAprendizaje
import pe.redactcode.nucleo.aprendizaje.PerfilCalculado
import pe.redactcode.nucleo.aprendizaje.Recomendacion
import pe.redactcode.nucleo.aprendizaje.RegistroErrorAprendizaje
import pe.redactcode.nucleo.mentor.ContextoAprendizaje
import pe.redactcode.nucleo.modelo.CategoriaConcepto
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.NivelExperiencia
import pe.redactcode.nucleo.modelo.Severidad
import pe.redactcode.nucleo.retos.CatalogoRetos
import pe.redactcode.nucleo.retos.Dificultad
import pe.redactcode.nucleo.validacion.CatalogoExplicaciones
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

fun LocalDate.comoDia(): Long = toEpochDay()
fun Long.comoFecha(): LocalDate = LocalDate.ofEpochDay(this)
fun Long.fechaDeMarca(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

@Singleton
class RepositorioPerfil @Inject constructor(private val perfilDao: PerfilDao) {
    val perfil: Flow<PerfilEntidad?> = perfilDao.observar()

    suspend fun obtener() = perfilDao.obtener()

    suspend fun crear(nombre: String, nivel: NivelExperiencia, lenguajes: List<Lenguaje>) {
        val limpio = nombre.trim()
        require(limpio.length in 1..40) { "El nombre debe tener entre 1 y 40 caracteres" }
        perfilDao.guardar(PerfilEntidad(nombre = limpio, nivel = nivel.name, lenguajesInteres = lenguajes.joinToString(",") { it.name }, creadoEn = System.currentTimeMillis()))
    }

    suspend fun actualizar(perfil: PerfilEntidad) = perfilDao.guardar(perfil)
}

data class ResumenActividad(
    val rachaActual: Int,
    val mejorRacha: Int,
    val minutosHoy: Int,
    val minutosSemana: Int,
    val retosSemana: Int,
    val semana: List<Pair<LocalDate, Int>>,
    val historial: List<ActividadDiariaEntidad>,
    val diasActivos: Int
)

@Singleton
class RepositorioActividad @Inject constructor(private val actividadDao: ActividadDao) {
    private val exclusion = Mutex()

    private suspend fun modificar(transformar: (ActividadDiariaEntidad) -> ActividadDiariaEntidad) = exclusion.withLock {
        val hoy = LocalDate.now().comoDia()
        val actual = actividadDao.obtener(hoy) ?: ActividadDiariaEntidad(fecha = hoy)
        actividadDao.guardar(transformar(actual))
    }

    suspend fun registrarTiempo(segundos: Long) { if (segundos > 0) modificar { it.copy(segundosPractica = it.segundosPractica + segundos.coerceAtMost(900)) } }
    suspend fun registrarEjecucion() = modificar { it.copy(ejecuciones = it.ejecuciones + 1) }
    suspend fun registrarGuardado() = modificar { it.copy(archivosGuardados = it.archivosGuardados + 1) }
    suspend fun registrarRetoResuelto() = modificar { it.copy(retosResueltos = it.retosResueltos + 1) }
    suspend fun registrarRepaso() = modificar { it.copy(repasosCompletados = it.repasosCompletados + 1) }

    val resumen: Flow<ResumenActividad> = actividadDao.observarTodas().map { todas ->
        val hoy = LocalDate.now()
        val activos = todas.filter { it.segundosPractica >= 60 || it.retosResueltos > 0 || it.archivosGuardados > 0 || it.repasosCompletados > 0 }.map { it.fecha.comoFecha() }
        val porDia = todas.associateBy { it.fecha }
        val semana = (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { dia -> dia to ((porDia[dia.comoDia()]?.segundosPractica ?: 0) / 60).toInt() }
        ResumenActividad(
            rachaActual = CalculadoraRacha.rachaActual(activos, hoy),
            mejorRacha = CalculadoraRacha.mejorRacha(activos),
            minutosHoy = ((porDia[hoy.comoDia()]?.segundosPractica ?: 0) / 60).toInt(),
            minutosSemana = semana.sumOf { it.second },
            retosSemana = (0..6).sumOf { porDia[hoy.minusDays(it.toLong()).comoDia()]?.retosResueltos ?: 0 },
            semana = semana,
            historial = todas,
            diasActivos = activos.size
        )
    }
}

@Singleton
class RepositorioAprendizaje @Inject constructor(
    private val registroErrorDao: RegistroErrorDao,
    private val retoDao: RetoDao,
    private val perfilAprendizajeDao: PerfilAprendizajeDao,
    private val repositorioPerfil: RepositorioPerfil,
    private val repositorioProyectos: RepositorioProyectos
) {
    private val ultimosRegistros = HashMap<String, Long>()

    suspend fun registrarDiagnosticos(diagnosticos: List<Diagnostico>, contexto: String, origen: String = "EDITOR") {
        val ahora = System.currentTimeMillis()
        diagnosticos.filter { it.severidad == Severidad.ERROR && it.codigo != "GEN001" }.distinctBy { it.codigo }.forEach { d ->
            val clave = "$contexto|${d.codigo}"
            val previo = synchronized(ultimosRegistros) { ultimosRegistros[clave] }
            if (previo == null || ahora - previo > 10 * 60_000) {
                synchronized(ultimosRegistros) { ultimosRegistros[clave] = ahora }
                registroErrorDao.insertar(RegistroErrorEntidad(codigo = d.codigo, lenguaje = d.lenguaje.name, categoria = d.categoria.name, origen = origen, fecha = LocalDate.now().comoDia(), marcaTiempo = ahora))
            }
        }
    }

    suspend fun registrarCodigo(codigo: String, lenguaje: Lenguaje, origen: String) {
        val categoria = CatalogoExplicaciones.buscar(codigo)?.categoria ?: CategoriaConcepto.SINTAXIS
        registroErrorDao.insertar(RegistroErrorEntidad(codigo = codigo, lenguaje = lenguaje.name, categoria = categoria.name, origen = origen, fecha = LocalDate.now().comoDia(), marcaTiempo = System.currentTimeMillis()))
    }

    suspend fun registrarFalloReto(categoria: CategoriaConcepto, lenguaje: Lenguaje) {
        registroErrorDao.insertar(RegistroErrorEntidad(codigo = "RETO_${categoria.name}", lenguaje = lenguaje.name, categoria = categoria.name, origen = "RETO", fecha = LocalDate.now().comoDia(), marcaTiempo = System.currentTimeMillis()))
    }

    suspend fun calcular(): PerfilCalculado {
        val retos = retoDao.todos().associateBy { it.id }
        val intentos = retoDao.intentos().mapNotNull { i ->
            val reto = retos[i.retoId] ?: return@mapNotNull null
            IntentoAprendizaje(
                i.retoId,
                runCatching { CategoriaConcepto.valueOf(reto.categoria) }.getOrDefault(CategoriaConcepto.FUNDAMENTOS),
                Lenguaje.desdeClave(reto.lenguaje),
                runCatching { Dificultad.valueOf(reto.dificultad) }.getOrDefault(Dificultad.BASICO),
                i.aprobado, i.puntaje, i.fecha.fechaDeMarca()
            )
        }
        val hoy = LocalDate.now()
        val errores = registroErrorDao.desde(hoy.minusDays(90).comoDia()).map {
            RegistroErrorAprendizaje(runCatching { CategoriaConcepto.valueOf(it.categoria) }.getOrDefault(CategoriaConcepto.SINTAXIS), Lenguaje.desdeClave(it.lenguaje), it.codigo, it.fecha.comoFecha())
        }
        val uso = repositorioProyectos.usoPorLenguaje().toMutableMap()
        intentos.forEach { uso[it.lenguaje] = (uso[it.lenguaje] ?: 0) + 1 }
        val perfil = AnalizadorAprendizaje.calcular(intentos, errores, uso, hoy)
        perfilAprendizajeDao.guardar(perfil.dominios.map {
            PerfilAprendizajeEntidad(it.categoria.name, it.puntaje, it.estado.name, it.intentos, it.errores, System.currentTimeMillis())
        })
        return perfil
    }

    suspend fun recomendaciones(perfil: PerfilCalculado): List<Recomendacion> {
        val resueltos = retoDao.todos().filter { it.resuelto }.map { it.id }.toSet()
        val preferidos = repositorioPerfil.obtener()?.lenguajesInteres?.split(',')?.filter { it.isNotBlank() }?.map { Lenguaje.desdeClave(it) } ?: emptyList()
        val catalogo = retoDao.todos().mapNotNull { CatalogoRetos.buscar(it.id) ?: runCatching { pe.redactcode.nucleo.validacion.LectorRecursos.json.decodeFromString<pe.redactcode.nucleo.retos.DefinicionReto>(it.definicionJson) }.getOrNull() }
        return AnalizadorAprendizaje.recomendar(perfil, catalogo, resueltos, preferidos)
    }

    suspend fun contexto(): ContextoAprendizaje {
        val perfil = calcular()
        val perfilUsuario = repositorioPerfil.obtener()
        val nivelDeclarado = perfilUsuario?.nivel?.let { runCatching { NivelExperiencia.valueOf(it) }.getOrNull() } ?: NivelExperiencia.PRINCIPIANTE
        val nivel = if (perfil.nivelSugerido.ordinal > nivelDeclarado.ordinal) perfil.nivelSugerido else nivelDeclarado
        val resueltos = retoDao.todos().filter { it.resuelto }.map { it.id }.toSet()
        return ContextoAprendizaje(
            nivel = nivel,
            lenguajesUsados = perfil.lenguajesUsados.map { it.first },
            retosCompletados = resueltos.size,
            conceptosDominados = perfil.dominados,
            conceptosPendientes = perfil.dificultades,
            erroresRecurrentes = perfil.erroresFrecuentes.toMap(),
            retosResueltos = resueltos
        )
    }

    fun observarErrores(dias: Long) = registroErrorDao.observarDesde(LocalDate.now().minusDays(dias).comoDia())

    val retosResueltos: Flow<Int> = retoDao.observarResueltos()

    fun cambios(): Flow<Unit> = combine(retoDao.observarIntentos(), registroErrorDao.observarDesde(0)) { _, _ -> }
}
