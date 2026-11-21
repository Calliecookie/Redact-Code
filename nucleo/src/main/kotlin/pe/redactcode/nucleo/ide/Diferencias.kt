package pe.redactcode.nucleo.ide

enum class TipoCambioLinea { IGUAL, AGREGADA, ELIMINADA }

data class LineaDiferencia(val tipo: TipoCambioLinea, val texto: String, val lineaAnterior: Int?, val lineaNueva: Int?)

data class BloqueDiferencia(
    val inicioAnterior: Int,
    val cantidadAnterior: Int,
    val inicioNuevo: Int,
    val cantidadNueva: Int,
    val lineas: List<LineaDiferencia>
) {
    val encabezado: String get() = "@@ -$inicioAnterior,$cantidadAnterior +$inicioNuevo,$cantidadNueva @@"
}

data class ResultadoDiferencias(val lineas: List<LineaDiferencia>, val bloques: List<BloqueDiferencia>) {
    val agregadas: Int get() = lineas.count { it.tipo == TipoCambioLinea.AGREGADA }
    val eliminadas: Int get() = lineas.count { it.tipo == TipoCambioLinea.ELIMINADA }
    val sinCambios: Boolean get() = agregadas == 0 && eliminadas == 0

    fun unificado(nombreAnterior: String, nombreNuevo: String): String = buildString {
        append("--- ").append(nombreAnterior).append('\n')
        append("+++ ").append(nombreNuevo).append('\n')
        bloques.forEach { b ->
            append(b.encabezado).append('\n')
            b.lineas.forEach { l ->
                append(when (l.tipo) { TipoCambioLinea.IGUAL -> ' '; TipoCambioLinea.AGREGADA -> '+'; TipoCambioLinea.ELIMINADA -> '-' })
                append(l.texto).append('\n')
            }
        }
    }
}

object Diferencias {
    const val LIMITE_EDICIONES = 4000

    fun dividir(texto: String): List<String> = if (texto.isEmpty()) emptyList() else texto.removeSuffix("\n").split("\n")

    fun comparar(anterior: String, nuevo: String, contexto: Int = 3): ResultadoDiferencias =
        compararLineas(dividir(anterior), dividir(nuevo), contexto)

    fun compararLineas(a: List<String>, b: List<String>, contexto: Int = 3): ResultadoDiferencias {
        var prefijo = 0
        while (prefijo < a.size && prefijo < b.size && a[prefijo] == b[prefijo]) prefijo++
        var sufijo = 0
        while (sufijo < a.size - prefijo && sufijo < b.size - prefijo && a[a.size - 1 - sufijo] == b[b.size - 1 - sufijo]) sufijo++
        val medioA = a.subList(prefijo, a.size - sufijo)
        val medioB = b.subList(prefijo, b.size - sufijo)
        val operaciones = mutableListOf<LineaDiferencia>()
        for (i in 0 until prefijo) operaciones += LineaDiferencia(TipoCambioLinea.IGUAL, a[i], i + 1, i + 1)
        myers(medioA, medioB).forEach { (tipo, ia, ib) ->
            operaciones += when (tipo) {
                TipoCambioLinea.IGUAL -> LineaDiferencia(tipo, medioA[ia], prefijo + ia + 1, prefijo + ib + 1)
                TipoCambioLinea.ELIMINADA -> LineaDiferencia(tipo, medioA[ia], prefijo + ia + 1, null)
                TipoCambioLinea.AGREGADA -> LineaDiferencia(tipo, medioB[ib], null, prefijo + ib + 1)
            }
        }
        for (k in 0 until sufijo) {
            val ia = a.size - sufijo + k
            val ib = b.size - sufijo + k
            operaciones += LineaDiferencia(TipoCambioLinea.IGUAL, a[ia], ia + 1, ib + 1)
        }
        return ResultadoDiferencias(operaciones, agrupar(operaciones, contexto))
    }

