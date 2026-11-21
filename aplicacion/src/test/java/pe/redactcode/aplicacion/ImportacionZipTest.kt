package pe.redactcode.aplicacion

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.redactcode.aplicacion.datos.RepositorioProyectos
import pe.redactcode.aplicacion.nucleo.archivos.ErrorArchivo
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.base_datos.BaseDatosRedactCode
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ImportacionZipTest {
    private lateinit var contexto: Context
    private lateinit var bd: BaseDatosRedactCode
    private lateinit var archivos: GestorArchivos
    private lateinit var proyectos: RepositorioProyectos

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = Room.inMemoryDatabaseBuilder(contexto, BaseDatosRedactCode::class.java).allowMainThreadQueries().build()
        archivos = GestorArchivos(contexto)
        archivos.raiz.deleteRecursively()
        archivos.raiz.mkdirs()
        proyectos = RepositorioProyectos(bd.proyectoDao(), bd.archivoDao(), archivos)
    }

    @After
    fun cerrar() { bd.close() }

    private fun zip(entradas: Map<String, String>): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { z -> entradas.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return salida.toByteArray()
    }

    @Test
    fun importaZipConCarpetaRaizYBloqueaRutasPeligrosas() = runBlocking {
        val bytes = zip(mapOf("mi-app/index.html" to "<h1>hola</h1>", "mi-app/src/app.js" to "console.log(1)", "mi-app/.git/config" to "x", "mi-app/../../fuera.txt" to "x"))
        val (id, resumen) = proyectos.importarZip(bytes.inputStream(), "mi-app.zip")
        val p = proyectos.obtener(id)!!
        assertEquals("mi-app", p.nombre)
        assertEquals("HTML", p.lenguajePrincipal)
        assertEquals(2, resumen.archivos)
        assertEquals(listOf("index.html", "src/app.js"), archivos.listarArchivos(p.carpeta))
        assertEquals("<h1>hola</h1>", archivos.leer(p.carpeta, "index.html"))
        assertFalse(java.io.File(archivos.raiz.parentFile, "fuera.txt").exists())
        assertTrue(archivos.temporal.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun carpetasOcultasNoSeListanNiSeBuscanNiSeEnvianAlAgente() = runBlocking {
        val id = proyectos.crear("oculto", "", pe.redactcode.aplicacion.datos.PlantillaProyecto.VACIO, mapOf("main.py" to "secreto = 1"))
        val p = proyectos.obtener(id)!!
        val oculta = java.io.File(archivos.carpetaProyecto(p.carpeta), ".redact/commits")
        oculta.mkdirs()
        java.io.File(oculta, "abc.json").writeText("{\"autor\":\"secreto\"}")
        assertEquals(listOf("main.py"), archivos.listarArchivos(p.carpeta))
        assertEquals(listOf("main.py"), archivos.buscar(p.carpeta, "secreto").map { it.rutaRelativa }.distinct())
    }

    @Test
    fun zipInvalidoNoDejaProyectosHuerfanos() = runBlocking {
        try { proyectos.importarZip(ByteArray(100) { 1 }.inputStream(), "roto.zip"); throw AssertionError("Debió fallar") } catch (e: ErrorArchivo) { assertTrue(e.message!!.contains("ZIP")) }
        assertTrue(archivos.raiz.listFiles().orEmpty().isEmpty())
        assertEquals(0, bd.proyectoDao().contarCarpeta("roto"))
    }
}
