package pe.redactcode.nucleo

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pe.redactcode.nucleo.aprendizaje.AlgoritmoSm2
import pe.redactcode.nucleo.aprendizaje.AnalizadorAprendizaje
import pe.redactcode.nucleo.aprendizaje.CalculadoraRacha
import pe.redactcode.nucleo.aprendizaje.EstadoDominio
import pe.redactcode.nucleo.aprendizaje.EstadoRepaso
import pe.redactcode.nucleo.aprendizaje.IntentoAprendizaje
import pe.redactcode.nucleo.aprendizaje.RegistroErrorAprendizaje
import pe.redactcode.nucleo.github.ClienteGitHub
import pe.redactcode.nucleo.github.ErrorGitHub
import pe.redactcode.nucleo.javascript.MotorRhino
import pe.redactcode.nucleo.javascript.ServicioJavaScript
import pe.redactcode.nucleo.mentor.AnalizadorEstructura
import pe.redactcode.nucleo.mentor.CatalogoConceptos
import pe.redactcode.nucleo.mentor.ConstructorPrompt
import pe.redactcode.nucleo.mentor.ContextoAprendizaje
import pe.redactcode.nucleo.mentor.CorrectorAutomatico
import pe.redactcode.nucleo.mentor.EstructuraCodigo
import pe.redactcode.nucleo.mentor.ExplicadorLineas
import pe.redactcode.nucleo.mentor.MentorLocal
import pe.redactcode.nucleo.mentor.MentorProgramacion
import pe.redactcode.nucleo.mentor.ProveedorIA
import pe.redactcode.nucleo.mentor.RespuestaMentor
import pe.redactcode.nucleo.mentor.SolicitudMentor
import pe.redactcode.nucleo.mentor.TipoSolicitud
import pe.redactcode.nucleo.modelo.CategoriaConcepto
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.NivelExperiencia
import pe.redactcode.nucleo.modelo.Severidad
import pe.redactcode.nucleo.retos.CatalogoRetos
import pe.redactcode.nucleo.retos.Dificultad
import pe.redactcode.nucleo.revision.RevisorCodigo
import pe.redactcode.nucleo.revision.TipoHallazgo
import pe.redactcode.nucleo.validacion.CatalogoExplicaciones
import pe.redactcode.nucleo.validacion.ValidadorJson
import java.time.LocalDate

class RevisionMentorAprendizajeTest {
    companion object {
        val motor = MotorRhino()
        val js = ServicioJavaScript(motor)

        @JvmStatic
        @AfterClass
        fun cerrar() = motor.cerrar()
    }

    private val revisor = RevisorCodigo(js.fuenteHallazgos, null)
    private val analizador = AnalizadorEstructura { lenguaje, codigo ->
        when (lenguaje) {
            Lenguaje.JAVASCRIPT -> js.estructura(codigo)
            Lenguaje.HTML -> ExplicadorLineas.estructuraHtml(codigo)
            Lenguaje.CSS -> ExplicadorLineas.estructuraCss(codigo)
            Lenguaje.JSON -> ExplicadorLineas.estructuraJson(codigo)
            else -> EstructuraCodigo("No disponible en pruebas JVM")
        }
    }
    private val contexto = ContextoAprendizaje(NivelExperiencia.PRINCIPIANTE, listOf(Lenguaje.JAVASCRIPT), 0, emptyList(), listOf(CategoriaConcepto.BUCLES), mapOf("JS001" to 3))

    @Test
    fun revisorJavaScriptDetectaPracticasYRiesgos() = runBlocking {
        val codigo = """
            var total = 0;
            let sinUso = 5;
            let fijo = 3;
            if (total == "0") { console.log(fijo); }
            eval("1 + 1");
            document.body.innerHTML = "<b>" + total + "</b>";
            try { total++; } catch (e) {}
        """.trimIndent()
        val informe = revisor.revisar(Lenguaje.JAVASCRIPT, codigo, emptyList())
        val reglas = informe.hallazgos.map { it.regla }.toSet()
        assertTrue(reglas.toString(), reglas.containsAll(listOf("JS_VAR", "JS_IGUALDAD_DEBIL", "SEG_EVAL", "SEG_INNERHTML", "CATCH_VACIO", "VARIABLE_NO_USADA", "PREFERIR_CONST")))
        assertTrue(informe.confirmados.isEmpty())
        assertTrue(informe.hallazgos.filter { it.regla.startsWith("SEG_") }.all { it.tipo == TipoHallazgo.SEGURIDAD })
    }

