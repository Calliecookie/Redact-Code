package pe.redactcode.nucleo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.redactcode.nucleo.ide.ResultadoComando
import pe.redactcode.nucleo.terminal.PuenteComandos
import pe.redactcode.nucleo.terminal.ServidorPuente
import java.io.File
import java.util.concurrent.TimeUnit

class PuenteTest {
    @get:Rule val carpeta = TemporaryFolder()
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var proyecto: File
    private lateinit var puente: File
    private lateinit var rc: File

    @Before
    fun preparar() {
        assumeTrue(File("/bin/sh").canExecute())
        proyecto = carpeta.newFolder("proyecto")
        puente = carpeta.newFolder("puente")
        rc = File(carpeta.root, "rc.sh").also { it.writeText(PuenteComandos.generarScript()) }
    }

    @After
    fun limpiar() { alcance.cancel() }

    private fun correr(directorio: File, orden: String, limiteSegundos: Long = 20): Pair<String, Int> {
        val p = ProcessBuilder("/bin/sh", "-c", ". ${rc.absolutePath}; $orden")
            .directory(directorio).redirectErrorStream(true)
        p.environment()["REDACT_PUENTE"] = puente.absolutePath
        val proceso = p.start()
        val texto = proceso.inputStream.bufferedReader().readText()
        assertTrue("tiempo agotado", proceso.waitFor(limiteSegundos, TimeUnit.SECONDS))
        return texto to proceso.exitValue()
    }

    @Test
    fun ejecutaComandoDevuelveSalidaYCodigo() {
        val vistos = mutableListOf<List<String>>()
        ServidorPuente(puente, proyecto, alcance) { comando, args, dir ->
            vistos += listOf(comando) + args
            ResultadoComando("hola desde $comando en ${dir.name}\n", "", 3)
        }.iniciar()
        val (texto, codigo) = correr(proyecto, "python a.py 'dos palabras'; echo \"codigo=\$?\"")
        assertTrue(texto, texto.contains("hola desde python en proyecto"))
        assertTrue(texto, texto.contains("codigo=3"))
        assertEquals(listOf("python", "a.py", "dos palabras"), vistos.single())
        assertTrue(puente.listFiles().orEmpty().none { it.name.endsWith(".salida") || it.name.endsWith(".fin") })
    }

    @Test
    fun rechazaDirectorioFueraDelProyecto() {
        var llamado = false
        ServidorPuente(puente, proyecto, alcance) { _, _, _ -> llamado = true; ResultadoComando("") }.iniciar()
        val (texto, codigo) = correr(carpeta.root, "git status; echo \"codigo=\$?\"")
        assertTrue(texto, texto.contains("fuera del proyecto"))
        assertTrue(texto, texto.contains("codigo=126"))
        assertFalse(llamado)
    }

    @Test
    fun rechazaComandoNoAdmitido() {
        ServidorPuente(puente, proyecto, alcance) { _, _, _ -> ResultadoComando("") }.iniciar()
        val (texto, _) = correr(proyecto, ". ${rc.absolutePath}; redact_pedir rm x; echo \"codigo=\$?\"")
        assertTrue(texto, texto.contains("codigo=127"))
    }

    @Test
    fun alIniciarDescartaSolicitudesAntiguasYAlDetenerLimpia() {
        File(puente, "999-1.solicitud").writeText("pip\n${proyecto.absolutePath}\ninstall\nalgo\n")
        File(puente, "999-1.salida").writeText("viejo")
        var llamadas = 0
        val servidor = ServidorPuente(puente, proyecto, alcance) { _, _, _ -> llamadas++; ResultadoComando("ok\n") }
        servidor.iniciar()
        Thread.sleep(500)
        assertEquals(0, llamadas)
        assertTrue(puente.listFiles().orEmpty().isEmpty())
        val (texto, _) = correr(proyecto, "redact; echo \"codigo=\$?\"")
        assertTrue(texto, texto.contains("codigo=0"))
        assertEquals(1, llamadas)
        servidor.detener()
        assertTrue(puente.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun sinPuenteMuestraAviso() {
        val p = ProcessBuilder("/bin/sh", "-c", ". ${rc.absolutePath}; python x; echo \"codigo=\$?\"").directory(proyecto).redirectErrorStream(true).start()
        val texto = p.inputStream.bufferedReader().readText()
        p.waitFor(10, TimeUnit.SECONDS)
        assertTrue(texto, texto.contains("no está disponible"))
        assertTrue(texto, texto.contains("codigo=127"))
    }

    @Test
    fun cancelarCreaMarcaYDetieneLaEjecucion() {
        ServidorPuente(puente, proyecto, alcance) { _, _, _ -> delay(60_000); ResultadoComando("") }.iniciar()
        val p = ProcessBuilder("/bin/sh", "-c", ". ${rc.absolutePath}; python largo; echo \"codigo=\$?\"")
            .directory(proyecto).redirectErrorStream(true)
        p.environment()["REDACT_PUENTE"] = puente.absolutePath
        val proceso = p.start()
        Thread.sleep(800)
        val id = (puente.listFiles().orEmpty().map { it.name.substringBefore('.') }.firstOrNull() ?: "")
        assertTrue(id.isNotEmpty())
        File(puente, "$id.cancelar").writeText("")
        val texto = proceso.inputStream.bufferedReader().readText()
        assertTrue(proceso.waitFor(15, TimeUnit.SECONDS))
        assertTrue(texto, texto.contains("codigo=130"))
    }

    @Test
    fun noEmiteAvisoFalsoCuandoSalidaAunNoExiste() {
        ServidorPuente(puente, proyecto, alcance) { comando, _, _ ->
            ResultadoComando("salida-$comando\n")
        }.iniciar()
        val (texto, codigo) = correr(proyecto, "python a.py")
        assertEquals(0, codigo)
        assertTrue(texto, texto.contains("salida-python"))
        assertFalse(texto, texto.contains("No such file", ignoreCase = true))
        assertFalse(texto, texto.contains("cannot open", ignoreCase = true))
        assertFalse(texto, texto.contains("can't open", ignoreCase = true))
    }
}
