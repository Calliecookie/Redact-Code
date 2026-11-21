package pe.redactcode.nucleo

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.redactcode.nucleo.terminal.ErrorNpm
import pe.redactcode.nucleo.terminal.InstaladorNpm
import pe.redactcode.nucleo.terminal.RangoSemver
import pe.redactcode.nucleo.terminal.VersionSemver
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPOutputStream

class NpmTest {
    @get:Rule val carpeta = TemporaryFolder()

    private fun elegir(rango: String, vararg v: String) = RangoSemver.elegir(v.toList(), rango)

    @Test
    fun rangosSemver() {
        val todas = arrayOf("0.2.5", "0.3.0", "1.0.0", "1.2.3", "1.9.0", "2.0.0", "2.1.0-beta.1", "3.0.0")
        assertEquals("1.9.0", elegir("^1.2.3", *todas))
        assertEquals("1.2.3", elegir("~1.2.0", *todas))
        assertEquals("0.2.9", elegir("^0.2.5", "0.2.5", "0.2.9", "0.3.0"))
        assertEquals("1.2.3", elegir("1.2.3", *todas))
        assertEquals("1.9.0", elegir("1.x", *todas))
        assertEquals("3.0.0", elegir("*", *todas))
        assertEquals("1.9.0", elegir(">=1.0.0 <2.0.0", *todas))
        assertEquals("3.0.0", elegir("<1.0.0 || >=3.0.0", *todas))
        assertEquals("1.9.0", elegir("1.0.0 - 1.9.0", *todas))
        assertEquals("2.0.0", elegir("<3", *todas))
        assertNull(elegir("^9.0.0", *todas))
        assertTrue(VersionSemver.analizar("1.2.3-beta")!! < VersionSemver.analizar("1.2.3")!!)
        assertNull(VersionSemver.analizar("abc"))
    }

