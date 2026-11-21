package pe.redactcode.nucleo

import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.redactcode.nucleo.javascript.MotorRhino
import pe.redactcode.nucleo.javascript.ServicioJavaScript
import pe.redactcode.nucleo.modelo.EstadoEjecucion
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.proyectos.CatalogoProyectosGuiados
import pe.redactcode.nucleo.proyectos.TipoVerificacion
import pe.redactcode.nucleo.proyectos.VerificadorProyecto
import pe.redactcode.nucleo.retos.CatalogoRetos
import pe.redactcode.nucleo.retos.FormatoRetos

class RetosYProyectosTest {
    companion object {
        val motor = MotorRhino()
        val js = ServicioJavaScript(motor)

        @JvmStatic
        @AfterClass
        fun cerrar() = motor.cerrar()

        val solucionesJs = mapOf(
            "js-fund-doble" to "function doble(n) { return n * 2; }",
            "js-var-celsius" to "function celsiusAFahrenheit(c) { const f = c * 9 / 5 + 32; return f; }",
            "js-cond-mayor" to "function mayorDeTres(a, b, c) { if (a >= b && a >= c) return a; if (b >= c) return b; return c; }",
            "js-bucle-tabla" to "function tablaMultiplicar(n) { const t = []; for (let i = 1; i <= 10; i++) t.push(n * i); return t; }",
            "js-cad-palabras" to "function contarPalabras(texto) { const l = texto.trim(); return l ? l.split(/\\s+/).length : 0; }",
            "js-lista-promedio" to "function promedio(numeros) { if (numeros.length === 0) return 0; let s = 0; numeros.forEach(n => { s += n; }); return s / numeros.length; }",
            "js-obj-nombre" to "function nombreCompleto(persona) { return `${'$'}{persona.nombre} ${'$'}{persona.apellido}`; }",
            "js-cad-invertir" to "function invertir(texto) { return texto.split('').reverse().join(''); }",
            "js-ed-unicos" to "function eliminarDuplicados(lista) { return [...new Set(lista)]; }",
            "js-alg-fizzbuzz" to "function fizzBuzz(n) { const r = []; for (let i = 1; i <= n; i++) { r.push(i % 15 === 0 ? 'FizzBuzz' : i % 3 === 0 ? 'Fizz' : i % 5 === 0 ? 'Buzz' : String(i)); } return r; }",
            "js-obj-agrupar" to "function agruparPorCategoria(productos) { const g = {}; productos.forEach(p => { if (!g[p.categoria]) g[p.categoria] = []; g[p.categoria].push(p.nombre); }); return g; }",
            "js-func-flecha" to "const cuadrados = (numeros) => numeros.map(n => n * n);"
        )
    }

    @Test
    fun catalogoDeRetosValido() {
        val retos = CatalogoRetos.retos
        assertTrue(retos.size >= 25)
        assertEquals(retos.size, retos.map { it.id }.toSet().size)
        retos.forEach { assertTrue(it.id, FormatoRetos.validar(it).isEmpty()) }
        assertTrue(retos.any { it.lenguaje == Lenguaje.PYTHON } && retos.any { it.lenguaje == Lenguaje.JAVASCRIPT })
    }

    @Test
    fun todosLosRetosJavaScriptTienenSolucionQueAprueba() = runBlocking {
        val retosJs = CatalogoRetos.retos.filter { it.lenguaje == Lenguaje.JAVASCRIPT }
        assertEquals(retosJs.map { it.id }.toSet(), solucionesJs.keys)
        retosJs.forEach { reto ->
            val r = js.ejecutar(solucionesJs.getValue(reto.id), reto.funcion, reto.pruebas)
            assertTrue("${reto.id}: ${r.errorGeneral} ${r.casos.filter { !it.aprobado }}", r.todosAprobados)
        }
    }

    @Test
    fun codigoInicialNoApruebaLosRetos() = runBlocking {
        CatalogoRetos.retos.filter { it.lenguaje == Lenguaje.JAVASCRIPT }.forEach { reto ->
            val r = js.ejecutar(reto.codigoInicial, reto.funcion, reto.pruebas)
            assertFalse(reto.id, r.todosAprobados)
        }
    }

