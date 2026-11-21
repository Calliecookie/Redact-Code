package pe.redactcode.nucleo.terminal

data class VersionPep440(
    val liberacion: List<Int>,
    val fase: Int,
    val numeroFase: Int,
    val post: Int,
    val desarrollo: Int,
    val original: String
) : Comparable<VersionPep440> {
    val esPrelanzamiento: Boolean get() = fase < FASE_FINAL || desarrollo != SIN_DESARROLLO

    override fun compareTo(other: VersionPep440): Int {
        val n = maxOf(liberacion.size, other.liberacion.size)
        for (i in 0 until n) {
            val c = (liberacion.getOrElse(i) { 0 }).compareTo(other.liberacion.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        val a = clave(); val b = other.clave()
        for (i in a.indices) { val c = a[i].compareTo(b[i]); if (c != 0) return c }
        return 0
    }

    private fun clave(): List<Int> {
        val dev = if (desarrollo != SIN_DESARROLLO && fase == FASE_FINAL && post == 0) Int.MIN_VALUE else 0
        val faseClave = if (desarrollo != SIN_DESARROLLO && fase == FASE_FINAL && post == 0) -1 else fase
        return listOf(faseClave, numeroFase, post, if (desarrollo == SIN_DESARROLLO) Int.MAX_VALUE else desarrollo, dev)
    }

    companion object {
        const val FASE_FINAL = 3
        const val SIN_DESARROLLO = -1
        private val patron = Regex("""^v?(\d+(?:\.\d+)*)(?:[-_.]?(a|alpha|b|beta|c|rc|pre|preview)[-_.]?(\d*))?(?:(?:-(\d+))|(?:[-_.]?(?:post|rev|r)[-_.]?(\d*)))?(?:[-_.]?dev[-_.]?(\d*))?(?:\+[a-z0-9.]+)?$""")

        fun analizar(texto: String): VersionPep440? {
            val m = patron.matchEntire(texto.trim().lowercase()) ?: return null
            val liberacion = m.groupValues[1].split('.').map { it.toInt() }
            val fase = when (m.groupValues[2]) {
                "" -> FASE_FINAL
                "a", "alpha" -> 0
                "b", "beta" -> 1
                else -> 2
            }
            val numero = if (m.groupValues[2].isEmpty()) 0 else m.groupValues[3].ifEmpty { "0" }.toInt()
            val post = when {
                m.groupValues[4].isNotEmpty() -> m.groupValues[4].toInt()
                m.groups[5] != null -> m.groupValues[5].ifEmpty { "0" }.toInt()
                else -> 0
            }
            val desarrollo = if (m.groups[6] == null) SIN_DESARROLLO else m.groupValues[6].ifEmpty { "0" }.toInt()
            return VersionPep440(liberacion, fase, numero, post, desarrollo, texto.trim())
        }
    }
}

data class RestriccionPep440(val operador: String, val version: String) {
    fun cumple(candidata: VersionPep440): Boolean {
        if (operador == "==" && version.endsWith(".*")) {
            val base = VersionPep440.analizar(version.removeSuffix(".*")) ?: return false
            return candidata.liberacion.take(base.liberacion.size) == base.liberacion
        }
        if (operador == "!=" && version.endsWith(".*")) {
            val base = VersionPep440.analizar(version.removeSuffix(".*")) ?: return true
            return candidata.liberacion.take(base.liberacion.size) != base.liberacion
        }
        val v = VersionPep440.analizar(version) ?: return false
        return when (operador) {
            "==", "===" -> candidata.compareTo(v) == 0
            "!=" -> candidata.compareTo(v) != 0
            ">=" -> candidata >= v
            "<=" -> candidata <= v
            ">" -> candidata > v
            "<" -> candidata < v
            "~=" -> {
                if (v.liberacion.size < 2) return false
                val prefijo = v.liberacion.dropLast(1)
                candidata >= v && candidata.liberacion.take(prefijo.size) == prefijo
            }
            else -> false
        }
    }
}

object EspecificadorPep440 {
    private val patron = Regex("""^\s*(===|==|!=|~=|>=|<=|>|<)\s*([A-Za-z0-9.*+!_\-]+)\s*$""")

    fun analizar(texto: String): List<RestriccionPep440>? {
        if (texto.isBlank()) return emptyList()
        return texto.split(',').map { parte ->
            val m = patron.matchEntire(parte) ?: return null
            RestriccionPep440(m.groupValues[1], m.groupValues[2])
        }
    }

    fun elegir(versiones: Collection<String>, especificador: String, permitirPrelanzamientos: Boolean = false): String? {
        val restricciones = analizar(especificador) ?: return null
        val candidatas = versiones.mapNotNull { v -> VersionPep440.analizar(v)?.let { v to it } }
        val validas = candidatas.filter { (_, v) -> restricciones.all { it.cumple(v) } }
        val finales = validas.filter { !it.second.esPrelanzamiento }
        val elegibles = if (finales.isNotEmpty() || !permitirPrelanzamientos && restricciones.none { it.version.contains(Regex("[a-z]")) }) finales else validas
        return elegibles.maxByOrNull { it.second }?.first
    }

    fun normalizarNombre(nombre: String): String = nombre.lowercase().replace(Regex("[-_.]+"), "-")
}