    private fun tar(entradas: List<Pair<String, ByteArray>>, tipo: Char = '0'): ByteArray {
        val salida = ByteArrayOutputStream()
        entradas.forEach { (nombre, datos) ->
            val cabecera = ByteArray(512)
            nombre.toByteArray().copyInto(cabecera)
            "0000644".toByteArray().copyInto(cabecera, 100)
            String.format("%011o", datos.size).toByteArray().copyInto(cabecera, 124)
            cabecera[156] = tipo.code.toByte()
            "ustar".toByteArray().copyInto(cabecera, 257)
            salida.write(cabecera)
            salida.write(datos)
            salida.write(ByteArray((512 - datos.size % 512) % 512))
        }
        salida.write(ByteArray(1024))
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(salida.toByteArray()) }
        return gz.toByteArray()
    }

    private fun paquete(nombre: String, version: String, extra: List<Pair<String, ByteArray>> = emptyList()) = tar(
        listOf("package/package.json" to """{"name":"$nombre","version":"$version"}""".toByteArray(), "package/index.js" to "module.exports = '$nombre';\n".toByteArray()) + extra
    )

    private fun sha512(b: ByteArray) = "sha512-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(b))

    private class Registro(val meta: Map<String, String>, val archivos: Map<String, ByteArray>) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val ruta = request.path.orEmpty()
            meta[ruta]?.let { return MockResponse().setBody(it) }
            archivos[ruta]?.let { return MockResponse().setBody(Buffer().write(it)) }
            return MockResponse().setResponseCode(404)
        }
    }

    private fun meta(nombre: String, versiones: Map<String, Pair<ByteArray, Map<String, String>>>, base: String, ultima: String, integridadFalsa: Boolean = false): String {
        val v = versiones.entries.joinToString(",") { (ver, par) ->
            val deps = par.second.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
            val integ = if (integridadFalsa) sha512(byteArrayOf(1)) else sha512(par.first)
            "\"$ver\":{\"name\":\"$nombre\",\"version\":\"$ver\",\"dependencies\":{$deps},\"dist\":{\"tarball\":\"$base/$nombre/-/$nombre-$ver.tgz\",\"integrity\":\"$integ\"}}"
        }
        return "{\"name\":\"$nombre\",\"dist-tags\":{\"latest\":\"$ultima\"},\"versions\":{$v}}"
    }

    @Test
    fun instalaConDependenciasYActualizaPackageJson() = runBlocking {
        val s = MockWebServer()
        val base = s.url("/").toString().trimEnd('/')
        val a1 = paquete("paqa", "1.0.0"); val a2 = paquete("paqa", "1.4.0"); val b = paquete("paqb", "2.0.0")
        s.dispatcher = Registro(
            mapOf(
                "/paqa" to meta("paqa", mapOf("1.0.0" to (a1 to emptyMap()), "1.4.0" to (a2 to mapOf("paqb" to "^2.0.0"))), base, "1.4.0"),
                "/paqb" to meta("paqb", mapOf("2.0.0" to (b to emptyMap())), base, "2.0.0")
            ),
            mapOf("/paqa/-/paqa-1.4.0.tgz" to a2, "/paqa/-/paqa-1.0.0.tgz" to a1, "/paqb/-/paqb-2.0.0.tgz" to b)
        )
        s.start()
        try {
            val proyecto = carpeta.newFolder("p")
            val inst = InstaladorNpm(base)
            val nuevos = inst.instalar(proyecto, listOf("paqa"))
            assertEquals(listOf("paqa" to "1.4.0", "paqb" to "2.0.0"), nuevos.map { it.nombre to it.version })
            assertEquals("module.exports = 'paqb';\n", File(proyecto, "node_modules/paqb/index.js").readText())
            val pj = File(proyecto, "package.json").readText()
            assertTrue(pj, pj.contains("\"paqa\": \"^1.4.0\""))
            assertEquals(2, inst.listar(proyecto).size)
            assertTrue(inst.instalar(proyecto, listOf("paqa")).isEmpty())
            assertTrue(inst.desinstalar(proyecto, "paqa"))
            assertFalse(File(proyecto, "node_modules/paqa").exists())
            assertFalse(File(proyecto, "package.json").readText().contains("paqa"))
        } finally { s.shutdown() }
    }

    @Test
    fun rechazaIntegridadIncorrectaRutasPeligrosasYNombresInvalidos() = runBlocking {
        val s = MockWebServer()
        val base = s.url("/").toString().trimEnd('/')
        val c = paquete("paqc", "1.0.0")
        val z = paquete("paqz", "1.0.0", listOf("package/../../escapa.txt" to "x".toByteArray()))
        val enlace = tar(listOf("package/enlace" to ByteArray(0)), tipo = '2')
        s.dispatcher = Registro(
            mapOf(
                "/paqc" to meta("paqc", mapOf("1.0.0" to (c to emptyMap())), base, "1.0.0", integridadFalsa = true),
                "/paqz" to meta("paqz", mapOf("1.0.0" to (z to emptyMap())), base, "1.0.0"),
                "/paql" to meta("paql", mapOf("1.0.0" to (enlace to emptyMap())), base, "1.0.0")
            ),
            mapOf("/paqc/-/paqc-1.0.0.tgz" to c, "/paqz/-/paqz-1.0.0.tgz" to z, "/paql/-/paql-1.0.0.tgz" to enlace)
        )
        s.start()
        try {
            val proyecto = carpeta.newFolder("p2")
            val inst = InstaladorNpm(base)
            fun falla(esp: String, fragmento: String) {
                try { runBlocking { inst.instalar(proyecto, listOf(esp)) }; fail("debia fallar: $esp") }
                catch (e: ErrorNpm) { assertTrue(e.message, e.message!!.contains(fragmento, ignoreCase = true)) }
            }
            falla("paqc", "integridad")
            falla("paqz", "no permitidas")
            falla("noexiste", "No existe")
            falla("../malo", "no válido")
            falla("paqc@git+https://x/y.git", "versiones del registro")
            assertFalse(File(proyecto, "node_modules/paqc").exists())
            assertFalse(File(proyecto.parentFile, "escapa.txt").exists())
            assertFalse(File(proyecto, "node_modules/paql/enlace").exists())
        } finally { s.shutdown() }
    }

    @Test
    fun nombresYEspecificaciones() {
        assertTrue(InstaladorNpm.validarNombre("left-pad"))
        assertTrue(InstaladorNpm.validarNombre("@scope/pkg"))
        assertFalse(InstaladorNpm.validarNombre("Mayusculas"))
        assertFalse(InstaladorNpm.validarNombre("a/b"))
        assertEquals("@scope/pkg" to "1.2.3", InstaladorNpm.separar("@scope/pkg@1.2.3"))
        assertEquals("@scope/pkg" to "latest", InstaladorNpm.separar("@scope/pkg"))
        assertEquals("left-pad" to "^1.0.0", InstaladorNpm.separar("left-pad@^1.0.0"))
    }
}
