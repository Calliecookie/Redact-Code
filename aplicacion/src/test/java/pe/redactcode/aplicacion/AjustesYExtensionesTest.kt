package pe.redactcode.aplicacion

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pe.redactcode.aplicacion.datos.RepositorioExtensiones
import pe.redactcode.aplicacion.funcionalidades.editor.ControladorEditores
import pe.redactcode.aplicacion.nucleo.archivos.GestorArchivos
import pe.redactcode.aplicacion.nucleo.diseno.ColoresEditorOscuro
import pe.redactcode.aplicacion.nucleo.diseno.ModoTema
import pe.redactcode.aplicacion.nucleo.preferencias.FuenteEditor
import pe.redactcode.aplicacion.nucleo.preferencias.GuardadoAutomatico
import pe.redactcode.aplicacion.nucleo.preferencias.OrdenArchivos
import pe.redactcode.aplicacion.nucleo.preferencias.PreferenciasUsuario
import pe.redactcode.aplicacion.nucleo.preferencias.ProveedorAgenteTipo
import pe.redactcode.aplicacion.nucleo.preferencias.RepositorioPreferencias
import pe.redactcode.nucleo.extensiones.FragmentosBase
import pe.redactcode.nucleo.extensiones.TemaExtension
import pe.redactcode.nucleo.modelo.Lenguaje
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AjustesYExtensionesTest {
    private lateinit var contexto: Context
    private lateinit var preferencias: RepositorioPreferencias

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        preferencias = RepositorioPreferencias(contexto)
        runBlocking { preferencias.restablecer() }
    }

    @Test
    fun valoresPredeterminadosCumplenLaEspecificacion() = runBlocking {
        val p = preferencias.actuales()
        assertEquals(ModoTema.OSCURO, p.modoTema)
        assertEquals(FuenteEditor.JETBRAINS_MONO, p.fuenteEditor)
        assertFalse(p.agenteEliminar)
        assertFalse(p.consentimientoIA)
        assertTrue(p.agenteLeerProyecto)
    }

    @Test
    fun todasLasPreferenciasSePersistenYSeLimitan() = runBlocking {
        preferencias.modoTema(ModoTema.CLARO)
        preferencias.escalaTexto(9f)
        preferencias.tamanoFuenteEditor(2)
        preferencias.fuenteEditor(FuenteEditor.MONOESPACIADA_SISTEMA)
        preferencias.interlineadoEditor(1.5f)
        preferencias.ligaduras(true)
        preferencias.autocierre(false)
        preferencias.sangriaEspacios(8)
        preferencias.ajusteLinea(true)
        preferencias.validacionEnVivo(false)
        preferencias.retrasoValidacion(5000)
        preferencias.numerosLinea(false)
        preferencias.resaltarLineaActual(false)
        preferencias.mostrarInvisibles(true)
        preferencias.autocompletado(false)
        preferencias.barraSimbolos(false)
        preferencias.formatearAlGuardar(true)
        preferencias.stickyScroll(false)
        preferencias.animaciones(false)
        preferencias.altoContraste(true)
        preferencias.objetivosGrandes(true)
        preferencias.mostrarMigas(false)
        preferencias.mostrarBarraEstado(false)
        preferencias.bloquearAlSalir(true)
        preferencias.urlCompatible("  https://api.ejemplo.pe/v1 ")
        preferencias.modeloCompatible("modelo-x")
        preferencias.proveedorAgente(ProveedorAgenteTipo.COMPATIBLE_OPENAI)
        preferencias.agenteEliminar(true)
        preferencias.agenteMaxArchivos(500)
        preferencias.ordenArchivos(OrdenArchivos.TIPO)
        preferencias.confirmarEliminar(false)
        preferencias.excluirBusqueda("*.json")
        preferencias.guardadoAutomatico(GuardadoAutomatico.CON_RETRASO)
        preferencias.limiteEjecucion(1)
        preferencias.tamanoFuenteTerminal(40)
        preferencias.limiteTerminal(1000)
        preferencias.gitAutor("Ana Pérez")
        preferencias.gitCorreo("ana@ejemplo.pe")
        preferencias.ramaPredeterminada("principal")
        preferencias.abrirUltimoProyecto(true)
        preferencias.ultimoProyecto(7)
        val p = preferencias.actuales()
        assertEquals(ModoTema.CLARO, p.modoTema)
        assertEquals(1.5f, p.escalaTexto, 0f)
        assertEquals(10, p.tamanoFuenteEditor)
        assertEquals(FuenteEditor.MONOESPACIADA_SISTEMA, p.fuenteEditor)
        assertEquals(1.5f, p.interlineadoEditor, 0f)
        assertTrue(p.ligaduras)
        assertFalse(p.autocierre)
        assertEquals(8, p.sangriaEspacios)
        assertTrue(p.ajusteLinea)
        assertFalse(p.validacionEnVivo)
        assertEquals(2000, p.retrasoValidacionMs)
        assertFalse(p.numerosLinea)
        assertFalse(p.resaltarLineaActual)
        assertTrue(p.mostrarInvisibles)
        assertFalse(p.autocompletado)
        assertFalse(p.barraSimbolos)
        assertTrue(p.formatearAlGuardar)
        assertFalse(p.stickyScroll)
        assertFalse(p.animaciones)
        assertTrue(p.altoContraste)
        assertTrue(p.objetivosGrandes)
        assertFalse(p.mostrarMigas)
        assertFalse(p.mostrarBarraEstado)
        assertTrue(p.bloquearAlSalir)
        assertEquals("https://api.ejemplo.pe/v1", p.urlCompatible)
        assertEquals("modelo-x", p.modeloCompatible)
        assertEquals(ProveedorAgenteTipo.COMPATIBLE_OPENAI, p.proveedorAgente)
        assertTrue(p.agenteEliminar)
        assertEquals(100, p.agenteMaxArchivos)
        assertEquals(OrdenArchivos.TIPO, p.ordenArchivos)
        assertFalse(p.confirmarEliminar)
        assertEquals("*.json", p.excluirBusqueda)
        assertEquals(GuardadoAutomatico.CON_RETRASO, p.guardadoAutomatico)
        assertEquals(3, p.limiteEjecucionSegundos)
        assertEquals(22, p.tamanoFuenteTerminal)
        assertEquals(300, p.limiteTerminalSegundos)
        assertEquals("Ana Pérez", p.gitAutor)
        assertEquals("ana@ejemplo.pe", p.gitCorreo)
        assertEquals("principal", p.ramaPredeterminada)
        assertTrue(p.abrirUltimoProyecto)
        assertEquals(7L, p.ultimoProyectoId)
        val nueva = RepositorioPreferencias(contexto).actuales()
        assertEquals(p, nueva)
    }

    @Test
    fun conjuntosDeLenguajesYExtensionesSeAlternan() = runBlocking {
        preferencias.validacionLenguaje("PYTHON", false)
        preferencias.validacionLenguaje("CSS", false)
        preferencias.validacionLenguaje("PYTHON", true)
        assertEquals(setOf("CSS"), preferencias.actuales().lenguajesSinValidacion)
        preferencias.extensionActiva("a.b", false)
        assertEquals(setOf("a.b"), preferencias.actuales().extensionesDesactivadas)
        preferencias.extensionActiva("a.b", true)
        assertTrue(preferencias.actuales().extensionesDesactivadas.isEmpty())
    }

    @Test
    fun restablecerAjustesConservaBienvenidaYBloqueo() = runBlocking {
        preferencias.bienvenidaCompletada(true)
        preferencias.bloqueoPin(true)
        preferencias.bloqueoBiometrico(true)
        preferencias.modoTema(ModoTema.CLARO)
        preferencias.tamanoFuenteEditor(20)
        preferencias.restablecerAjustes()
        val p = preferencias.actuales()
        assertTrue(p.bienvenidaCompletada)
        assertTrue(p.bloqueoPin)
        assertTrue(p.bloqueoBiometrico)
        assertEquals(ModoTema.OSCURO, p.modoTema)
        assertEquals(14, p.tamanoFuenteEditor)
    }

    private suspend fun esperar(condicion: () -> Boolean) {
        kotlinx.coroutines.withTimeout(5_000) { while (!condicion()) kotlinx.coroutines.delay(25) }
    }

    private fun vsix(): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { z ->
            fun f(n: String, c: String) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
            f("extension/package.json", """{"name":"tema-prueba","publisher":"ana","version":"1.0.0","contributes":{"themes":[{"label":"Noche","uiTheme":"vs-dark","path":"./t.json"}],"snippets":[{"language":"python","path":"./s.json"}]}}""")
            f("extension/t.json", """{"name":"Noche","type":"dark","colors":{"editor.background":"#101820"},"tokenColors":[{"scope":"keyword","settings":{"foreground":"#ff0000"}}]}""")
            f("extension/s.json", """{"Imprimir":{"prefix":"imp","body":["print(${'$'}{1:x})"]}}""")
        }
        return salida.toByteArray()
    }

    @Test
    fun repositorioDeExtensionesInstalaActivaYDesinstala() = runBlocking {
        val repo = RepositorioExtensiones(contexto, preferencias)
        val m = repo.instalarBytes(vsix())
        assertEquals("ana.tema-prueba", m.id)
        esperar { repo.extensiones.value.any { it.manifiesto.id == "ana.tema-prueba" } }
        val lista = repo.extensiones.value.map { it.manifiesto.id }
        assertTrue(FragmentosBase.ID in lista)
        assertTrue("ana.tema-prueba" in lista)
        assertTrue(repo.fragmentosActivos().any { it.prefijo == "imp" })
        assertEquals(1, repo.temas().size)
        preferencias.extensionActiva("ana.tema-prueba", false)
        esperar { repo.fragmentosActivos().none { it.prefijo == "imp" } }
        assertFalse(repo.fragmentosActivos().any { it.prefijo == "imp" })
        preferencias.temaEditor("ana.tema-prueba:0")
        repo.desinstalar("ana.tema-prueba")
        assertEquals(PreferenciasUsuario.TEMA_EDITOR_PREDETERMINADO, preferencias.actuales().temaEditor)
        assertFalse(repo.manifiestos.value.any { it.id == "ana.tema-prueba" })
        try { repo.desinstalar(FragmentosBase.ID); throw AssertionError() } catch (_: pe.redactcode.nucleo.extensiones.ErrorExtension) { }
    }

    @Test
    fun temaDeExtensionSobrescribeSoloLoDeclarado() {
        val tema = TemaExtension("Prueba", true, mapOf("fondo" to "#101820", "palabraClave" to "#ff0000"))
        val c = RepositorioExtensiones.aplicarTema(ColoresEditorOscuro, tema)
        assertEquals(Color(0xFF101820), c.fondo)
        assertEquals(Color(0xFFFF0000), c.palabraClave)
        assertEquals(ColoresEditorOscuro.cadena, c.cadena)
        assertEquals(ColoresEditorOscuro, RepositorioExtensiones.aplicarTema(ColoresEditorOscuro, null))
    }

    @Test
    fun gestorArchivosAceptaTextoComunYRechazaBinarios() {
        assertEquals(null, GestorArchivos.validarNombre("README.md", true))
        assertEquals(null, GestorArchivos.validarNombre("config.yml", true))
        assertEquals(null, GestorArchivos.validarNombre("App.kt", true))
        assertNotNull(GestorArchivos.validarNombre("programa.exe", true))
        assertNotNull(GestorArchivos.validarNombre("foto.png", true))
        assertEquals(Lenguaje.JAVASCRIPT, Lenguaje.desdeNombreArchivo("modulo.mjs"))
    }

    @Test
    fun controladorDelEditorNoFallaSinEditoresActivos() {
        val c = ControladorEditores(contexto)
        c.duplicarLinea()
        c.moverLinea(true)
        c.alternarComentario(Lenguaje.PYTHON)
        assertEquals(null, c.cursorActivo())
        assertTrue(c.claves().isEmpty())
    }
}
