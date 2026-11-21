package pe.redactcode.nucleo.extensiones

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pe.redactcode.nucleo.modelo.Lenguaje
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

@Serializable
data class FragmentoExtension(
    val nombre: String,
    val prefijo: String,
    val cuerpo: String,
    val descripcion: String = "",
    val lenguaje: String? = null
)

@Serializable
data class TemaExtension(
    val nombre: String,
    val oscuro: Boolean,
    val colores: Map<String, String>
)

@Serializable
data class ManifiestoExtension(
    val id: String,
    val nombre: String,
    val version: String,
    val autor: String = "",
    val descripcion: String = "",
    val licencia: String = "",
    val origen: String = ORIGEN_REDACT,
    val fragmentos: List<FragmentoExtension> = emptyList(),
    val temas: List<TemaExtension> = emptyList(),
    val omitido: List<String> = emptyList()
) {
    companion object {
        const val ORIGEN_REDACT = "redactcode"
        const val ORIGEN_VSCODE = "vscode"
    }
}

class ErrorExtension(mensaje: String) : Exception(mensaje)

data class FragmentoExpandido(val texto: String, val cursor: Int)

object LectorExtensiones {
    const val TAMANO_MAXIMO_PAQUETE = 20L * 1024 * 1024
    const val TAMANO_MAXIMO_ENTRADA = 2L * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val patronId = Regex("^[a-z0-9][a-z0-9._-]{1,100}$")

    val clavesTema = mapOf(
        "editor.background" to "fondo",
        "editor.foreground" to "texto",
        "editor.lineHighlightBackground" to "lineaActual",
        "editorLineNumber.foreground" to "numeroLinea",
        "editor.selectionBackground" to "seleccion",
        "editorCursor.foreground" to "cursor"
    )

    private val ambitos = listOf(
        "comment" to "comentario",
        "string" to "cadena",
        "constant.numeric" to "numero",
        "keyword.operator" to "operador",
        "keyword" to "palabraClave",
        "storage" to "palabraClave",
        "entity.name.function" to "funcion",
        "support.function" to "funcion",
        "entity.name.type" to "tipo",
        "entity.name.class" to "tipo",
        "support.class" to "tipo",
        "entity.name.tag" to "etiqueta",
        "entity.other.attribute-name" to "atributo"
    )

    private val lenguajesVsCode = mapOf(
        "python" to Lenguaje.PYTHON, "javascript" to Lenguaje.JAVASCRIPT, "javascriptreact" to Lenguaje.JAVASCRIPT,
        "html" to Lenguaje.HTML, "css" to Lenguaje.CSS, "json" to Lenguaje.JSON, "jsonc" to Lenguaje.JSON, "plaintext" to Lenguaje.TEXTO
    )

    fun lenguajeVsCode(id: String): Lenguaje? = lenguajesVsCode[id.lowercase()]

