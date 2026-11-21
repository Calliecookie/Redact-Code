package pe.redactcode.aplicacion

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.redactcode.aplicacion.datos.PlantillaProyecto
import pe.redactcode.aplicacion.datos.ProveedorAnthropic
import pe.redactcode.aplicacion.datos.RepositorioActividad
import pe.redactcode.aplicacion.datos.RepositorioAprendizaje
import pe.redactcode.aplicacion.datos.RepositorioFragmentos
import pe.redactcode.aplicacion.datos.RepositorioPerfil
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.datos.RepositorioRetos
import pe.redactcode.aplicacion.datos.ServicioCodigo
import pe.redactcode.aplicacion.datos.comoDia
import pe.redactcode.aplicacion.nucleo.archivos.ErrorArchivo
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.base_datos.BaseDatosRedactCode
import pe.redactcode.aplicacion.nucleo.base_datos.ProyectoEntidad
import pe.redactcode.aplicacion.nucleo.ejecucion.MotorJavaScriptCompuesto
import pe.redactcode.aplicacion.nucleo.ejecucion.MotorJavaScriptSandbox
import pe.redactcode.aplicacion.nucleo.ejecucion.PuentePython
import pe.redactcode.aplicacion.nucleo.preferencias.OrdenArchivos
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.aplicacion.nucleo.seguridad.AlmacenCredenciales
import pe.redactcode.aplicacion.nucleo.seguridad.CifradorKeystore
import pe.redactcode.nucleo.javascript.MotorRhino
import pe.redactcode.nucleo.javascript.ServicioJavaScript
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.NivelExperiencia
import pe.redactcode.nucleo.proyectos.CatalogoProyectosGuiados
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BaseDatosYRepositoriosTest {
    private lateinit var contexto: Context
    private lateinit var bd: BaseDatosRedactCode
    private lateinit var archivos: GestorArchivos
    private lateinit var proyectos: RepositorioProyectos
    private lateinit var actividad: RepositorioActividad
    private lateinit var fragmentos: RepositorioFragmentos
    private lateinit var perfil: RepositorioPerfil
    private lateinit var aprendizaje: RepositorioAprendizaje
    private lateinit var retos: RepositorioRetos
    private lateinit var motor: MotorJavaScriptCompuesto

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = Room.inMemoryDatabaseBuilder(contexto, BaseDatosRedactCode::class.java).allowMainThreadQueries().build()
        archivos = GestorArchivos(contexto)
        archivos.raiz.deleteRecursively()
        archivos.raiz.mkdirs()
        proyectos = RepositorioProyectos(bd.proyectoDao(), bd.archivoDao(), archivos)
        actividad = RepositorioActividad(bd.actividadDao())
        fragmentos = RepositorioFragmentos(bd.fragmentoDao(), actividad)
        perfil = RepositorioPerfil(bd.perfilDao())
        aprendizaje = RepositorioAprendizaje(bd.registroErrorDao(), bd.retoDao(), bd.perfilAprendizajeDao(), perfil, proyectos)
        motor = MotorJavaScriptCompuesto(MotorJavaScriptSandbox(contexto), MotorRhino())
        val servicio = ServicioCodigo(
            PuentePython(contexto), ServicioJavaScript(motor, motor),
            ProveedorAnthropic(AlmacenCredenciales(contexto, CifradorKeystore()), RepositorioPreferencias(contexto))
        )
        retos = RepositorioRetos(bd.retoDao(), servicio, actividad, aprendizaje)
    }

    @After
    fun cerrar() {
        bd.close()
        motor.cerrar()
        archivos.raiz.deleteRecursively()
    }

    @Test
    fun crearProyectoConPlantillaWeb() = runBlocking {
        val id = proyectos.crear("Mi Sitio", "Prueba", PlantillaProyecto.WEB)
        val p = proyectos.obtener(id)!!
        assertEquals("mi_sitio", p.carpeta)
        val arbol = proyectos.arbol(p, OrdenArchivos.NOMBRE)
        assertEquals(listOf("app.js", "estilos.css", "index.html"), arbol.map { it.nombre })
        assertTrue(proyectos.leer(p, "index.html").contains("<title>Mi Sitio</title>"))
        val otro = proyectos.obtener(proyectos.crear("Mi Sitio", "", PlantillaProyecto.PYTHON))!!
        assertEquals("mi_sitio_2", otro.carpeta)
    }

    @Test
    fun operacionesDeArchivosYSeguridadDeRutas() = runBlocking {
        val p = proyectos.obtener(proyectos.crear("Archivos", "", PlantillaProyecto.VACIO))!!
        proyectos.crearCarpeta(p, "", "src")
        val ruta = proyectos.crearArchivo(p, "src", "main.py")
        assertEquals("src/main.py", ruta)
        proyectos.guardar(p, ruta, "print('hola')\nx = 1\n")
        assertEquals("print('hola')\nx = 1\n", proyectos.leer(p, ruta))
        val renombrado = proyectos.renombrar(p, ruta, "app.py")
        assertEquals("src/app.py", renombrado)
        val movido = proyectos.mover(p, renombrado, "")
        assertEquals("app.py", movido)
        val coincidencias = proyectos.buscar(p, "hola")
        assertEquals(1, coincidencias.single { !it.enNombre }.linea)
        try { archivos.crearArchivo(p.carpeta, "", "virus.exe"); fail("Debió rechazar la extensión") } catch (e: ErrorArchivo) { assertTrue(e.message!!.contains("Extensión")) }
        try { archivos.leer(p.carpeta, "../../shared_prefs/x.xml"); fail("Debió bloquear la ruta") } catch (e: ErrorArchivo) { assertTrue(e.message!!.contains("bloqueada")) }
        try { archivos.mover(p.carpeta, "src", "src"); fail("No debe mover una carpeta dentro de sí misma") } catch (e: ErrorArchivo) { }
        proyectos.eliminar(p, "src")
        assertEquals(listOf("app.py", "notas.txt"), proyectos.arbol(p, OrdenArchivos.NOMBRE).map { it.nombre })
        proyectos.eliminarProyecto(p)
        assertNull(proyectos.obtener(p.id))
        assertFalse(archivos.carpetaProyecto(p.carpeta).exists())
    }

    @Test
    fun recientesYEliminacionEnCascada() = runBlocking {
        val p = proyectos.obtener(proyectos.crear("Recientes", "", PlantillaProyecto.PYTHON))!!
        proyectos.registrarApertura(p, "main.py")
        val recientes = proyectos.recientes.first()
        assertEquals("main.py", recientes.single().rutaRelativa)
        bd.proyectoDao().eliminar(p)
        assertTrue(bd.archivoDao().delProyecto(p.id).isEmpty())
    }

    @Test
    fun proyectoGuiadoSeCreaUnaVez() = runBlocking {
        val guiado = CatalogoProyectosGuiados.buscar("calculadora")!!
        val id1 = proyectos.crearDesdeGuiado(guiado)
        val id2 = proyectos.crearDesdeGuiado(guiado)
        assertEquals(id1, id2)
        val p = proyectos.obtener(id1)!!
        assertEquals(ProyectoEntidad.ORIGEN_GUIADO, p.origen)
        assertTrue(proyectos.leer(p, "calculadora.py").contains("def calcular"))
    }

    @Test
    fun fragmentosConRepeticionEspaciada() = runBlocking {
        val id = fragmentos.guardar(null, "Lista por comprensión", "", "[x * 2 for x in datos]", Lenguaje.PYTHON, listOf("Listas", " python "), true)
        val guardado = fragmentos.obtener(id)!!
        assertEquals(listOf("listas", "python"), guardado.fragmento.listaEtiquetas)
        assertEquals(LocalDate.now().plusDays(1).comoDia(), guardado.repaso!!.proximaFecha)
        assertTrue(fragmentos.pendientes().first().isEmpty())
        val repaso = fragmentos.repasar(id, 5)
        assertEquals(1, repaso.repeticiones)
        assertEquals(2.6, repaso.factorFacilidad, 1e-9)
        val segundo = fragmentos.repasar(id, 4)
        assertEquals(6, segundo.intervaloDias)
        assertEquals(2, fragmentos.historial(id).size)
        assertEquals(2, bd.actividadDao().obtener(LocalDate.now().comoDia())!!.repasosCompletados)
        fragmentos.eliminar(id)
        assertNull(bd.fragmentoDao().repasoDe(id))
    }

    @Test
    fun actividadYRacha() = runBlocking {
        val hoy = LocalDate.now()
        bd.actividadDao().guardar(pe.redactcode.aplicacion.nucleo.base_datos.ActividadDiariaEntidad(hoy.minusDays(1).comoDia(), segundosPractica = 600))
        actividad.registrarTiempo(120)
        actividad.registrarGuardado()
        val resumen = actividad.resumen.first()
        assertEquals(2, resumen.rachaActual)
        assertEquals(2, resumen.minutosHoy)
        assertEquals(12, resumen.minutosSemana)
    }

    @Test
    fun perfilYAprendizajeSinDatosInventados() = runBlocking {
        perfil.crear("Ana", NivelExperiencia.PRINCIPIANTE, listOf(Lenguaje.JAVASCRIPT))
        retos.sincronizarCatalogo()
        val calculado = aprendizaje.calcular()
        assertFalse(calculado.tieneDatos)
        assertTrue(calculado.erroresFrecuentes.isEmpty())
        val contexto = aprendizaje.contexto()
        assertEquals(NivelExperiencia.PRINCIPIANTE, contexto.nivel)
        assertEquals(0, contexto.retosCompletados)
    }

    @Test
    fun evaluarRetoRegistraIntentoYProgreso() = runBlocking {
        retos.sincronizarCatalogo()
        val reto = retos.observar("js-fund-doble").first()!!
        val fallo = retos.evaluar(reto, "function doble(n) { return n; }")
        assertFalse(fallo.todosAprobados)
        val errorSintaxis = retos.evaluar(reto, "function doble(n) { return n * ; }")
        assertNotNull(errorSintaxis.errorGeneral)
        val exito = retos.evaluar(reto, "function doble(n) { return n * 2; }")
        assertTrue(exito.todosAprobados)
        val actualizado = retos.observar("js-fund-doble").first()!!
        assertTrue(actualizado.entidad.resuelto)
        assertEquals(100, actualizado.entidad.mejorPuntaje)
        assertEquals(2, bd.retoDao().intentos().size)
        assertEquals(1, bd.actividadDao().obtener(LocalDate.now().comoDia())!!.retosResueltos)
        val perfilCalculado = aprendizaje.calcular()
        assertTrue(perfilCalculado.tieneDatos)
        assertTrue(perfilCalculado.erroresFrecuentes.any { it.first == "JS001" })
    }

    @Test
    fun importarRetosDesdeJson() = runBlocking {
        val json = """
            {"formato":"redactcode.retos","version":1,"retos":[
              {"id":"importado-1","titulo":"Triple","categoria":"FUNDAMENTOS","dificultad":"basico","lenguaje":"javascript",
               "enunciado":"Devuelve el triple","codigoInicial":"function triple(n) {}","funcion":"triple",
               "pruebas":[{"entrada":[2],"esperado":6}]},
              {"id":"js-fund-doble","titulo":"Duplicado","categoria":"FUNDAMENTOS","dificultad":"basico","lenguaje":"javascript",
               "enunciado":"x","codigoInicial":"function doble(n) {}","funcion":"doble","pruebas":[{"entrada":[1],"esperado":2}]}
            ]}
        """.trimIndent()
        retos.sincronizarCatalogo()
        val resultado = retos.importar(json)
        assertEquals(listOf("importado-1"), resultado.retos.map { it.id })
        assertTrue(resultado.errores.any { it.contains("omitieron") })
        val importado = retos.observar("importado-1").first()!!
        assertTrue(retos.evaluar(importado, "function triple(n) { return n * 3; }").todosAprobados)
    }
}