    @Test
    fun revisorCssHtmlYDuplicados() = runBlocking {
        val css = revisor.revisar(Lenguaje.CSS, "p { color: red; color: blue; }\nh1 { margin: 0; }\nh1 { padding: 0; }\nnav { }", emptyList())
        assertTrue(css.hallazgos.map { it.regla }.containsAll(listOf("CSS_PROPIEDAD_REPETIDA", "CSS_SELECTOR_REPETIDO", "CSS_REGLA_VACIA")))
        val html = revisor.revisar(Lenguaje.HTML, "<html><body><img src=\"a.png\"><a href=\"x\" target=\"_blank\">x</a><p id=\"a\"></p><p id=\"a\"></p><input id=\"c\"></body></html>", emptyList())
        assertTrue(html.hallazgos.map { it.regla }.toString(), html.hallazgos.map { it.regla }.containsAll(listOf("HTML_IMG_ALT", "HTML_BLANK_NOOPENER", "HTML_ID_DUPLICADO", "HTML_LANG", "HTML_TITLE", "HTML_LABEL")))
        val bloque = "const a = calcular(1);\nconst b = transformar(a);\nconsole.log(a, b);\nguardar(b, a);\n"
        val duplicado = revisor.revisar(Lenguaje.JAVASCRIPT, "function x() {\n$bloque}\nfunction y() {\n$bloque}\n", emptyList())
        assertTrue(duplicado.hallazgos.any { it.regla == "CODIGO_DUPLICADO" })
    }

    @Test
    fun revisorDistingueErroresConfirmados() = runBlocking {
        val diagnosticos = ValidadorJson().validarSincrono("{\"a\": 1,}").diagnosticos
        val informe = revisor.revisar(Lenguaje.JSON, "{\"a\": 1,}", diagnosticos)
        assertEquals(1, informe.confirmados.size)
        assertEquals(TipoHallazgo.CONFIRMADO, informe.confirmados.single().tipo)
    }

    @Test
    fun explicacionLineaPorLineaJavaScript() = runBlocking {
        val codigo = "function sumar(a, b) {\n  return a + b;\n}\nconst lista = [1, 2];\nfor (const n of lista) {\n  console.log(sumar(n, 1));\n}"
        val respuesta = MentorLocal(analizador).responder(SolicitudMentor(TipoSolicitud.LINEA_POR_LINEA, Lenguaje.JAVASCRIPT, codigo, contexto))
        val porLinea = respuesta.lineas.associateBy { it.linea }
        assertTrue(porLinea.getValue(1).explicacion.contains("sumar"))
        assertTrue(porLinea.getValue(2).explicacion.contains("devuelve"))
        assertTrue(porLinea.getValue(4).explicacion.contains("lista"))
        assertTrue(porLinea.getValue(5).explicacion.contains("Recorre"))
        assertTrue(porLinea.getValue(6).explicacion.contains("salida"))
        assertEquals(1, porLinea.getValue(6).nivel)
    }

    @Test
    fun explicacionAdaptadaAlNivel() {
        val principiante = ExplicadorLineas.describir(pe.redactcode.nucleo.mentor.ElementoLinea(1, 0, "funcion", "f", 1), Lenguaje.PYTHON, NivelExperiencia.PRINCIPIANTE)
        val avanzado = ExplicadorLineas.describir(pe.redactcode.nucleo.mentor.ElementoLinea(1, 0, "funcion", "f", 1), Lenguaje.PYTHON, NivelExperiencia.AVANZADO)
        assertTrue(principiante.length > avanzado.length)
        assertEquals(CatalogoConceptos.conceptos.size, CatalogoConceptos.conceptos.map { it.categoria }.toSet().size)
    }

    @Test
    fun mentorExplicaErrorConHistorial() = runBlocking {
        val d = CatalogoExplicaciones.diagnostico(Lenguaje.JAVASCRIPT, 2, 11, "JS001", "Unexpected token")
        val r = MentorLocal(analizador).responder(SolicitudMentor(TipoSolicitud.EXPLICAR_ERROR, Lenguaje.JAVASCRIPT, "a\nif (x {", contexto, diagnostico = d))
        assertTrue(r.esLocal)
        assertTrue(r.secciones.any { it.titulo == "Patrón detectado" && it.texto.contains("3 veces") })
        assertTrue(r.secciones.any { it.codigo != null })
    }

