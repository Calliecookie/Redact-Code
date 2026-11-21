package pe.redactcode.nucleo

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.redactcode.nucleo.terminal.AnalizadorRequisitos
import pe.redactcode.nucleo.terminal.ComandoGit
import pe.redactcode.nucleo.terminal.ErrorPip
import pe.redactcode.nucleo.terminal.EspecificadorPep440
import pe.redactcode.nucleo.terminal.InstaladorPip
import pe.redactcode.nucleo.terminal.VersionPep440
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PipYGitTest {
    @get:Rule val carpeta = TemporaryFolder()

    private fun v(t: String) = VersionPep440.analizar(t)!!

    @Test
    fun ordenDeVersiones() {
        val orden = listOf("1.0.dev1", "1.0a1", "1.0b2", "1.0rc1", "1.0", "1.0.post1", "1.0.1", "1.1", "2.0", "10.0")
        for (i in 0 until orden.size - 1) assertTrue("${orden[i]} < ${orden[i + 1]}", v(orden[i]) < v(orden[i + 1]))
        assertEquals(0, v("1.0").compareTo(v("1.0.0")))
        assertNull(VersionPep440.analizar("no-es-version"))
        assertTrue(v("1.0rc1").esPrelanzamiento)
        assertFalse(v("1.0").esPrelanzamiento)
    }

    @Test
    fun especificadores() {
        val versiones = listOf("1.0", "1.4.2", "1.5", "2.0", "2.1b1", "3.0")
        assertEquals("3.0", EspecificadorPep440.elegir(versiones, ""))
        assertEquals("1.5", EspecificadorPep440.elegir(versiones, "<2"))
        assertEquals("1.4.2", EspecificadorPep440.elegir(versiones, "==1.4.*"))
        assertEquals("1.5", EspecificadorPep440.elegir(versiones, "~=1.4"))
        assertEquals("1.4.2", EspecificadorPep440.elegir(versiones, "~=1.4.0"))
        assertEquals("2.0", EspecificadorPep440.elegir(versiones, ">=1.5,<3,!=1.5"))
        assertNull(EspecificadorPep440.elegir(versiones, ">=9"))
        assertNull(EspecificadorPep440.elegir(versiones, "basura"))
        assertEquals("requests-toolbelt", EspecificadorPep440.normalizarNombre("Requests_Toolbelt"))
    }

    @Test
    fun requisitosYMarcadores() {
        val r = AnalizadorRequisitos.analizar("urllib3 (<3,>=1.21.1)")!!
        assertEquals("urllib3", r.nombre)
        assertEquals("<3,>=1.21.1", r.especificador)
        assertEquals(">=2.0", AnalizadorRequisitos.analizar("charset-normalizer>=2.0")!!.especificador)
        assertEquals("requests", AnalizadorRequisitos.analizar("requests[socks]==2.31.0")!!.nombre)
        assertNull(AnalizadorRequisitos.analizar("# comentario"))
        assertTrue(AnalizadorRequisitos.marcadorCumple("python_version >= \"3.8\""))
        assertFalse(AnalizadorRequisitos.marcadorCumple("python_version < \"3.8\""))
        assertFalse(AnalizadorRequisitos.marcadorCumple("extra == \"socks\""))
        assertTrue(AnalizadorRequisitos.marcadorCumple("sys_platform == \"linux\" and python_version >= \"3.9\""))
        assertFalse(AnalizadorRequisitos.marcadorCumple("sys_platform == \"win32\""))
        assertTrue(AnalizadorRequisitos.marcadorCumple("sys_platform == \"win32\" or python_version >= \"3.9\""))
    }

    private fun rueda(nombre: String, version: String, requiere: List<String>, extra: Map<String, String> = emptyMap()): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { z ->
            fun add(n: String, t: String) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            add("$nombre/__init__.py", "VERSION = '$version'\n")
            add("$nombre-$version.dist-info/METADATA", "Metadata-Version: 2.1\nName: $nombre\nVersion: $version\n" + requiere.joinToString("") { "Requires-Dist: $it\n" })
            add("$nombre-$version.dist-info/RECORD", "$nombre/__init__.py,,\n$nombre-$version.dist-info/METADATA,,\n")
            extra.forEach { (k, t) -> add(k, t) }
        }
        return salida.toByteArray()
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private class IndiceFalso(val base: () -> String, val ruedas: Map<String, ByteArray>, val meta: Map<String, String>) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val ruta = request.path.orEmpty()
            meta[ruta]?.let { return MockResponse().setBody(it) }
            ruedas[ruta]?.let { return MockResponse().setBody(Buffer().write(it)) }
            return MockResponse().setResponseCode(404)
        }
    }

    private fun json(nombre: String, version: String, archivo: String, bytes: ByteArray, requiere: List<String>, base: String, tipo: String = "bdist_wheel", hash: String = sha(bytes)): String {
        val deps = requiere.joinToString(",") { "\"" + it.replace("\"", "\\\"") + "\"" }
        val a = "{\"filename\":\"$archivo\",\"url\":\"$base/archivos/$archivo\",\"packagetype\":\"$tipo\",\"size\":${bytes.size},\"digests\":{\"sha256\":\"$hash\"}}"
        return "{\"info\":{\"name\":\"$nombre\",\"version\":\"$version\",\"requires_dist\":[$deps]},\"releases\":{\"$version\":[$a]},\"urls\":[$a]}"
    }

    @Test
    fun instalaPaqueteConDependencias() = runBlocking {
        val servidor = MockWebServer()
        val base = servidor.url("/").toString().trimEnd('/')
        val a = rueda("paqa", "1.2.0", listOf("paqb>=2", "paqc; extra == \"x\"", "paqw; sys_platform == \"win32\""))
        val b = rueda("paqb", "2.5.0", emptyList())
        servidor.dispatcher = IndiceFalso(
            { base },
            mapOf("/archivos/paqa-1.2.0-py3-none-any.whl" to a, "/archivos/paqb-2.5.0-py3-none-any.whl" to b),
            mapOf(
                "/pypi/paqa/json" to json("paqa", "1.2.0", "paqa-1.2.0-py3-none-any.whl", a, listOf("paqb>=2", "paqc; extra == \"x\"", "paqw; sys_platform == \"win32\""), base),
                "/pypi/paqb/json" to json("paqb", "2.5.0", "paqb-2.5.0-py3-none-any.whl", b, emptyList(), base)
            )
        )
        servidor.start()
        try {
            val destino = carpeta.newFolder("sp")
            val instalador = InstaladorPip(base)
            val nuevos = instalador.instalar(listOf("paqa"), destino)
            assertEquals(listOf("paqa", "paqb"), nuevos.map { it.nombre })
            assertTrue(File(destino, "paqb/__init__.py").isFile)
            assertEquals(2, instalador.listarInstalados(destino).size)
            assertTrue(instalador.instalar(listOf("paqa"), destino).isEmpty())
            assertTrue(instalador.desinstalar(destino, "PAQA"))
            assertFalse(File(destino, "paqa/__init__.py").exists())
            assertEquals(1, instalador.listarInstalados(destino).size)
        } finally { servidor.shutdown() }
    }

    @Test
    fun rechazaPaqueteInexistenteRuedaNativaHashIncorrectoYZipSlip() = runBlocking {
        val servidor = MockWebServer()
        val base = servidor.url("/").toString().trimEnd('/')
        val buena = rueda("paqh", "1.0", emptyList())
        val maliciosa = rueda("paqz", "1.0", emptyList(), mapOf("../../escapa.txt" to "x"))
        val nativa = rueda("paqn", "1.0", emptyList())
        servidor.dispatcher = IndiceFalso(
            { base },
            mapOf(
                "/archivos/paqh-1.0-py3-none-any.whl" to buena, "/archivos/paqz-1.0-py3-none-any.whl" to maliciosa,
                "/archivos/paqn-1.0-cp313-cp313-linux_aarch64.whl" to nativa
            ),
            mapOf(
                "/pypi/paqh/json" to json("paqh", "1.0", "paqh-1.0-py3-none-any.whl", buena, emptyList(), base, hash = "0".repeat(64)),
                "/pypi/paqz/json" to json("paqz", "1.0", "paqz-1.0-py3-none-any.whl", maliciosa, emptyList(), base),
                "/pypi/paqn/json" to json("paqn", "1.0", "paqn-1.0-cp313-cp313-linux_aarch64.whl", nativa, emptyList(), base)
            )
        )
        servidor.start()
        try {
            val destino = carpeta.newFolder("sp2")
            val instalador = InstaladorPip(base)
            fun falla(req: String, fragmento: String) {
                try { runBlocking { instalador.instalar(listOf(req), destino) }; fail("debia fallar: $req") }
                catch (e: ErrorPip) { assertTrue(e.message, e.message!!.contains(fragmento, ignoreCase = true)) }
            }
            falla("noexiste", "No existe el paquete")
            falla("paqn", "código nativo")
            falla("paqh", "suma de verificación")
            falla("paqz", "no permitidas")
            assertFalse(File(destino.parentFile, "escapa.txt").exists())
            assertFalse(File(destino.parentFile.parentFile, "escapa.txt").exists())
        } finally { servidor.shutdown() }
    }

    @Test
    fun comandoGitFlujoLocal() = runBlocking {
        val raiz = carpeta.newFolder("proyecto")
        val git = ComandoGit(raiz, { "Ana" to "ana@ejemplo.pe" })
        assertEquals(0, git.ejecutar(listOf("init")).codigo)
        File(raiz, "a.txt").writeText("uno\n")
        assertTrue(git.ejecutar(listOf("status", "-s")).salida.contains("a.txt"))
        assertEquals(1, git.ejecutar(listOf("commit", "-m", "x")).codigo)
        assertEquals(0, git.ejecutar(listOf("add", "a.txt")).codigo)
        assertEquals(1, git.ejecutar(listOf("add", "../fuera")).codigo)
        val c = git.ejecutar(listOf("commit", "-m", "primero"))
        assertEquals(c.error, 0, c.codigo)
        assertTrue(git.ejecutar(listOf("log", "--oneline")).salida.contains("primero"))
        File(raiz, "a.txt").writeText("uno\ndos\n")
        assertTrue(git.ejecutar(listOf("diff")).salida.contains("+dos"))
        assertEquals(0, git.ejecutar(listOf("commit", "-a", "-m", "segundo")).codigo)
        assertEquals(0, git.ejecutar(listOf("checkout", "-b", "otra")).codigo)
        assertTrue(git.ejecutar(listOf("branch")).salida.contains("* otra"))
        assertEquals(0, git.ejecutar(listOf("switch", "main")).codigo)
        assertEquals(0, git.ejecutar(listOf("branch", "-d", "otra")).codigo)
        val log = git.ejecutar(listOf("log"))
        assertTrue(log.salida.contains("Ana <ana@ejemplo.pe>"))
        val no = git.ejecutar(listOf("rebase"))
        assertEquals(1, no.codigo)
        assertTrue(no.error.contains("subcomando no disponible"))
        assertEquals(1, git.ejecutar(listOf("clone", "https://gitlab.com/a/b")).codigo)
        assertNotNull(git.ejecutar(listOf("push")).error)
    }

    @Test
    fun comandoGitRemotoUsaLasAcciones() = runBlocking {
        val llamadas = mutableListOf<String>()
        val acciones = object : pe.redactcode.nucleo.terminal.AccionesRemotasGit {
            override suspend fun clonar(url: String, rama: String?): String { llamadas += "clonar $url $rama"; return "clonado" }
            override suspend fun traer(): String { llamadas += "traer"; return "al dia" }
            override suspend fun publicar(): String { llamadas += "publicar"; return "publicado" }
        }
        val git = ComandoGit(carpeta.newFolder("remoto"), { "Ana" to "" }, acciones)
        assertEquals("clonado\n", git.ejecutar(listOf("clone", "https://github.com/usuario/repo.git", "--branch", "dev")).salida)
        assertEquals("clonado\n", git.ejecutar(listOf("clone", "https://github.com/usuario/repo")).salida)
        assertEquals(1, git.ejecutar(listOf("clone", "git@github.com:usuario/repo.git")).codigo)
        assertEquals(1, git.ejecutar(listOf("clone", "https://github.com/usuario")).codigo)
        assertEquals(1, git.ejecutar(listOf("clone", "https://evil.com/a/b")).codigo)
        assertEquals("al dia\n", git.ejecutar(listOf("pull")).salida)
        assertEquals("publicado\n", git.ejecutar(listOf("push")).salida)
        assertEquals(listOf("clonar https://github.com/usuario/repo.git dev", "clonar https://github.com/usuario/repo null", "traer", "publicar"), llamadas)
    }
}
