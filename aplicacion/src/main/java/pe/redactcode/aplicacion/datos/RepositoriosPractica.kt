package pe.redactcode.aplicacion.datos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.redactcode.aplicacion.nucleo.base_datos.FragmentoCodigoEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.FragmentoConRepaso
import pe.redactcode.aplicacion.nucleo.base_datos.FragmentoDao
import pe.redactcode.aplicacion.nucleo.base_datos.HistorialRepasoEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.IntentoRetoEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.RepasoEntidad
import pe.redactcode.aplicacion.nucleo.base_datos.RetoDao
import pe.redactcode.aplicacion.nucleo.base_datos.RetoEntidad
import pe.redactcode.nucleo.aprendizaje.AlgoritmoSm2
import pe.redactcode.nucleo.aprendizaje.EstadoRepaso
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.retos.CatalogoRetos
import pe.redactcode.nucleo.retos.DefinicionReto
import pe.redactcode.nucleo.retos.FormatoRetos
import pe.redactcode.nucleo.retos.ResultadoImportacion
import pe.redactcode.nucleo.retos.ResultadoPruebas
import pe.redactcode.nucleo.validacion.LectorRecursos
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class RetoConEstado(val definicion: DefinicionReto, val entidad: RetoEntidad)

@Singleton
class RepositorioRetos @Inject constructor(
    private val retoDao: RetoDao,
    private val servicioCodigo: ServicioCodigo,
    private val actividad: RepositorioActividad,
    private val aprendizaje: RepositorioAprendizaje
) {
    private fun entidad(d: DefinicionReto, origen: String) = RetoEntidad(
        id = d.id, titulo = d.titulo, categoria = d.categoria.name, dificultad = d.dificultad.name, lenguaje = d.lenguaje.name,
        definicionJson = LectorRecursos.json.encodeToString(DefinicionReto.serializer(), d), origen = origen
    )

    private fun definicion(e: RetoEntidad): DefinicionReto? =
        runCatching { LectorRecursos.json.decodeFromString(DefinicionReto.serializer(), e.definicionJson) }.getOrNull()

    suspend fun sincronizarCatalogo() {
        val catalogo = CatalogoRetos.retos
        retoDao.insertarSiNoExiste(catalogo.map { entidad(it, RetoEntidad.ORIGEN_CATALOGO) })
        catalogo.forEach { d ->
            val e = entidad(d, RetoEntidad.ORIGEN_CATALOGO)
            retoDao.actualizarDefinicion(e.id, e.titulo, e.categoria, e.dificultad, e.lenguaje, e.definicionJson)
        }
    }

    val retos: Flow<List<RetoConEstado>> = retoDao.observarTodos().map { lista -> lista.mapNotNull { e -> definicion(e)?.let { RetoConEstado(it, e) } } }

    fun observar(id: String): Flow<RetoConEstado?> = retoDao.observar(id).map { e -> e?.let { ent -> definicion(ent)?.let { RetoConEstado(it, ent) } } }

    fun intentos(id: String) = retoDao.observarIntentosDe(id)

    val historial: Flow<List<IntentoRetoEntidad>> = retoDao.observarIntentos()

    suspend fun guardarCodigo(id: String, codigo: String) = retoDao.guardarCodigo(id, codigo)

    suspend fun evaluar(reto: RetoConEstado, codigo: String): ResultadoPruebas {
        val d = reto.definicion
        retoDao.guardarCodigo(d.id, codigo)
        val ejecutor = servicioCodigo.ejecutoresPruebas.getValue(d.lenguaje)
        val validacion = servicioCodigo.validar(d.lenguaje, codigo)
        if (!validacion.esValido) {
            aprendizaje.registrarDiagnosticos(validacion.errores, "reto:${d.id}", "RETO")
            val primero = validacion.errores.first()
            return ResultadoPruebas(emptyList(), "${primero.titulo}: ${primero.mensajeOriginal}", primero.linea, "", validacion.duracionMs)
        }
        val resultado = ejecutor.ejecutar(codigo, d.funcion, d.pruebas, 8_000)
        retoDao.registrarIntento(
            IntentoRetoEntidad(
                retoId = d.id, fecha = System.currentTimeMillis(), aprobado = resultado.todosAprobados, puntaje = resultado.puntaje,
                aprobados = resultado.aprobados, total = resultado.total, duracionMs = resultado.duracionMs, codigo = codigo
            )
        )
        val actual = retoDao.obtenerPorId(d.id) ?: reto.entidad
        val nuevoResuelto = resultado.todosAprobados && !actual.resuelto
        retoDao.guardar(actual.copy(resuelto = actual.resuelto || resultado.todosAprobados, mejorPuntaje = maxOf(actual.mejorPuntaje, resultado.puntaje), codigoGuardado = codigo))
        if (nuevoResuelto) actividad.registrarRetoResuelto()
        if (!resultado.todosAprobados) {
            aprendizaje.registrarFalloReto(d.categoria, d.lenguaje)
            if (resultado.tiempoAgotado) aprendizaje.registrarCodigo(if (d.lenguaje == Lenguaje.PYTHON) "PYR009" else "JSR004", d.lenguaje, "RETO")
        }
        return resultado
    }

    suspend fun importar(texto: String): ResultadoImportacion {
        val resultado = FormatoRetos.importar(texto)
        val existentesCatalogo = CatalogoRetos.retos.map { it.id }.toSet()
        val validos = resultado.retos.filter { it.id !in existentesCatalogo }
        validos.forEach { retoDao.guardar(entidad(it, RetoEntidad.ORIGEN_IMPORTADO)) }
        val omitidos = resultado.retos.size - validos.size
        return if (omitidos > 0) resultado.copy(retos = validos, errores = resultado.errores + "Se omitieron $omitidos retos que ya existen en el catálogo") else resultado
    }
}