    fun limpiarJsonConComentarios(texto: String): String {
        val sb = StringBuilder()
        var i = 0
        var enCadena = false
        while (i < texto.length) {
            val c = texto[i]
            if (enCadena) {
                sb.append(c)
                if (c == '\\' && i + 1 < texto.length) { sb.append(texto[i + 1]); i += 2; continue }
                if (c == '"') enCadena = false
                i++
                continue
            }
            when {
                c == '"' -> { enCadena = true; sb.append(c); i++ }
                c == '/' && texto.getOrNull(i + 1) == '/' -> { while (i < texto.length && texto[i] != '\n') i++ }
                c == '/' && texto.getOrNull(i + 1) == '*' -> { val fin = texto.indexOf("*/", i + 2); i = if (fin < 0) texto.length else fin + 2 }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString().replace(Regex(",(\\s*[}\\]])"), "$1")
    }

    fun leerLimitado(entrada: java.io.InputStream, limite: Long): ByteArray {
        val salida = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (total < limite) {
            val n = entrada.read(buffer, 0, minOf(buffer.size.toLong(), limite - total).toInt())
            if (n < 0) break
            salida.write(buffer, 0, n)
            total += n
        }
        return salida.toByteArray()
    }

    private fun parsear(texto: String): JsonElement =
        runCatching { json.parseToJsonElement(limpiarJsonConComentarios(texto.removePrefix("﻿"))) }.getOrElse { throw ErrorExtension("JSON no válido: ${it.message?.lineSequence()?.firstOrNull()}") }

    private fun texto(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull

    fun fragmentosVsCode(contenido: String, lenguaje: String?): List<FragmentoExtension> {
        val raiz = parsear(contenido) as? JsonObject ?: throw ErrorExtension("El archivo de fragmentos debe ser un objeto JSON")
        return raiz.mapNotNull { (nombre, valor) ->
            val obj = valor as? JsonObject ?: return@mapNotNull null
            val prefijos = when (val p = obj["prefix"]) { is JsonArray -> p.mapNotNull { texto(it) }; else -> listOfNotNull(texto(p)) }
            val cuerpo = when (val b = obj["body"]) { is JsonArray -> b.mapNotNull { texto(it) }.joinToString("\n"); else -> texto(b) } ?: return@mapNotNull null
            val alcance = texto(obj["scope"])?.split(',')?.map { it.trim() }?.firstOrNull { lenguajeVsCode(it) != null }
            val idLenguaje = (lenguaje ?: alcance)?.let { lenguajeVsCode(it)?.name }
            prefijos.firstOrNull()?.takeIf { it.isNotBlank() }?.let { FragmentoExtension(nombre.take(80), it.take(40), cuerpo, texto(obj["description"]).orEmpty().take(200), idLenguaje) }
        }
    }

    private fun colorValido(valor: String?): String? = valor?.trim()?.takeIf { Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$").matches(it) }?.let { if (it.length == 9) it.substring(0, 7) else it }

    fun temaVsCode(contenido: String, nombrePorDefecto: String, oscuroPorDefecto: Boolean): TemaExtension {
        val raiz = parsear(contenido) as? JsonObject ?: throw ErrorExtension("El tema debe ser un objeto JSON")
        val colores = mutableMapOf<String, String>()
        (raiz["colors"] as? JsonObject)?.forEach { (clave, valor) -> clavesTema[clave]?.let { destino -> colorValido(texto(valor))?.let { colores[destino] = it } } }
        val reglas = (raiz["tokenColors"] as? JsonArray).orEmpty()
        ambitos.forEach { (ambito, destino) ->
            if (destino in colores) return@forEach
            val regla = reglas.firstOrNull { r ->
                val scope = (r as? JsonObject)?.get("scope")
                val lista = when (scope) { is JsonArray -> scope.mapNotNull { texto(it) }; else -> texto(scope)?.split(',')?.map { it.trim() }.orEmpty() }
                lista.any { it == ambito || it.startsWith("$ambito.") }
            } as? JsonObject
            colorValido(texto((regla?.get("settings") as? JsonObject)?.get("foreground")))?.let { colores[destino] = it }
        }
        if (colores.isEmpty()) throw ErrorExtension("El tema no contiene colores compatibles")
        val tipo = texto(raiz["type"])
        return TemaExtension(texto(raiz["name"]) ?: nombrePorDefecto, tipo?.let { it != "light" && it != "hc-light" } ?: oscuroPorDefecto, colores)
    }

    fun desdeJsonRedact(contenido: String): ManifiestoExtension {
        val m = runCatching { json.decodeFromString<ManifiestoExtension>(contenido) }.getOrElse { throw ErrorExtension("Manifiesto no válido: ${it.message?.lineSequence()?.firstOrNull()}") }
        return validar(m.copy(origen = ManifiestoExtension.ORIGEN_REDACT))
    }

    fun validar(m: ManifiestoExtension): ManifiestoExtension {
        if (!patronId.matches(m.id)) throw ErrorExtension("Identificador de extensión no válido")
        if (m.nombre.isBlank()) throw ErrorExtension("La extensión no tiene nombre")
        if (m.fragmentos.isEmpty() && m.temas.isEmpty()) throw ErrorExtension("La extensión no aporta fragmentos ni temas compatibles")
        if (m.fragmentos.size > 2000) throw ErrorExtension("Demasiados fragmentos (máximo 2000)")
        return m
    }

    private fun leerZip(bytes: ByteArray): Map<String, ByteArray> {
        if (bytes.size > TAMANO_MAXIMO_PAQUETE) throw ErrorExtension("El paquete supera 20 MB")
        val entradas = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val nombre = e.name.replace('\\', '/')
                if (e.isDirectory || nombre.startsWith("/") || nombre.split('/').any { it == ".." }) continue
                val datos = leerLimitado(zip, TAMANO_MAXIMO_ENTRADA + 1)
                if (datos.size > TAMANO_MAXIMO_ENTRADA) continue
                total += datos.size
                if (total > 60L * 1024 * 1024) throw ErrorExtension("El paquete descomprimido es demasiado grande")
                entradas[nombre] = datos
            }
        }
        return entradas
    }

    private fun normalizarRuta(base: String, relativa: String): String {
        val partes = ArrayDeque<String>()
        (base.split('/') + relativa.removePrefix("./").split('/')).forEach { p ->
            when (p) { "", "." -> Unit; ".." -> partes.removeLastOrNull(); else -> partes.addLast(p) }
        }
        return partes.joinToString("/")
    }

    fun desdeVsix(bytes: ByteArray): ManifiestoExtension {
        val entradas = leerZip(bytes)
        val paquete = entradas["extension/package.json"] ?: throw ErrorExtension("No es un paquete .vsix válido (falta extension/package.json)")
        val pj = parsear(paquete.toString(Charsets.UTF_8)) as? JsonObject ?: throw ErrorExtension("package.json no válido")
        val nombre = texto(pj["name"]) ?: throw ErrorExtension("package.json sin nombre")
        val editor = texto(pj["publisher"]).orEmpty()
        val omitido = mutableListOf<String>()
        if (pj["main"] != null || pj["browser"] != null) omitido += "Código ejecutable de la extensión (Redact Code no ejecuta código de extensiones de VS Code)"
        val contribuciones = pj["contributes"] as? JsonObject ?: JsonObject(emptyMap())
        contribuciones.keys.filter { it !in setOf("snippets", "themes") }.forEach { omitido += "Contribución no compatible: $it" }
        val fragmentos = mutableListOf<FragmentoExtension>()
        (contribuciones["snippets"] as? JsonArray).orEmpty().forEach { s ->
            val o = s as? JsonObject ?: return@forEach
            val ruta = normalizarRuta("extension", texto(o["path"]) ?: return@forEach)
            val lenguaje = texto(o["language"])
            if (lenguaje != null && lenguajeVsCode(lenguaje) == null) { omitido += "Fragmentos para $lenguaje (lenguaje no disponible)"; return@forEach }
            val datos = entradas[ruta] ?: run { omitido += "Archivo de fragmentos faltante: $ruta"; return@forEach }
            runCatching { fragmentos += fragmentosVsCode(datos.toString(Charsets.UTF_8), lenguaje) }.onFailure { omitido += "Fragmentos no válidos en $ruta" }
        }
        val temas = mutableListOf<TemaExtension>()
        (contribuciones["themes"] as? JsonArray).orEmpty().forEach { t ->
            val o = t as? JsonObject ?: return@forEach
            val ruta = normalizarRuta("extension", texto(o["path"]) ?: return@forEach)
            val datos = entradas[ruta] ?: run { omitido += "Archivo de tema faltante: $ruta"; return@forEach }
            val oscuro = texto(o["uiTheme"])?.let { it == "vs-dark" || it == "hc-black" } ?: true
            runCatching { temas += temaVsCode(datos.toString(Charsets.UTF_8), texto(o["label"]) ?: nombre, oscuro) }.onFailure { omitido += "Tema no compatible: ${texto(o["label"]) ?: ruta}" }
        }
        val id = "${editor.ifBlank { "vscode" }}.$nombre".lowercase().replace(Regex("[^a-z0-9._-]"), "-")
        return validar(
            ManifiestoExtension(
                id = id, nombre = texto(pj["displayName"]) ?: nombre, version = texto(pj["version"]) ?: "0.0.0",
                autor = editor, descripcion = texto(pj["description"]).orEmpty().take(300),
                licencia = texto(pj["license"]).orEmpty(), origen = ManifiestoExtension.ORIGEN_VSCODE,
                fragmentos = fragmentos, temas = temas, omitido = omitido.distinct()
            )
        )
    }

    fun expandir(cuerpo: String, nombreArchivo: String = ""): FragmentoExpandido {
        val sb = StringBuilder()
        var cursor = -1
        var primerMarcador = -1
        var i = 0
        val variables = mapOf("TM_FILENAME" to nombreArchivo, "TM_FILENAME_BASE" to nombreArchivo.substringBeforeLast('.'), "TM_SELECTED_TEXT" to "", "CLIPBOARD" to "")
        fun marcar(numero: Int) {
            if (numero == 0) cursor = sb.length
            else if (primerMarcador < 0 && numero == 1) primerMarcador = sb.length
        }
        while (i < cuerpo.length) {
            val c = cuerpo[i]
            if (c == '\\' && i + 1 < cuerpo.length && cuerpo[i + 1] in "$}\\") { sb.append(cuerpo[i + 1]); i += 2; continue }
            if (c == '$' && i + 1 < cuerpo.length) {
                val sig = cuerpo[i + 1]
                if (sig.isDigit()) {
                    var j = i + 1
                    while (j < cuerpo.length && cuerpo[j].isDigit()) j++
                    marcar(cuerpo.substring(i + 1, j).toInt())
                    i = j
                    continue
                }
                if (sig == '{') {
                    var nivel = 1
                    var j = i + 2
                    while (j < cuerpo.length && nivel > 0) { if (cuerpo[j] == '{') nivel++ else if (cuerpo[j] == '}') nivel--; j++ }
                    val interior = cuerpo.substring(i + 2, (j - 1).coerceAtLeast(i + 2))
                    val numero = interior.takeWhile { it.isDigit() }
                    if (numero.isNotEmpty()) {
                        marcar(numero.toInt())
                        val resto = interior.drop(numero.length)
                        when {
                            resto.startsWith(":") -> sb.append(expandir(resto.drop(1), nombreArchivo).texto)
                            resto.startsWith("|") -> sb.append(resto.drop(1).substringBefore('|').substringBefore(','))
                        }
                    } else {
                        val nombreVar = interior.substringBefore(':')
                        sb.append(variables[nombreVar] ?: interior.substringAfter(':', ""))
                    }
                    i = j
                    continue
                }
                if (sig.isLetter() || sig == '_') {
                    var j = i + 1
                    while (j < cuerpo.length && (cuerpo[j].isLetterOrDigit() || cuerpo[j] == '_')) j++
                    sb.append(variables[cuerpo.substring(i + 1, j)].orEmpty())
                    i = j
                    continue
                }
            }
            sb.append(c)
            i++
        }
        val posicion = if (primerMarcador >= 0) primerMarcador else if (cursor >= 0) cursor else sb.length
        return FragmentoExpandido(sb.toString().replace("\t", "    "), posicion)
    }

    fun exportar(m: ManifiestoExtension): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(ManifiestoExtension.serializer(), m)
}