    @Test
    fun evaluacionDetectaErroresYBuclesInfinitos() = runBlocking {
        val reto = CatalogoRetos.buscar("js-fund-doble")!!
        val incorrecto = js.ejecutar("function doble(n) { return n * 2 + (n < 0 ? 1 : 0); }", reto.funcion, reto.pruebas)
        assertFalse(incorrecto.todosAprobados)
        assertTrue(incorrecto.casos.any { it.aprobado } && incorrecto.casos.any { !it.aprobado })
        val excepcion = js.ejecutar("function doble(n) { return n.x.y; }", reto.funcion, reto.pruebas)
        assertTrue(excepcion.casos.toString(), excepcion.casos.all { it.error?.startsWith("TypeError") == true })
        val infinito = js.ejecutar("function doble(n) { while (true) {} }", reto.funcion, reto.pruebas, 1_000)
        assertTrue(infinito.tiempoAgotado)
        val sinFuncion = js.ejecutar("const x = 1;", reto.funcion, reto.pruebas)
        assertTrue(sinFuncion.errorGeneral!!.contains("doble"))
    }

    @Test
    fun ejecucionDeProgramaConConsolaYErrores() = runBlocking {
        val ok = js.ejecutar("const a = [1, 2];\nconsole.log('suma', a[0] + a[1]);\nconsole.log({ x: 1 });")
        assertEquals(EstadoEjecucion.EXITO, ok.estado)
        assertEquals("suma 3\n{\"x\":1}", ok.salida)
        val error = js.ejecutar("console.log('antes');\nconst u = undefined;\nu.campo;")
        assertEquals(EstadoEjecucion.ERROR, error.estado)
        assertEquals("antes", error.salida)
        assertEquals("TypeError", error.excepcion)
        assertEquals(3, error.lineaExcepcion)
        val tiempo = js.ejecutar("for(;;){}", 800)
        assertEquals(EstadoEjecucion.TIEMPO_AGOTADO, tiempo.estado)
    }

    @Test
    fun importacionDeRetosValidaFormato() {
        val exportado = FormatoRetos.exportar(CatalogoRetos.retos.take(2), "prueba")
        assertEquals(2, FormatoRetos.importar(exportado).retos.size)
        val invalido = FormatoRetos.importar("{\"formato\":\"otro\",\"version\":9,\"retos\":[]}")
        assertEquals(2, invalido.errores.size)
        assertTrue(FormatoRetos.importar("no es json").errores.isNotEmpty())
    }

    @Test
    fun proyectosGuiadosCompletos() {
        val proyectos = CatalogoProyectosGuiados.proyectos
        assertEquals(8, proyectos.size)
        proyectos.forEach { p ->
            assertTrue(p.id, p.pasos.isNotEmpty() && p.archivosIniciales.isNotEmpty())
            p.pasos.flatMap { it.verificaciones }.forEach { v -> assertTrue("${p.id}:${v.archivo}", v.archivo in p.archivosIniciales.keys) }
        }
    }

    @Test
    fun proyectoListaDeTareasSeVerificaConSolucion() = runBlocking {
        val proyecto = CatalogoProyectosGuiados.buscar("lista-tareas")!!
        val archivos = proyecto.archivosIniciales.toMutableMap()
        val verificador = VerificadorProyecto(mapOf(Lenguaje.JAVASCRIPT to js)) { archivos[it] }
        assertFalse(verificador.verificar(proyecto.pasos[0]).aprobado)
        archivos["tareas.js"] = """
            function agregarTarea(lista, texto) {
              const limpio = texto.trim();
              if (!limpio) return lista;
              return [...lista, { texto: limpio, hecha: false }];
            }
            function completarTarea(lista, indice) {
              return lista.map((t, i) => i === indice ? { ...t, hecha: true } : t);
            }
            function pendientes(lista) {
              return lista.filter(t => !t.hecha).length;
            }
        """.trimIndent()
        proyecto.pasos.forEach { paso -> assertTrue(paso.titulo, verificador.verificar(paso).aprobado) }
    }

