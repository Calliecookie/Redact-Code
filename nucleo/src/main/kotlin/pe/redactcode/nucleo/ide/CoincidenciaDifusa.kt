package pe.redactcode.nucleo.ide

import java.text.Normalizer

data class ResultadoDifuso<T>(val elemento: T, val puntaje: Int, val posiciones: List<Int>)

object CoincidenciaDifusa {
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    fun puntuar(consulta: String, texto: String): ResultadoDifuso<String>? {
        val q = normalizar(consulta).filterNot { it.isWhitespace() }
        if (q.isEmpty()) return ResultadoDifuso(texto, 0, emptyList())
        val t = normalizar(texto)
        val posiciones = mutableListOf<Int>()
        var puntaje = 0
        var previa = -2
        var j = 0
        for (i in t.indices) {
            if (j < q.length && t[i] == q[j]) {
                var bonus = 1
                if (i == previa + 1) bonus += 5
                if (i == 0 || !t[i - 1].isLetterOrDigit()) bonus += 8
                if (i > 0 && texto.getOrNull(i)?.isUpperCase() == true && texto.getOrNull(i - 1)?.isLowerCase() == true) bonus += 6
                puntaje += bonus
                posiciones += i
                previa = i
                j++
            }
        }
        if (j < q.length) return null
        val indiceContiguo = t.indexOf(q)
        if (indiceContiguo >= 0) puntaje += 20 + if (indiceContiguo == 0) 15 else 0
        puntaje -= (t.length - q.length) / 4
        return ResultadoDifuso(texto, puntaje, posiciones)
    }

    fun <T> filtrar(consulta: String, elementos: List<T>, texto: (T) -> String, limite: Int = 60): List<ResultadoDifuso<T>> =
        elementos.mapNotNull { e -> puntuar(consulta, texto(e))?.let { ResultadoDifuso(e, it.puntaje, it.posiciones) } }
            .sortedByDescending { it.puntaje }
            .take(limite)
}
