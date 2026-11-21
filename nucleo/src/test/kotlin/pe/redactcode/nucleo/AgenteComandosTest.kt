package pe.redactcode.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.redactcode.nucleo.agentes.AgenteCodigo
import pe.redactcode.nucleo.agentes.PermisosAgente
import pe.redactcode.nucleo.terminal.NivelRiesgo
import pe.redactcode.nucleo.terminal.RegistroEjecuciones

class AgenteComandosTest {
    @get:Rule val carpeta = TemporaryFolder()

    private fun respuesta(vararg comandos: String) =
        "{\"resumen\":\"ok\",\"cambios\":[],\"comandos\":[" + comandos.joinToString(",") { "{\"comando\":\"${it.replace("\"", "\\\"")}\",\"motivo\":\"m\"}" } + "]}"

    @Test
    fun sinPermisoNoSeAceptaNingunComando() {
        val p = AgenteCodigo.interpretar(respuesta("ls"), emptyMap(), PermisosAgente()) { null }
        assertTrue(p.comandos.isEmpty())
        assertTrue(p.rechazados.single().contains("sin permiso"))
    }

    @Test
    fun conPermisoClasificaYBloquea() {
        val permisos = PermisosAgente(ejecutarComandos = true)
        val p = AgenteCodigo.interpretar(respuesta("ls", "pip install requests", "rm x.txt", "rm -rf /", "sudo ls"), emptyMap(), permisos) { null }
        assertEquals(listOf("ls", "pip install requests", "rm x.txt"), p.comandos.map { it.comando })
        assertEquals(listOf(NivelRiesgo.BAJO, NivelRiesgo.MEDIO, NivelRiesgo.ALTO), p.comandos.map { it.riesgo.nivel })
        assertEquals(2, p.rechazados.count { it.contains("bloqueado") })
    }

    @Test
    fun limitaCantidadYLargo() {
        val permisos = PermisosAgente(ejecutarComandos = true)
        val seis = Array(6) { "echo $it" }
        val p = AgenteCodigo.interpretar(respuesta(*seis), emptyMap(), permisos) { null }
        assertEquals(AgenteCodigo.MAXIMO_COMANDOS, p.comandos.size)
        val largo = AgenteCodigo.interpretar(respuesta("echo " + "a".repeat(600)), emptyMap(), permisos) { null }
        assertTrue(largo.comandos.isEmpty())
    }

    @Test
    fun elSistemaDescribeElContratoSoloConPermiso() {
        assertTrue(AgenteCodigo.sistema(PermisosAgente(ejecutarComandos = true)).contains("\"comandos\""))
        assertFalse(AgenteCodigo.sistema(PermisosAgente()).contains("\"comandos\""))
    }

    @Test
    fun registroCensuraYRecorta() {
        val r = RegistroEjecuciones(carpeta.newFile("registro.jsonl"), maximoBytes = 2000, maximoSalida = 50)
        val e = r.registrar(1, "agente", "curl -H 'Authorization: Bearer abcdefghijklmnop1234' x", NivelRiesgo.ALTO, 0, "salida " + "z".repeat(200))
        assertFalse(e.comando.contains("abcdefghijklmnop1234"))
        assertEquals(50, e.salida.length)
        repeat(40) { r.registrar(it.toLong(), "agente", "echo $it", NivelRiesgo.BAJO, 0, "x".repeat(40)) }
        val leidas = r.leer(100)
        assertTrue(leidas.size < 41)
        assertEquals("echo 39", leidas.first().comando)
        r.borrar()
        assertTrue(r.leer().isEmpty())
    }
}
