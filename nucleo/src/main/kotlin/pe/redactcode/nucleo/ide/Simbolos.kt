package pe.redactcode.nucleo.ide

import pe.redactcode.nucleo.modelo.Lenguaje

enum class TipoSimbolo(val nombreVisible: String) {
    CLASE("Clase"), FUNCION("Función"), METODO("Método"), VARIABLE("Variable"), SELECTOR("Selector"),
    ELEMENTO("Elemento"), CLAVE("Clave"), ENCABEZADO("Encabezado")
}

data class SimboloCodigo(val nombre: String, val tipo: TipoSimbolo, val linea: Int, val columna: Int, val profundidad: Int)

object ExtractorSimbolos {
    private val pyClase = Regex("^(\\s*)class\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val pyFuncion = Regex("^(\\s*)(?:async\\s+)?def\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val pyVariable = Regex("^([A-Z_][A-Z0-9_]*)\\s*=")
    private val jsClase = Regex("^(\\s*)(?:export\\s+)?(?:default\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)")
    private val jsFuncion = Regex("^(\\s*)(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\s*\\*?\\s*([A-Za-z_$][A-Za-z0-9_$]*)")
    private val jsFlecha = Regex("^(\\s*)(?:export\\s+)?(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z_$][A-Za-z0-9_$]*)\\s*=>")
    private val jsMetodo = Regex("^(\\s+)(?:static\\s+)?(?:async\\s+)?(?:get\\s+|set\\s+)?([A-Za-z_$#][A-Za-z0-9_$]*)\\s*\\([^)]*\\)\\s*\\{")
    private val reservadas = setOf("if", "for", "while", "switch", "catch", "function", "return", "else")

    fun extraer(lenguaje: Lenguaje, texto: String): List<SimboloCodigo> {
        val lineas = texto.split("\n")
        return when (lenguaje) {
            Lenguaje.PYTHON -> lineas.flatMapIndexed { i, l ->
                listOfNotNull(
                    pyClase.find(l)?.let { SimboloCodigo(it.groupValues[2], TipoSimbolo.CLASE, i + 1, it.groupValues[1].length + 1, it.groupValues[1].length / 4) },
                    pyFuncion.find(l)?.let { val p = it.groupValues[1].length; SimboloCodigo(it.groupValues[2], if (p > 0) TipoSimbolo.METODO else TipoSimbolo.FUNCION, i + 1, p + 1, p / 4) },
                    pyVariable.find(l)?.let { SimboloCodigo(it.groupValues[1], TipoSimbolo.VARIABLE, i + 1, 1, 0) }
                )
            }
            Lenguaje.JAVASCRIPT -> lineas.flatMapIndexed { i, l ->
                val clase = jsClase.find(l)?.let { SimboloCodigo(it.groupValues[2], TipoSimbolo.CLASE, i + 1, it.groupValues[1].length + 1, 0) }
                val funcion = (jsFuncion.find(l) ?: jsFlecha.find(l))?.let { SimboloCodigo(it.groupValues[2], TipoSimbolo.FUNCION, i + 1, it.groupValues[1].length + 1, if (it.groupValues[1].isEmpty()) 0 else 1) }
                val metodo = if (clase == null && funcion == null) jsMetodo.find(l)?.takeIf { it.groupValues[2] !in reservadas }?.let { SimboloCodigo(it.groupValues[2], TipoSimbolo.METODO, i + 1, it.groupValues[1].length + 1, 1) } else null
                listOfNotNull(clase, funcion, metodo)
            }
            Lenguaje.CSS -> {
                val resultado = mutableListOf<SimboloCodigo>()
                var enComentario = false
                lineas.forEachIndexed { i, l ->
                    var t = l
                    if (enComentario) { val fin = t.indexOf("*/"); if (fin < 0) return@forEachIndexed; t = t.substring(fin + 2); enComentario = false }
                    t = t.replace(Regex("/\\*.*?\\*/"), "")
                    if (t.contains("/*")) { enComentario = true; t = t.substringBefore("/*") }
                    val llave = t.indexOf('{')
                    if (llave > 0) {
                        val selector = t.substring(0, llave).trim()
                        if (selector.isNotEmpty()) resultado += SimboloCodigo(selector.take(80), TipoSimbolo.SELECTOR, i + 1, l.indexOf(selector).coerceAtLeast(0) + 1, (l.length - l.trimStart().length) / 2)
                    }
                }
                resultado
            }
            Lenguaje.HTML -> lineas.flatMapIndexed { i, l ->
                val res = mutableListOf<SimboloCodigo>()
                Regex("<(h[1-6])[^>]*>([^<]{1,80})").findAll(l).forEach { res += SimboloCodigo(it.groupValues[2].trim(), TipoSimbolo.ENCABEZADO, i + 1, it.range.first + 1, it.groupValues[1].drop(1).toInt() - 1) }
                Regex("<([a-zA-Z][a-zA-Z0-9-]*)[^>]*\\sid\\s*=\\s*[\"']([^\"']+)[\"']").findAll(l).forEach { res += SimboloCodigo("${it.groupValues[1]}#${it.groupValues[2]}", TipoSimbolo.ELEMENTO, i + 1, it.range.first + 1, 0) }
                res
            }
            Lenguaje.JSON -> lineas.flatMapIndexed { i, l ->
                Regex("^(\\s*)\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:").find(l)?.let {
                    val p = it.groupValues[1].length
                    if (p <= 8) listOf(SimboloCodigo(it.groupValues[2], TipoSimbolo.CLAVE, i + 1, p + 1, p / 2)) else emptyList()
                } ?: emptyList()
            }
            Lenguaje.TEXTO -> lineas.flatMapIndexed { i, l ->
                Regex("^(#{1,6})\\s+(.+)").find(l)?.let { listOf(SimboloCodigo(it.groupValues[2].trim(), TipoSimbolo.ENCABEZADO, i + 1, 1, it.groupValues[1].length - 1)) } ?: emptyList()
            }
        }
    }

    fun palabraEn(texto: String, linea: Int, columna: Int): String? {
        val l = texto.split("\n").getOrNull(linea - 1) ?: return null
        val c = (columna - 1).coerceIn(0, l.length)
        var a = c
        var b = c
        while (a > 0 && (l[a - 1].isLetterOrDigit() || l[a - 1] == '_' || l[a - 1] == '$')) a--
        while (b < l.length && (l[b].isLetterOrDigit() || l[b] == '_' || l[b] == '$')) b++
        return l.substring(a, b).takeIf { it.isNotEmpty() && !it.first().isDigit() }
    }

    fun definicion(lenguaje: Lenguaje, texto: String, nombre: String): SimboloCodigo? =
        extraer(lenguaje, texto).firstOrNull { it.nombre == nombre }

    fun referencias(texto: String, nombre: String): List<CoincidenciaTexto> =
        BusquedaGlobal.buscarEnTexto(texto, BusquedaGlobal.patron(OpcionesBusqueda(nombre, distinguirMayusculas = true, palabraCompleta = true)))
}