    private fun myers(a: List<String>, b: List<String>): List<Triple<TipoCambioLinea, Int, Int>> {
        val n = a.size
        val m = b.size
        if (n == 0) return List(m) { Triple(TipoCambioLinea.AGREGADA, 0, it) }
        if (m == 0) return List(n) { Triple(TipoCambioLinea.ELIMINADA, it, 0) }
        val maximo = n + m
        val desplazamiento = maximo
        val v = IntArray(2 * maximo + 2)
        val historial = ArrayList<IntArray>()
        var encontrado = -1
        bucle@ for (d in 0..minOf(maximo, LIMITE_EDICIONES)) {
            historial += v.copyOf()
            var k = -d
            while (k <= d) {
                var x = if (k == -d || (k != d && v[desplazamiento + k - 1] < v[desplazamiento + k + 1])) v[desplazamiento + k + 1] else v[desplazamiento + k - 1] + 1
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) { x++; y++ }
                v[desplazamiento + k] = x
                if (x >= n && y >= m) { encontrado = d; break@bucle }
                k += 2
            }
        }
        if (encontrado < 0) return List(n) { Triple(TipoCambioLinea.ELIMINADA, it, 0) } + List(m) { Triple(TipoCambioLinea.AGREGADA, 0, it) }
        val resultado = ArrayList<Triple<TipoCambioLinea, Int, Int>>()
        var x = n
        var y = m
        for (d in encontrado downTo 0) {
            val vd = historial[d]
            val k = x - y
            val kPrevio = if (k == -d || (k != d && vd[desplazamiento + k - 1] < vd[desplazamiento + k + 1])) k + 1 else k - 1
            val xPrevio = if (d == 0) 0 else vd[desplazamiento + kPrevio]
            val yPrevio = xPrevio - kPrevio
            while (x > xPrevio && y > yPrevio) {
                x--; y--
                resultado += Triple(TipoCambioLinea.IGUAL, x, y)
            }
            if (d > 0) {
                if (x == xPrevio) { y--; resultado += Triple(TipoCambioLinea.AGREGADA, x, y) }
                else { x--; resultado += Triple(TipoCambioLinea.ELIMINADA, x, y) }
            }
        }
        while (x > 0 && y > 0) { x--; y--; resultado += Triple(TipoCambioLinea.IGUAL, x, y) }
        return resultado.asReversed()
    }

    private fun agrupar(lineas: List<LineaDiferencia>, contexto: Int): List<BloqueDiferencia> {
        val cambios = lineas.indices.filter { lineas[it].tipo != TipoCambioLinea.IGUAL }
        if (cambios.isEmpty()) return emptyList()
        val rangos = mutableListOf<IntRange>()
        var inicio = (cambios.first() - contexto).coerceAtLeast(0)
        var fin = (cambios.first() + contexto).coerceAtMost(lineas.lastIndex)
        for (indice in cambios.drop(1)) {
            val desde = (indice - contexto).coerceAtLeast(0)
            if (desde <= fin + 1) fin = (indice + contexto).coerceAtMost(lineas.lastIndex)
            else {
                rangos += inicio..fin
                inicio = desde
                fin = (indice + contexto).coerceAtMost(lineas.lastIndex)
            }
        }
        rangos += inicio..fin
        return rangos.map { r ->
            val trozo = lineas.subList(r.first, r.last + 1)
            val anteriores = trozo.filter { it.tipo != TipoCambioLinea.AGREGADA }
            val nuevas = trozo.filter { it.tipo != TipoCambioLinea.ELIMINADA }
            val inicioAnterior = anteriores.firstOrNull()?.lineaAnterior ?: (lineas.subList(0, r.first).lastOrNull { it.lineaAnterior != null }?.lineaAnterior ?: 0)
            val inicioNuevo = nuevas.firstOrNull()?.lineaNueva ?: (lineas.subList(0, r.first).lastOrNull { it.lineaNueva != null }?.lineaNueva ?: 0)
            BloqueDiferencia(inicioAnterior, anteriores.size, inicioNuevo, nuevas.size, trozo)
        }
    }

    fun aplicarBloques(anterior: String, nuevo: String, resultado: ResultadoDiferencias, aceptados: Set<Int>): String {
        if (resultado.bloques.indices.all { it in aceptados }) return nuevo
        if (resultado.bloques.indices.none { it in aceptados }) return anterior
        val salida = mutableListOf<String>()
        val bloquePorLinea = java.util.IdentityHashMap<LineaDiferencia, Int>()
        resultado.bloques.forEachIndexed { indice, b -> b.lineas.forEach { bloquePorLinea[it] = indice } }
        resultado.lineas.forEach { l ->
            val bloque = bloquePorLinea[l]
            val aceptado = bloque != null && bloque in aceptados
            when (l.tipo) {
                TipoCambioLinea.IGUAL -> salida += l.texto
                TipoCambioLinea.ELIMINADA -> if (!aceptado) salida += l.texto
                TipoCambioLinea.AGREGADA -> if (aceptado) salida += l.texto
            }
        }
        val texto = salida.joinToString("\n")
        return if (texto.isNotEmpty() && (anterior.endsWith("\n") || nuevo.endsWith("\n"))) "$texto\n" else texto
    }
}