@Singleton
class RepositorioFragmentos @Inject constructor(
    private val fragmentoDao: FragmentoDao,
    private val actividad: RepositorioActividad
) {
    val fragmentos: Flow<List<FragmentoConRepaso>> = fragmentoDao.observarTodos()

    fun pendientes(): Flow<List<FragmentoConRepaso>> = fragmentoDao.observarPendientes(LocalDate.now().comoDia())

    suspend fun contarPendientes(): Int = fragmentoDao.contarPendientes(LocalDate.now().comoDia())

    suspend fun obtener(id: Long) = fragmentoDao.obtener(id)

    suspend fun historial(id: Long) = fragmentoDao.historial(id)

    suspend fun guardar(id: Long?, titulo: String, descripcion: String, codigo: String, lenguaje: Lenguaje, etiquetas: List<String>, repasoActivo: Boolean): Long {
        require(titulo.isNotBlank()) { "El título es obligatorio" }
        require(codigo.isNotBlank()) { "El código no puede estar vacío" }
        val ahora = System.currentTimeMillis()
        val textoEtiquetas = etiquetas.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().joinToString(",")
        return if (id == null) {
            val nuevo = fragmentoDao.insertar(FragmentoCodigoEntidad(titulo = titulo.trim(), descripcion = descripcion.trim(), codigo = codigo, lenguaje = lenguaje.name, etiquetas = textoEtiquetas, repasoActivo = repasoActivo, creadoEn = ahora, modificadoEn = ahora))
            fragmentoDao.guardarRepaso(RepasoEntidad(fragmentoId = nuevo, proximaFecha = LocalDate.now().plusDays(1).comoDia()))
            nuevo
        } else {
            val existente = fragmentoDao.obtener(id)?.fragmento ?: throw IllegalArgumentException("El fragmento ya no existe")
            fragmentoDao.actualizar(existente.copy(titulo = titulo.trim(), descripcion = descripcion.trim(), codigo = codigo, lenguaje = lenguaje.name, etiquetas = textoEtiquetas, repasoActivo = repasoActivo, modificadoEn = ahora))
            if (fragmentoDao.repasoDe(id) == null) fragmentoDao.guardarRepaso(RepasoEntidad(fragmentoId = id, proximaFecha = LocalDate.now().plusDays(1).comoDia()))
            id
        }
    }

    suspend fun eliminar(id: Long) = fragmentoDao.eliminar(id)

    suspend fun repasar(fragmentoId: Long, calidad: Int): RepasoEntidad {
        val hoy = LocalDate.now()
        val actual = fragmentoDao.repasoDe(fragmentoId) ?: RepasoEntidad(fragmentoId = fragmentoId, proximaFecha = hoy.comoDia())
        val estado = AlgoritmoSm2.calcular(EstadoRepaso(actual.repeticiones, actual.intervaloDias, actual.factorFacilidad, actual.proximaFecha.comoFecha()), calidad, hoy)
        val nuevo = actual.copy(repeticiones = estado.repeticiones, intervaloDias = estado.intervaloDias, factorFacilidad = estado.factorFacilidad, proximaFecha = estado.proximaFecha.comoDia(), ultimaFecha = hoy.comoDia())
        fragmentoDao.guardarRepaso(nuevo)
        fragmentoDao.registrarHistorial(HistorialRepasoEntidad(fragmentoId = fragmentoId, fecha = hoy.comoDia(), calidad = calidad, intervaloResultante = estado.intervaloDias))
        actividad.registrarRepaso()
        return nuevo
    }
}