    @Test
    fun proyectosJavaScriptConSoluciones() = runBlocking {
        val soluciones = mapOf(
            "aplicacion-notas" to ("notas.js" to """
                function crearNota(titulo, contenido) { const l = titulo.trim(); return { titulo: l || "Sin título", contenido }; }
                function buscarNotas(notas, texto) { const t = texto.toLowerCase(); return notas.filter(n => n.titulo.toLowerCase().includes(t) || n.contenido.toLowerCase().includes(t)); }
                function contarPalabras(nota) { const l = nota.contenido.trim(); return l ? l.split(/\s+/).length : 0; }
            """.trimIndent()),
            "formulario-interactivo" to ("validacion.js" to """
                function correoValido(texto) {
                  if (texto.includes(" ")) return false;
                  const partes = texto.split("@");
                  if (partes.length !== 2 || !partes[0]) return false;
                  const dominio = partes[1];
                  const punto = dominio.indexOf(".");
                  return punto > 0 && punto < dominio.length - 1;
                }
                function telefonoValido(texto) { return texto.length === 9 && texto[0] === "9" && [...texto].every(c => c >= "0" && c <= "9"); }
                function validarFormulario(datos) {
                  const errores = [];
                  if (!datos.nombre.trim()) errores.push("El nombre es obligatorio");
                  if (!correoValido(datos.correo)) errores.push("El correo no es válido");
                  if (!telefonoValido(datos.telefono)) errores.push("El teléfono no es válido");
                  return errores;
                }
            """.trimIndent())
        )
        soluciones.forEach { (id, archivo) ->
            val proyecto = CatalogoProyectosGuiados.buscar(id)!!
            val archivos = proyecto.archivosIniciales.toMutableMap()
            archivos[archivo.first] = archivo.second
            val verificador = VerificadorProyecto(mapOf(Lenguaje.JAVASCRIPT to js)) { archivos[it] }
            proyecto.pasos.flatMap { it.verificaciones }.filter { it.tipo == TipoVerificacion.FUNCION }.forEach { v ->
                val r = verificador.verificar(v)
                assertTrue("$id ${v.funcion}: ${r.detalle}", r.aprobada)
            }
        }
    }

    @Test
    fun proyectosHtmlYCssSeVerificanConSolucion() = runBlocking {
        val proyecto = CatalogoProyectosGuiados.buscar("pagina-personal")!!
        val archivos = proyecto.archivosIniciales.toMutableMap()
        val verificador = VerificadorProyecto(emptyMap()) { archivos[it] }
        assertFalse(verificador.verificar(proyecto.pasos[0]).aprobado)
        archivos["index.html"] = """
            <!DOCTYPE html>
            <html lang="es">
            <head><title>Ana</title><link rel="stylesheet" href="estilos.css"></head>
            <body>
              <header><h1>Ana Torres</h1></header>
              <main>
                <section id="sobre-mi"><h2>Sobre mí</h2><p>Estudiante.</p></section>
                <ul><li>Python</li><li>HTML</li><li>CSS</li></ul>
              </main>
              <footer><a href="mailto:ana@correo.pe">Escríbeme</a></footer>
            </body>
            </html>
        """.trimIndent()
        archivos["estilos.css"] = "body, html {\n  font-family: system-ui, sans-serif;\n}\n\nheader {\n  background-color: #151518;\n}\n"
        proyecto.pasos.forEach { paso ->
            val r = verificador.verificar(paso)
            assertTrue("${paso.titulo}: ${r.resultados.map { it.detalle }}", r.aprobado)
        }
        val formulario = CatalogoProyectosGuiados.buscar("formulario-interactivo")!!
        val archivosForm = formulario.archivosIniciales.toMutableMap()
        archivosForm["index.html"] = "<form><label for=\"correo\">Correo</label><input type=\"email\" id=\"correo\"><button type=\"submit\">Enviar</button></form>"
        val verificadorForm = VerificadorProyecto(emptyMap()) { archivosForm[it] }
        assertTrue(verificadorForm.verificar(formulario.pasos[0]).aprobado)
    }
}
