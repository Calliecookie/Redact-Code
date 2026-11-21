package pe.redactcode.nucleo.ide

import java.io.File

data class OpcionesBusqueda(
    val consulta: String,
    val distinguirMayusculas: Boolean = false,
    val palabraCompleta: Boolean = false,
    val expresionRegular: Boolean = false,
    val incluir: String = "",
    val excluir: String = ""
)

data class CoincidenciaTexto(val linea: Int, val columna: Int, val longitud: Int, val textoLinea: String)

data class ResultadoArchivoBusqueda(val ruta: String, val coincidencias: List<CoincidenciaTexto>)

data class ResultadoBusquedaGlobal(val archivos: List<ResultadoArchivoBusqueda>, val truncado: Boolean, val error: String? = null) {
    val total: Int get() = archivos.sumOf { it.coincidencias.size }
}

object BusquedaGlobal {
    fun patron(o: OpcionesBusqueda): Regex {
        val base = if (o.expresionRegular) o.consulta else Regex.escape(o.consulta)
        val conPalabra = if (o.palabraCompleta) "\\b(?:$base)\\b" else base
        val opciones = if (o.distinguirMayusculas) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex(conPalabra, opciones)
    }

    fun globARegex(glob: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when {
                c == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> { sb.append(".*"); i++ ; if (i + 1 < glob.length && glob[i + 1] == '/') i++ }
                c == '*' -> sb.append("[^/]*")
                c == '?' -> sb.append("[^/]")
                "\\.[]{}()+-^$|".indexOf(c) >= 0 -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i++
        }
        return Regex(sb.append('$').toString(), RegexOption.IGNORE_CASE)
    }

    private fun coincideFiltros(ruta: String, filtros: String): Boolean {
        val lista = filtros.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return lista.any { f ->
            val g = if (f.contains('/') || f.contains("**")) f else "**/$f"
            globARegex(g).matches(ruta) || globARegex(f).matches(ruta)
        }
    }

    fun admite(ruta: String, o: OpcionesBusqueda): Boolean {
        if (o.incluir.isNotBlank() && !coincideFiltros(ruta, o.incluir)) return false
        if (o.excluir.isNotBlank() && coincideFiltros(ruta, o.excluir)) return false
        return true
    }

    fun buscarEnTexto(texto: String, patron: Regex, limite: Int = 1000): List<CoincidenciaTexto> {
        val resultado = mutableListOf<CoincidenciaTexto>()
        texto.split("\n").forEachIndexed { indice, linea ->
            if (resultado.size >= limite) return resultado
            patron.findAll(linea).forEach { m ->
                if (m.value.isNotEmpty() && resultado.size < limite) resultado += CoincidenciaTexto(indice + 1, m.range.first + 1, m.value.length, linea.take(240))
            }
        }
        return resultado
    }

    fun buscar(archivos: Map<String, () -> String?>, o: OpcionesBusqueda, limite: Int = 2000): ResultadoBusquedaGlobal {
        if (o.consulta.isEmpty()) return ResultadoBusquedaGlobal(emptyList(), false)
        val p = runCatching { patron(o) }.getOrElse { return ResultadoBusquedaGlobal(emptyList(), false, "Expresión regular no válida: ${it.message?.lineSequence()?.firstOrNull()}") }
        val lista = mutableListOf<ResultadoArchivoBusqueda>()
        var total = 0
        for ((ruta, lector) in archivos.toSortedMap()) {
            if (!admite(ruta, o)) continue
            val texto = lector() ?: continue
            val coincidencias = buscarEnTexto(texto, p, limite - total)
            if (coincidencias.isNotEmpty()) {
                lista += ResultadoArchivoBusqueda(ruta, coincidencias)
                total += coincidencias.size
                if (total >= limite) return ResultadoBusquedaGlobal(lista, true)
            }
        }
        return ResultadoBusquedaGlobal(lista, false)
    }

    fun reemplazar(texto: String, o: OpcionesBusqueda, reemplazo: String): Pair<String, Int> {
        val p = patron(o)
        var cantidad = 0
        val nuevo = p.replace(texto) { m ->
            cantidad++
            if (o.expresionRegular) {
                var r = reemplazo
                for (g in m.groupValues.indices.reversed()) r = r.replace("$$g", m.groupValues[g])
                r
            } else reemplazo
        }
        return nuevo to cantidad
    }

    fun archivosDeCarpeta(raiz: File, tamanoMaximo: Long = 2L * 1024 * 1024): Map<String, () -> String?> =
        raiz.walkTopDown()
            .onEnter { it == raiz || !it.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") && it.length() <= tamanoMaximo }
            .associate { f -> f.relativeTo(raiz).invariantSeparatorsPath to { runCatching { f.readBytes() }.getOrNull()?.takeIf { b -> b.none { it == 0.toByte() } }?.toString(Charsets.UTF_8) } }
}