    @Test
    fun correctorAutomaticoPropuestasVerificables() {
        val py = CorrectorAutomatico.proponer(CatalogoExplicaciones.diagnostico(Lenguaje.PYTHON, 1, 12, "PY001", "expected ':'"), "if edad > 18\n    print(1)")!!
        assertEquals("if edad > 18:", py.corregida)
        val print = CorrectorAutomatico.proponer(CatalogoExplicaciones.diagnostico(Lenguaje.PYTHON, 1, 1, "PY010", "Missing parentheses"), "    print \"hola\"")!!
        assertEquals("    print(\"hola\")", print.corregida)
        val jsonCodigo = "{\n  \"a\": 1,\n}"
        val diagnostico = ValidadorJson().validarSincrono(jsonCodigo).errores.single()
        val coma = CorrectorAutomatico.proponer(diagnostico, jsonCodigo)!!
        val corregido = jsonCodigo.lines().toMutableList().also { it[coma.linea - 1] = coma.corregida }.joinToString("\n")
        assertTrue(ValidadorJson().validarSincrono(corregido).esValido)
        val css = CorrectorAutomatico.proponer(CatalogoExplicaciones.diagnostico(Lenguaje.CSS, 2, 12, "CSS004", "Falta ;"), "p {\n  color: red\n  margin: 0;\n}")!!
        assertEquals("  color: red;", css.corregida)
        val cadena = CorrectorAutomatico.proponer(CatalogoExplicaciones.diagnostico(Lenguaje.PYTHON, 1, 7, "PY007", "unterminated string"), "print(\"hola")!!
        assertEquals("print(\"hola\")", cadena.corregida)
    }

    @Test
    fun mentorRecurreALocalSiFallaExterno() = runBlocking {
        val externo = object : ProveedorIA {
            override val id = "prueba"
            override val nombre = "Proveedor de prueba"
            override val requiereRed = true
            override suspend fun disponible() = true
            override suspend fun responder(solicitud: SolicitudMentor): RespuestaMentor = throw IllegalStateException("sin red")
        }
        val mentor = MentorProgramacion(MentorLocal(analizador), externo) { true }
        val r = mentor.responder(SolicitudMentor(TipoSolicitud.EXPLICAR_CONCEPTO, Lenguaje.PYTHON, "", contexto, concepto = CategoriaConcepto.BUCLES))
        assertTrue(r.esLocal)
        assertNotNull(r.aviso)
        assertNotNull(r.retoSugerido)
        val sinConsentimiento = MentorProgramacion(MentorLocal(analizador), externo) { false }
        assertNull(sinConsentimiento.responder(SolicitudMentor(TipoSolicitud.EXPLICAR_CONCEPTO, Lenguaje.PYTHON, "", contexto)).aviso)
    }

    @Test
    fun interpretacionDeRespuestaExterna() {
        val secciones = ConstructorPrompt.interpretar("## Qué hace\nSuma dos números.\n```python\nprint(1)\n```\n## Recomendación\nUsa nombres claros.")
        assertEquals(listOf("Qué hace", "Qué hace", "Recomendación"), secciones.map { it.titulo })
        assertEquals("print(1)", secciones[1].codigo)
        assertTrue(ConstructorPrompt.sistema(contexto).contains("Principiante"))
    }

    @Test
    fun algoritmoSm2() {
        val hoy = LocalDate.of(2026, 1, 1)
        val inicial = EstadoRepaso(proximaFecha = hoy)
        val r1 = AlgoritmoSm2.calcular(inicial, 5, hoy)
        assertEquals(1, r1.intervaloDias)
        assertEquals(2.6, r1.factorFacilidad, 1e-9)
        val r2 = AlgoritmoSm2.calcular(r1, 4, hoy)
        assertEquals(6, r2.intervaloDias)
        assertEquals(2.6, r2.factorFacilidad, 1e-9)
        val r3 = AlgoritmoSm2.calcular(r2, 3, hoy)
        assertEquals(3, r3.repeticiones)
        assertEquals(Math.round(6 * 2.46).toInt(), r3.intervaloDias)
        assertEquals(hoy.plusDays(r3.intervaloDias.toLong()), r3.proximaFecha)
        val fallo = AlgoritmoSm2.calcular(r3, 1, hoy)
        assertEquals(0, fallo.repeticiones)
        assertEquals(1, fallo.intervaloDias)
        var e = inicial
        repeat(20) { e = AlgoritmoSm2.calcular(e, 0, hoy) }
        assertEquals(AlgoritmoSm2.FACILIDAD_MINIMA, e.factorFacilidad, 1e-9)
    }

    @Test
    fun rachas() {
        val hoy = LocalDate.of(2026, 3, 10)
        val dias = listOf(hoy, hoy.minusDays(1), hoy.minusDays(2), hoy.minusDays(5))
        assertEquals(3, CalculadoraRacha.rachaActual(dias, hoy))
        assertEquals(2, CalculadoraRacha.rachaActual(listOf(hoy.minusDays(1), hoy.minusDays(2)), hoy))
        assertEquals(0, CalculadoraRacha.rachaActual(listOf(hoy.minusDays(3)), hoy))
        assertEquals(3, CalculadoraRacha.mejorRacha(dias))
    }

