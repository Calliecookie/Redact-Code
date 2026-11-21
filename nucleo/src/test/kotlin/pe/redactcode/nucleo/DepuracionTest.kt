package pe.redactcode.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.redactcode.nucleo.depuracion.FaseSesion
import pe.redactcode.nucleo.depuracion.PuntosInterrupcion
import pe.redactcode.nucleo.depuracion.ReductorSesion

class DepuracionTest {
    private fun aplicar(vararg eventos: String) =
        eventos.fold(ReductorSesion.iniciar(true)) { e, t -> ReductorSesion.aplicar(e, ReductorSesion.interpretar(t)!!) }

    @Test
    fun flujoCompletoDePausaYFin() {
        val e = aplicar(
            """{"tipo":"salida","texto":"hola ","error":false}""",
            """{"tipo":"salida","texto":"mundo\n","error":false}""",
            """{"tipo":"salida","texto":"fallo\n","error":true}""",
            """{"tipo":"pausa","linea":4,"pila":[{"funcion":"f","linea":4},{"funcion":"<module>","linea":9}],"variables":[{"nombre":"x","valor":"3","tipo":"int"}]}"""
        )
        assertEquals(FaseSesion.PAUSADA, e.fase)
        assertEquals(4, e.lineaActual)
        assertEquals(2, e.pila.size)
        assertEquals("x", e.variables.single().nombre)
        assertEquals(listOf("hola mundo\n", "fallo\n"), e.salida.map { it.texto })
        assertEquals(listOf(false, true), e.salida.map { it.error })
        val reanudada = ReductorSesion.alReanudar(e)
        assertEquals(FaseSesion.EJECUTANDO, reanudada.fase)
        val fin = ReductorSesion.aplicar(reanudada, ReductorSesion.interpretar("""{"tipo":"fin","estado":"EXITO","mensaje":null,"linea":null,"duracion_ms":12}""")!!)
        assertEquals(FaseSesion.TERMINADA, fin.fase)
        assertEquals("EXITO", fin.estadoFinal)
        assertTrue(fin.variables.isEmpty())
    }

    @Test
    fun entradaYEvaluacion() {
        var e = aplicar("""{"tipo":"entrada"}""")
        assertEquals(FaseSesion.ESPERANDO_ENTRADA, e.fase)
        e = ReductorSesion.alEnviarEntrada(e)
        assertEquals(FaseSesion.EJECUTANDO, e.fase)
        e = aplicar("""{"tipo":"pausa","linea":1,"pila":[],"variables":[]}""")
        e = ReductorSesion.alEvaluar(e, "x + 1")
        e = ReductorSesion.aplicar(e, ReductorSesion.interpretar("""{"tipo":"evaluacion","texto":"4","error":false}""")!!)
        assertEquals("x + 1", e.evaluaciones.single().expresion)
        assertEquals("4", e.evaluaciones.single().resultado)
        assertNull(e.expresionPendiente)
    }

    @Test
    fun interpreteIgnoraBasuraYLimitaSalida() {
        assertNull(ReductorSesion.interpretar(""))
        assertNull(ReductorSesion.interpretar("no es json"))
        var e = ReductorSesion.iniciar(false)
        val grande = "a".repeat(100_000)
        repeat(5) { e = ReductorSesion.aplicar(e, ReductorSesion.interpretar("""{"tipo":"salida","texto":"$grande","error":false}""")!!) }
        assertEquals(ReductorSesion.MAXIMO_SALIDA, e.salida.sumOf { it.texto.length })
    }

    @Test
    fun puntosDeInterrupcion() {
        val p = PuntosInterrupcion()
        assertTrue(p.alternar("a.py", 5))
        assertTrue(p.alternar("a.py", 2))
        assertEquals(listOf(2, 5), p.de("a.py"))
        assertEquals(false, p.alternar("a.py", 5))
        assertEquals(false, p.alternar("a.py", 0))
        assertEquals(mapOf("a.py" to listOf(2)), p.todos())
        p.limpiar("a.py")
        assertTrue(p.todos().isEmpty())
    }
}
