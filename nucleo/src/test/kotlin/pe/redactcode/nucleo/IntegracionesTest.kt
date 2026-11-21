package pe.redactcode.nucleo

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.redactcode.nucleo.agentes.AccionCambio
import pe.redactcode.nucleo.agentes.AgenteCodigo
import pe.redactcode.nucleo.agentes.ErrorAgente
import pe.redactcode.nucleo.agentes.MensajeAgente
import pe.redactcode.nucleo.agentes.PermisosAgente
import pe.redactcode.nucleo.agentes.ProveedorCompatibleOpenAI
import pe.redactcode.nucleo.control.ControlVersiones
import pe.redactcode.nucleo.control.ErrorControlVersiones
import pe.redactcode.nucleo.extensiones.ErrorExtension
import pe.redactcode.nucleo.extensiones.LectorExtensiones
import pe.redactcode.nucleo.github.ClienteGitHub
import pe.redactcode.nucleo.github.ExtractorZip
import pe.redactcode.nucleo.github.SincronizadorGitHub
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class IntegracionesTest {
    @get:Rule val temporal = TemporaryFolder()

    private fun zip(entradas: Map<String, String>): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { z -> entradas.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return salida.toByteArray()
    }

    @Test
    fun extensionVsixDeclarativa() {
        val vsix = zip(
            mapOf(
                "extension/package.json" to """{"name":"mis-fragmentos","displayName":"Mis fragmentos","publisher":"Ana","version":"1.2.0","license":"MIT","main":"./out/ext.js",
                    "contributes":{"snippets":[{"language":"python","path":"./snippets/py.json"},{"language":"rust","path":"./snippets/rs.json"}],
                    "themes":[{"label":"Noche","uiTheme":"vs-dark","path":"./themes/noche.json"}],"commands":[{"command":"x"}]}}""",
                "extension/snippets/py.json" to """{
                    // comentario
                    "Para": {"prefix": ["para", "for"], "body": ["for ${'$'}{1:i} in range(${'$'}{2:10}):", "\t${'$'}0"], "description": "Bucle for",},
                }""",
                "extension/themes/noche.json" to """{"name":"Noche","type":"dark","colors":{"editor.background":"#101010","editor.foreground":"#eeeeee80"},
                    "tokenColors":[{"scope":["keyword.control","storage.type"],"settings":{"foreground":"#ff79c6"}},{"scope":"string.quoted","settings":{"foreground":"#f1fa8c"}}]}""",
                "../malicioso.txt" to "x"
            )
        )
        val m = LectorExtensiones.desdeVsix(vsix)
        assertEquals("ana.mis-fragmentos", m.id)
        assertEquals("Mis fragmentos", m.nombre)
        assertEquals(1, m.fragmentos.size)
        assertEquals("para", m.fragmentos[0].prefijo)
        assertEquals("PYTHON", m.fragmentos[0].lenguaje)
        assertEquals(1, m.temas.size)
        assertEquals(mapOf("fondo" to "#101010", "texto" to "#eeeeee", "palabraClave" to "#ff79c6", "cadena" to "#f1fa8c"), m.temas[0].colores)
        assertTrue(m.omitido.any { it.contains("Código ejecutable") })
        assertTrue(m.omitido.any { it.contains("commands") })
        assertTrue(m.omitido.any { it.contains("rust") })
        val e = LectorExtensiones.expandir(m.fragmentos[0].cuerpo)
        assertEquals("for i in range(10):\n    ", e.texto)
        assertEquals(4, e.cursor)
        assertEquals(m, LectorExtensiones.desdeJsonRedact(LectorExtensiones.exportar(m)).copy(origen = m.origen))
        try { LectorExtensiones.desdeVsix(zip(mapOf("x.txt" to "y"))); throw AssertionError() } catch (_: ErrorExtension) { }
        try { LectorExtensiones.desdeJsonRedact("""{"id":"../x","nombre":"a","version":"1"}"""); throw AssertionError() } catch (_: ErrorExtension) { }
    }

    @Test
    fun catalogoOpenVsxBuscaYDescargaConValidaciones() = runBlocking {
        val servidor = MockWebServer()
        val paquete = zip(mapOf("extension/package.json" to """{"name":"temas","publisher":"ana","version":"1.0.0","contributes":{"themes":[{"label":"X","uiTheme":"vs-dark","path":"./t.json"}]}}""", "extension/t.json" to """{"name":"X","colors":{"editor.background":"#000000"}}"""))
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.startsWith("/api/-/search") -> MockResponse().setBody("""{"offset":0,"totalSize":2,"extensions":[
                    {"namespace":"ana","name":"temas","version":"1.0.0","displayName":"Temas de Ana","downloadCount":42,"files":{"download":"${servidor.url("/api/ana/temas/1.0.0/file/temas.vsix")}"},"campoNuevo":1},
                    {"namespace":"mal","name":"externo","version":"1","files":{"download":"https://otro-servidor.example/x.vsix"}}]}""")
                request.path == "/api/ana/temas/1.0.0/file/temas.vsix" -> MockResponse().setBody(Buffer().write(paquete))
                else -> MockResponse().setResponseCode(404)
            }
        }
        servidor.start()
        try {
            val cliente = pe.redactcode.nucleo.extensiones.ClienteOpenVsx(servidor.url("/").toString().trimEnd('/'))
            val lista = cliente.buscar("temas", "Themes")
            assertEquals(listOf("ana.temas", "mal.externo"), lista.map { it.identificador })
            assertEquals("Temas de Ana", lista[0].titulo)
            val consulta = servidor.takeRequest().requestUrl!!
            assertEquals("temas", consulta.queryParameter("query"))
            assertEquals("Themes", consulta.queryParameter("category"))
            val m = LectorExtensiones.desdeVsix(cliente.descargar(lista[0]))
            assertEquals(1, m.temas.size)
            try { cliente.descargar(lista[1]); throw AssertionError("Debió bloquear el servidor externo") } catch (e: ErrorExtension) { assertTrue(e.message!!.contains("bloqueada")) }
        } finally {
            servidor.shutdown()
        }
    }

    @Test
    fun direccionDelPanelDeAntigravitySeValida() {
        val v = pe.redactcode.nucleo.agentes.PanelAntigravity
        assertEquals(null, v.validar(v.URL_PREDETERMINADA))
        assertEquals(null, v.validar("https://antigravity.google/remote/abc?x=1"))
        assertEquals(null, v.validar("https://app.antigravity.google.com/r/123"))
        assertNotNull(v.validar("http://antigravity.google.com"))
        assertNotNull(v.validar("https://antigravity.google.com.malicioso.pe"))
        assertNotNull(v.validar("https://usuario:clave@antigravity.google.com"))
        assertNotNull(v.validar("javascript:alert(1)"))
        assertNotNull(v.validar("no es una url"))
    }

    @Test
    fun lecturaLimitadaNoSuperaElTope() {
        val datos = ByteArray(50_000) { 7 }
        assertEquals(10_000, LectorExtensiones.leerLimitado(datos.inputStream(), 10_000).size)
        assertEquals(50_000, LectorExtensiones.leerLimitado(datos.inputStream(), 100_000).size)
        assertEquals(0, LectorExtensiones.leerLimitado(ByteArray(0).inputStream(), 10).size)
    }

    @Test
    fun extractorZipBloqueaRutasPeligrosas() {
        val archivo = temporal.newFile("r.zip")
        archivo.writeBytes(zip(mapOf("repo-abc/a.txt" to "1", "repo-abc/src/b.py" to "2", "repo-abc/../../fuera.txt" to "x")))
        val destino = temporal.newFolder("destino")
        val (extraidos, omitidos) = ExtractorZip.extraer(archivo, destino, quitarCarpetaRaiz = true)
        assertEquals(listOf("a.txt", "src/b.py"), extraidos)
        assertEquals(1, omitidos.size)
        assertFalse(File(destino.parentFile, "fuera.txt").exists())
    }

    private class GitHubFalso : Dispatcher() {
        val blobs = HashMap<String, String>()
        var ramaSha = "c1"
        val arboles = HashMap<String, Map<String, String>>()
        val commitsArbol = HashMap<String, String>()
        val zipBytes = HashMap<String, ByteArray>()
        val cuerposArbol = mutableListOf<String>()
        var contador = 0

        fun agregarCommit(sha: String, archivos: Map<String, String>) {
            val arbol = archivos.mapValues { (_, c) -> ControlVersiones.hashBlob(c.toByteArray()).also { blobs[it] = c } }
            arboles["t$sha"] = arbol
            commitsArbol[sha] = "t$sha"
        }

        private fun json(cuerpo: String, codigo: Int = 200) = MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

        override fun dispatch(request: RecordedRequest): MockResponse {
            val ruta = request.path!!.substringBefore('?')
            val metodo = request.method
            return when {
                metodo == "GET" && ruta.endsWith("/git/ref/heads/main") -> json("""{"ref":"refs/heads/main","object":{"sha":"$ramaSha"}}""")
                metodo == "GET" && ruta.contains("/zipball/") -> MockResponse().setBody(Buffer().write(zipBytes.getValue(ruta.substringAfterLast('/'))))
                metodo == "GET" && ruta.contains("/git/commits/") -> { val sha = ruta.substringAfterLast('/'); json("""{"sha":"$sha","tree":{"sha":"${commitsArbol.getValue(sha)}"}}""") }
                metodo == "GET" && ruta.contains("/git/trees/") -> {
                    val t = ruta.substringAfterLast('/')
                    val entradas = arboles.getValue(t).entries.joinToString(",") { """{"path":"${it.key}","mode":"100644","type":"blob","sha":"${it.value}"}""" }
                    json("""{"sha":"$t","tree":[$entradas],"truncated":false}""")
                }
                metodo == "GET" && ruta.contains("/git/blobs/") -> {
                    val sha = ruta.substringAfterLast('/')
                    json("""{"sha":"$sha","encoding":"base64","content":"${Base64.getEncoder().encodeToString(blobs.getValue(sha).toByteArray())}"}""")
                }
                metodo == "POST" && ruta.endsWith("/git/blobs") -> {
                    val cuerpo = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                    val contenido = String(Base64.getDecoder().decode(cuerpo["content"]!!.jsonPrimitive.content))
                    val sha = ControlVersiones.hashBlob(contenido.toByteArray())
                    blobs[sha] = contenido
                    json("""{"sha":"$sha"}""", 201)
                }
                metodo == "POST" && ruta.endsWith("/git/trees") -> {
                    val texto = request.body.readUtf8()
                    cuerposArbol += texto
                    val cuerpo = Json.parseToJsonElement(texto).jsonObject
                    val base = arboles.getValue(cuerpo["base_tree"]!!.jsonPrimitive.content).toMutableMap()
                    cuerpo["tree"]!!.jsonArray.forEach { e ->
                        val o = e.jsonObject
                        val sha = o["sha"]
                        if (sha == null || sha is JsonNull) base.remove(o["path"]!!.jsonPrimitive.content) else base[o["path"]!!.jsonPrimitive.content] = sha.jsonPrimitive.content
                    }
                    val id = "tn${++contador}"
                    arboles[id] = base
                    json("""{"sha":"$id"}""", 201)
                }
                metodo == "POST" && ruta.endsWith("/git/commits") -> {
                    val cuerpo = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                    val id = "cn${++contador}"
                    commitsArbol[id] = cuerpo["tree"]!!.jsonPrimitive.content
                    json("""{"sha":"$id"}""", 201)
                }
                metodo == "PATCH" && ruta.endsWith("/git/refs/heads/main") -> {
                    ramaSha = Json.parseToJsonElement(request.body.readUtf8()).jsonObject["sha"]!!.jsonPrimitive.content
                    json("""{"ref":"refs/heads/main","object":{"sha":"$ramaSha"}}""")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    @Test
    fun sincronizacionCompletaConGitHub() = runBlocking {
        val falso = GitHubFalso()
        val inicial = mapOf("README.md" to "# Hola\n", "src/app.js" to "console.log(1)\n", ".gitignore" to "node_modules\n")
        falso.agregarCommit("c1", inicial)
        falso.zipBytes["c1"] = zip(inicial.mapKeys { "ana-repo-c1/${it.key}" })
        val servidor = MockWebServer().apply { dispatcher = falso; start() }
        try {
            val cliente = ClienteGitHub({ "token" }, servidor.url("/").toString())
            val raiz = temporal.newFolder("clon")
            val sync = SincronizadorGitHub(cliente, raiz)
            val clon = sync.clonar("ana", "repo", "main", "Ana", temporal.newFile("descarga.zip"))
            assertEquals(2, clon.archivos)
            assertTrue(clon.diferentesDelRemoto.isEmpty())
            assertEquals("# Hola\n", File(raiz, "README.md").readText())
            assertTrue(File(raiz, ".gitignore").exists())
            assertEquals("c1", sync.vinculo()!!.shaCommit)

            File(raiz, "src/app.js").writeText("console.log(2)\n")
            File(raiz, "README.md").delete()
            File(raiz, "nuevo.py").writeText("print('x')\n")
            sync.vcs.confirmar("Cambios locales", "Ana")
            assertEquals(listOf("nuevo.py", "src/app.js") to listOf("README.md"), sync.pendientesDePublicar())
            val publicado = sync.publicar("Cambios desde Redact Code", null)
            assertEquals(falso.ramaSha, publicado.shaCommit)
            val arbolRemoto = falso.arboles.getValue(falso.commitsArbol.getValue(falso.ramaSha))
            assertEquals(setOf(".gitignore", "src/app.js", "nuevo.py"), arbolRemoto.keys)
            assertTrue(falso.cuerposArbol.last().contains("\"sha\":null"))
            assertEquals(emptyList<String>() to emptyList<String>(), sync.pendientesDePublicar())

            val remoto2 = mapOf(".gitignore" to "node_modules\n", "src/app.js" to "console.log(2)\n", "nuevo.py" to "print('x')\n", "otro.css" to "a{}\n")
            falso.agregarCommit("c9", remoto2)
            falso.ramaSha = "c9"
            val traido = sync.traer("Ana")
            assertEquals(listOf("otro.css"), traido.actualizados)
            assertEquals("a{}\n", File(raiz, "otro.css").readText())
            assertTrue(sync.vcs.estado().isEmpty())

            falso.agregarCommit("c10", remoto2 + ("nuevo.py" to "print('remoto')\n"))
            falso.ramaSha = "c10"
            File(raiz, "nuevo.py").writeText("print('local')\n")
            sync.vcs.confirmar("Local", "Ana")
            val conflicto = sync.traer("Ana")
            assertEquals(listOf("nuevo.py"), conflicto.conflictos)
            assertEquals("print('local')\n", File(raiz, "nuevo.py").readText())
            try { sync.publicar("x", null); throw AssertionError() } catch (e: ErrorControlVersiones) { assertTrue(e.message!!.contains("Traer")) }
        } finally {
            servidor.shutdown()
        }
    }

    @Test
    fun agenteInterpretaCambiosConPermisos() {
        val actuales = mapOf("main.py" to "print(1)\n", "viejo.txt" to "x\n")
        val respuesta = """Aquí está:
```json
{"resumen": "Mejoré el programa", "cambios": [
  {"ruta": "main.py", "accion": "modificar", "contenido": "print(2)\n", "motivo": "Cambio pedido"},
  {"ruta": "util/ayuda.py", "accion": "crear", "contenido": "def f():\n    return 1\n"},
  {"ruta": "viejo.txt", "accion": "eliminar"},
  {"ruta": "../fuera.py", "accion": "crear", "contenido": "x"},
  {"ruta": "x.exe", "accion": "crear", "contenido": "x"}
]}
```"""
        val p = AgenteCodigo.interpretar(respuesta, actuales, PermisosAgente(), { if (it.endsWith(".exe")) "extensión no permitida" else null })
        assertEquals("Mejoré el programa", p.resumen)
        assertEquals(listOf("main.py" to AccionCambio.MODIFICAR, "util/ayuda.py" to AccionCambio.CREAR), p.cambios.map { it.ruta to it.accion })
        assertEquals(3, p.rechazados.size)
        assertTrue(p.rechazados.any { it.contains("sin permiso para eliminar") })
        assertEquals(1, p.cambios[0].diferencias.agregadas)
        val conEliminar = AgenteCodigo.interpretar(respuesta, actuales, PermisosAgente(eliminarArchivos = true, crearArchivos = false), { null })
        assertTrue(conEliminar.cambios.any { it.accion == AccionCambio.ELIMINAR })
        assertTrue(conEliminar.cambios.none { it.accion == AccionCambio.CREAR })
        val texto = AgenteCodigo.interpretar("No puedo ayudar con eso", actuales, PermisosAgente(), { null })
        assertTrue(texto.cambios.isEmpty())
        val ctx = AgenteCodigo.contexto(mapOf("a.py" to "1".repeat(50), "b.py" to "2".repeat(50), "c.py" to "3"), listOf("b.py"), PermisosAgente(maxCaracteres = 60))
        assertEquals(listOf("b.py", "c.py"), ctx.incluidos)
        assertEquals(listOf("a.py"), ctx.omitidos)
        assertTrue(AgenteCodigo.sistema(PermisosAgente()).contains("No tienes permiso para eliminar"))
    }

    @Test
    fun proveedorCompatibleOpenAI() = runBlocking {
        val servidor = MockWebServer()
        servidor.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"{\"resumen\":\"ok\",\"cambios\":[]}"}}]}"""))
        servidor.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        servidor.start()
        try {
            val url = servidor.url("/v1").toString().replace("localhost", "127.0.0.1")
            val p = ProveedorCompatibleOpenAI("Prueba", url, { "clave" }, "modelo-x")
            assertEquals("{\"resumen\":\"ok\",\"cambios\":[]}", p.completar("sistema", listOf(MensajeAgente(MensajeAgente.USUARIO, "hola"))))
            val r = servidor.takeRequest()
            assertEquals("/v1/chat/completions", r.path)
            assertEquals("Bearer clave", r.getHeader("Authorization"))
            val cuerpo = Json.parseToJsonElement(r.body.readUtf8()).jsonObject
            assertEquals("modelo-x", cuerpo["model"]!!.jsonPrimitive.content)
            assertEquals("system", cuerpo["messages"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)
            try { p.completar("s", emptyList()); throw AssertionError() } catch (e: ErrorAgente) { assertTrue(e.message!!.contains("bad key")) }
            assertNotNull(ProveedorCompatibleOpenAI.validarUrl("http://ejemplo.com"))
        } finally {
            servidor.shutdown()
        }
    }
}