    @Test
    fun perfilDeAprendizajeYRecomendaciones() {
        val hoy = LocalDate.of(2026, 3, 10)
        val vacio = AnalizadorAprendizaje.calcular(emptyList(), emptyList(), emptyMap(), hoy)
        assertFalse(vacio.tieneDatos)
        val errores = List(6) { RegistroErrorAprendizaje(CategoriaConcepto.BUCLES, Lenguaje.PYTHON, "PYR009", hoy) }
        val intentos = listOf(
            IntentoAprendizaje("py-cond-par", CategoriaConcepto.CONDICIONALES, Lenguaje.PYTHON, Dificultad.BASICO, true, 100, hoy),
            IntentoAprendizaje("py-cond-nota", CategoriaConcepto.CONDICIONALES, Lenguaje.PYTHON, Dificultad.INTERMEDIO, true, 100, hoy),
            IntentoAprendizaje("py-bucle-suma", CategoriaConcepto.BUCLES, Lenguaje.PYTHON, Dificultad.BASICO, false, 25, hoy)
        )
        val perfil = AnalizadorAprendizaje.calcular(intentos, errores, mapOf(Lenguaje.PYTHON to 10), hoy)
        assertTrue(perfil.tieneDatos)
        assertEquals(EstadoDominio.DOMINADO, perfil.dominios.first { it.categoria == CategoriaConcepto.CONDICIONALES }.estado)
        assertEquals(listOf(CategoriaConcepto.BUCLES), perfil.dificultades)
        assertEquals("PYR009" to 6, perfil.erroresFrecuentes.first())
        val recomendaciones = AnalizadorAprendizaje.recomendar(perfil, CatalogoRetos.retos, setOf("py-cond-par"), listOf(Lenguaje.PYTHON))
        assertEquals(CategoriaConcepto.BUCLES, recomendaciones.first().categoria)
        assertTrue(recomendaciones.first().retoId!!.startsWith("py-bucle"))
    }

    @Test
    fun clienteGitHubContraServidorSimulado() = runBlocking {
        val servidor = MockWebServer()
        servidor.start()
        try {
            val cliente = ClienteGitHub({ "token-de-prueba" }, servidor.url("/").toString())
            servidor.enqueue(MockResponse().setBody("[{\"id\":1,\"name\":\"demo\",\"full_name\":\"ana/demo\",\"private\":false,\"default_branch\":\"main\",\"owner\":{\"login\":\"ana\"}}]"))
            val repos = cliente.repositorios()
            assertEquals("ana/demo", repos.single().nombreCompleto)
            val peticionRepos = servidor.takeRequest()
            assertEquals("Bearer token-de-prueba", peticionRepos.getHeader("Authorization"))
            assertEquals("2022-11-28", peticionRepos.getHeader("X-GitHub-Api-Version"))

            servidor.enqueue(MockResponse().setBody("{\"name\":\"a.py\",\"path\":\"src/a.py\",\"sha\":\"abc\",\"type\":\"file\",\"encoding\":\"base64\",\"content\":\"cHJpbnQoImhvbGEiKQ==\\n\"}"))
            val (contenido, sha) = cliente.descargar("ana", "demo", "src/a.py", "main")
            assertEquals("print(\"hola\")", contenido)
            assertEquals("abc", sha)
            assertEquals("/repos/ana/demo/contents/src/a.py?ref=main", servidor.takeRequest().path)

            servidor.enqueue(MockResponse().setBody("{\"content\":{\"name\":\"a.py\",\"path\":\"src/a.py\",\"sha\":\"def\",\"type\":\"file\"},\"commit\":{\"sha\":\"123\",\"html_url\":\"https://github.com/ana/demo/commit/123\"}}"))
            val publicado = cliente.publicar("ana", "demo", "src/a.py", "main", "print(1)", "Actualiza a.py", "abc")
            assertEquals("123", publicado.commit.sha)
            val peticionPut = servidor.takeRequest()
            assertEquals("PUT", peticionPut.method)
            val cuerpo = peticionPut.body.readUtf8()
            assertTrue(cuerpo, cuerpo.contains("\"sha\":\"abc\"") && cuerpo.contains("\"branch\":\"main\"") && cuerpo.contains("cHJpbnQoMSk="))

            servidor.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"Bad credentials\"}"))
            try {
                cliente.usuario()
                fail("Se esperaba un error")
            } catch (e: ErrorGitHub) {
                assertEquals(401, e.codigoHttp)
            }
        } finally {
            servidor.shutdown()
        }
    }

    @Test
    fun severidadesDelCatalogo() {
        assertEquals(Severidad.ADVERTENCIA, CatalogoExplicaciones.diagnostico(Lenguaje.CSS, 1, 1, "CSS007", "x", Severidad.ADVERTENCIA).severidad)
        assertEquals("GEN001", CatalogoExplicaciones.diagnostico(Lenguaje.CSS, 1, 1, "NO_EXISTE", "x").codigo)
    }
}
