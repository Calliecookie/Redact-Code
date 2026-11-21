package pe.redactcode.nucleo.revision

import kotlinx.serialization.Serializable
import pe.redactcode.nucleo.modelo.CategoriaConcepto
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.Severidad
import pe.redactcode.nucleo.validacion.AnalizadorCss
import pe.redactcode.nucleo.validacion.AnalizadorHtml

@Serializable
enum class TipoHallazgo(val nombreVisible: String) {
    CONFIRMADO("Error confirmado"),
    ADVERTENCIA("Advertencia"),
    SUGERENCIA("Sugerencia"),
    SEGURIDAD("Posible riesgo de seguridad")
}

@Serializable
data class Hallazgo(
    val regla: String,
    val tipo: TipoHallazgo,
    val linea: Int,
    val columna: Int,
    val titulo: String,
    val detalle: String,
    val recomendacion: String,
    val ejemploAntes: String,
    val ejemploDespues: String,
    val categoria: CategoriaConcepto
)

@Serializable
data class HallazgoCrudo(val regla: String, val linea: Int, val columna: Int = 1, val extra: String = "")

data class InformeRevision(
    val lenguaje: Lenguaje,
    val hallazgos: List<Hallazgo>,
    val erroresSintaxis: Int,
    val herramientas: List<String>
) {
    val confirmados get() = hallazgos.filter { it.tipo == TipoHallazgo.CONFIRMADO }
    val advertencias get() = hallazgos.filter { it.tipo != TipoHallazgo.CONFIRMADO }
    val avisoAlcance: String =
        "Este informe combina análisis sintáctico real con reglas heurísticas. No reemplaza una auditoría de seguridad profesional."
}

object ReglasRevision {
    private data class Plantilla(
        val tipo: TipoHallazgo,
        val categoria: CategoriaConcepto,
        val titulo: (List<String>) -> String,
        val detalle: (List<String>) -> String,
        val recomendacion: String,
        val antes: String,
        val despues: String
    )

    private val plantillas: Map<String, Plantilla> = mapOf(
        "JS_VAR" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.VARIABLES, { "Uso de var" },
            { "var tiene alcance de función y permite redeclaraciones, lo que facilita errores difíciles de rastrear." },
            "Usa const para valores que no cambian y let para los que sí.", "var total = 0;", "let total = 0;"),
        "JS_IGUALDAD_DEBIL" to Plantilla(TipoHallazgo.ADVERTENCIA, CategoriaConcepto.CONDICIONALES, { "Comparación con ${it.getOrElse(0) { "==" }}" },
            { "La comparación débil convierte tipos de forma implícita: 0 == \"\" es true." },
            "Usa === y !== para comparar valor y tipo.", "if (edad == \"18\") {}", "if (edad === 18) {}"),
        "SEG_EVAL" to Plantilla(TipoHallazgo.SEGURIDAD, CategoriaConcepto.FUNCIONES, { "Ejecución dinámica de código" },
            { "eval y new Function ejecutan texto como código. Si ese texto proviene del usuario, permite inyectar código." },
            "Evita eval; usa JSON.parse para datos o funciones definidas explícitamente.", "const datos = eval(texto);", "const datos = JSON.parse(texto);"),
        "SEG_INNERHTML" to Plantilla(TipoHallazgo.SEGURIDAD, CategoriaConcepto.HTML_ESTRUCTURA, { "Asignación a innerHTML" },
            { "Insertar HTML construido con datos externos puede permitir ataques de inyección de scripts (XSS)." },
            "Usa textContent para texto o crea los elementos con document.createElement.", "lista.innerHTML = \"<li>\" + nombre + \"</li>\";",
            "const item = document.createElement(\"li\");\nitem.textContent = nombre;\nlista.appendChild(item);"),
        "SEG_DOCUMENT_WRITE" to Plantilla(TipoHallazgo.SEGURIDAD, CategoriaConcepto.HTML_ESTRUCTURA, { "Uso de document.write" },
            { "document.write reemplaza el documento si se llama después de la carga y puede introducir contenido no controlado." },
            "Modifica el DOM con métodos como appendChild o textContent.", "document.write(mensaje);", "salida.textContent = mensaje;"),
        "CATCH_VACIO" to Plantilla(TipoHallazgo.ADVERTENCIA, CategoriaConcepto.EXCEPCIONES, { "Bloque catch vacío" },
            { "El error se captura y se descarta en silencio, lo que oculta fallos reales." },
            "Registra el error o informa al usuario dentro del catch.", "try { guardar(); } catch (e) {}", "try { guardar(); } catch (e) { console.error(\"No se pudo guardar\", e); }"),
        "PY_EXCEPT_VACIO" to Plantilla(TipoHallazgo.ADVERTENCIA, CategoriaConcepto.EXCEPCIONES, { "except que ignora el error" },
            { "Un except con solo pass oculta cualquier fallo, incluso los inesperados." },
            "Captura excepciones específicas y maneja o informa el error.", "try:\n    valor = int(texto)\nexcept:\n    pass",
            "try:\n    valor = int(texto)\nexcept ValueError:\n    valor = 0"),
        "PY_EXCEPT_GENERAL" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.EXCEPCIONES, { "except sin tipo de excepción" },
            { "Un except sin tipo captura todo, incluso interrupciones del programa." },
            "Indica el tipo de excepción que esperas, por ejemplo except ValueError:.", "except:", "except ValueError:"),
        "FUNCION_COMPLEJA" to Plantilla(TipoHallazgo.ADVERTENCIA, CategoriaConcepto.FUNCIONES, { "Función compleja: ${it.getOrElse(0) { "" }}" },
            { "La función tiene una complejidad ciclomática de ${it.getOrElse(1) { "?" }} (más de 10 caminos posibles), lo que dificulta probarla y entenderla." },
            "Divide la función en funciones más pequeñas con una sola responsabilidad.",
            "function procesar(d) {\n  if (...) { ... } else if (...) { ... }\n}", "function validar(d) { ... }\nfunction calcular(d) { ... }"),
        "FUNCION_LARGA" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.FUNCIONES, { "Función extensa: ${it.getOrElse(0) { "" }}" },
            { "La función ocupa ${it.getOrElse(1) { "?" }} líneas. Las funciones largas suelen hacer demasiadas cosas." },
            "Extrae bloques con un propósito claro a funciones con nombres descriptivos.", "", ""),
        "MUCHOS_PARAMETROS" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.FUNCIONES, { "Demasiados parámetros: ${it.getOrElse(0) { "" }}" },
            { "La función recibe ${it.getOrElse(1) { "?" }} parámetros, lo que hace fácil confundir su orden." },
            "Agrupa los parámetros relacionados en un objeto o diccionario.", "crear(nombre, edad, ciudad, pais, correo, telefono)", "crear(persona)"),
        "ANIDAMIENTO_PROFUNDO" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.CONDICIONALES, { "Anidamiento profundo en ${it.getOrElse(0) { "" }}" },
            { "Hay más de cuatro niveles de bloques anidados, lo que reduce la legibilidad." },
            "Usa retornos tempranos o extrae funciones para reducir niveles.", "if (a) {\n  if (b) {\n    if (c) { ... }\n  }\n}", "if (!a || !b || !c) return;\n..."),
        "VARIABLE_NO_USADA" to Plantilla(TipoHallazgo.ADVERTENCIA, CategoriaConcepto.VARIABLES, { "Variable no utilizada: ${it.getOrElse(0) { "" }}" },
            { "La variable '${it.getOrElse(0) { "" }}' se declara pero nunca se usa." },
            "Elimínala o úsala donde corresponda.", "const temporal = calcular();", "calcular();"),
        "PREFERIR_CONST" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.VARIABLES, { "Puede ser const: ${it.getOrElse(0) { "" }}" },
            { "'${it.getOrElse(0) { "" }}' se declara con let pero nunca se reasigna." },
            "Declárala con const para expresar que no cambia.", "let pi = 3.1416;", "const pi = 3.1416;"),
        "PY_IMPORT_NO_USADO" to Plantilla(TipoHallazgo.ADVERTENCIA, CategoriaConcepto.FUNDAMENTOS, { "Importación no utilizada: ${it.getOrElse(0) { "" }}" },
            { "Se importa '${it.getOrElse(0) { "" }}' pero no se usa en el archivo." },
            "Elimina la importación innecesaria.", "import math\nprint(2 + 2)", "print(2 + 2)"),
        "PY_GLOBAL" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.FUNCIONES, { "Uso de global" },
            { "Modificar variables globales desde funciones dificulta seguir el flujo de datos." },
            "Pasa los valores como parámetros y devuelve el resultado.", "def sumar():\n    global total\n    total += 1",
            "def sumar(total):\n    return total + 1"),
        "PY_COMPARACION_NONE" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.CONDICIONALES, { "Comparación con None usando ==" },
            { "Para comparar con None se recomienda usar is, que compara identidad." },
            "Usa 'is None' o 'is not None'.", "if valor == None:", "if valor is None:"),
        "PY_NOMBRE_RESERVADO" to Plantilla(TipoHallazgo.ADVERTENCIA, CategoriaConcepto.VARIABLES, { "Se oculta una función incorporada: ${it.getOrElse(0) { "" }}" },
            { "La variable '${it.getOrElse(0) { "" }}' reemplaza una función incorporada de Python y puede causar errores más adelante." },
            "Usa otro nombre, por ejemplo agregando un sufijo descriptivo.", "list = [1, 2]", "numeros = [1, 2]"),
        "PY_ARGUMENTO_MUTABLE" to Plantilla(TipoHallazgo.ADVERTENCIA, CategoriaConcepto.FUNCIONES, { "Argumento por defecto mutable" },
            { "Una lista o diccionario como valor por defecto se comparte entre todas las llamadas a la función." },
            "Usa None como valor por defecto y crea la lista dentro de la función.", "def agregar(x, lista=[]):",
            "def agregar(x, lista=None):\n    if lista is None:\n        lista = []"),
        "SEG_PY_EVAL" to Plantilla(TipoHallazgo.SEGURIDAD, CategoriaConcepto.FUNCIONES, { "Uso de eval o exec" },
            { "eval y exec ejecutan texto como código Python. Con datos externos permiten ejecutar instrucciones arbitrarias." },
            "Convierte datos con int(), float() o json.loads() en lugar de eval.", "edad = eval(input())", "edad = int(input())"),
        "SEG_PY_SHELL" to Plantilla(TipoHallazgo.SEGURIDAD, CategoriaConcepto.FUNCIONES, { "Ejecución de comandos del sistema" },
            { "os.system, subprocess con shell=True o funciones similares pueden permitir inyección de comandos." },
            "Evita construir comandos con datos externos; usa listas de argumentos sin shell=True.",
            "os.system(\"ls \" + carpeta)", "subprocess.run([\"ls\", carpeta])"),
        "SEG_PY_PICKLE" to Plantilla(TipoHallazgo.SEGURIDAD, CategoriaConcepto.ESTRUCTURAS_DATOS, { "Deserialización con pickle" },
            { "pickle.load puede ejecutar código al cargar datos no confiables." },
            "Usa json para intercambiar datos con fuentes externas.", "datos = pickle.load(archivo)", "datos = json.load(archivo)"),
        "CODIGO_DUPLICADO" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.FUNCIONES, { "Bloque de código repetido" },
            { "Las líneas a partir de aquí repiten el bloque que empieza en la línea ${it.getOrElse(0) { "?" }}." },
            "Extrae el bloque repetido a una función y llámala desde ambos lugares.", "", ""),
        "LINEA_LARGA" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.FUNDAMENTOS, { "Línea muy larga" },
            { "La línea tiene ${it.getOrElse(0) { "?" }} caracteres, lo que dificulta leerla en pantallas pequeñas." },
            "Divide la expresión en varias líneas o usa variables intermedias.", "", ""),
        "CSS_IMPORTANT" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.CSS_ESTILOS, { "Uso frecuente de !important" },
            { "Se encontraron ${it.getOrElse(0) { "?" }} declaraciones con !important. Su abuso hace difícil mantener los estilos." },
            "Ajusta la especificidad de los selectores en lugar de forzar con !important.", "p { color: red !important; }", ".aviso p { color: red; }"),
        "CSS_PROPIEDAD_REPETIDA" to Plantilla(TipoHallazgo.ADVERTENCIA, CategoriaConcepto.CSS_ESTILOS, { "Propiedad repetida: ${it.getOrElse(0) { "" }}" },
            { "La propiedad '${it.getOrElse(0) { "" }}' aparece más de una vez en la misma regla; solo se aplica la última." },
            "Deja una sola declaración de la propiedad.", "p { color: red; color: blue; }", "p { color: blue; }"),
        "CSS_SELECTOR_REPETIDO" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.CSS_ESTILOS, { "Selector repetido: ${it.getOrElse(0) { "" }}" },
            { "El selector '${it.getOrElse(0) { "" }}' aparece en varias reglas." },
            "Une las declaraciones en una sola regla.", "h1 { color: red; }\nh1 { margin: 0; }", "h1 { color: red; margin: 0; }"),
        "CSS_REGLA_VACIA" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.CSS_ESTILOS, { "Regla vacía" },
            { "La regla '${it.getOrElse(0) { "" }}' no contiene declaraciones." }, "Elimina la regla o agrega sus estilos.", "nav { }", ""),
        "HTML_IMG_ALT" to Plantilla(TipoHallazgo.ADVERTENCIA, CategoriaConcepto.HTML_ESTRUCTURA, { "Imagen sin texto alternativo" },
            { "Las imágenes sin atributo alt no son accesibles para lectores de pantalla." },
            "Agrega alt con una descripción breve, o alt=\"\" si es decorativa.", "<img src=\"logo.png\">", "<img src=\"logo.png\" alt=\"Logo de la tienda\">"),
        "HTML_LANG" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.HTML_ESTRUCTURA, { "Falta el idioma del documento" },
            { "El elemento <html> no declara el atributo lang." },
            "Agrega lang=\"es\" para mejorar la accesibilidad y el SEO.", "<html>", "<html lang=\"es\">"),
        "HTML_TITLE" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.HTML_ESTRUCTURA, { "Falta el título de la página" },
            { "El documento no tiene un elemento <title>." }, "Agrega <title> dentro de <head>.", "<head></head>", "<head>\n  <title>Mi página</title>\n</head>"),
        "HTML_EVENTO_EN_LINEA" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.HTML_ESTRUCTURA, { "Manejador de eventos en línea: ${it.getOrElse(0) { "" }}" },
            { "Mezclar JavaScript dentro de atributos HTML dificulta el mantenimiento." },
            "Asocia el evento desde JavaScript con addEventListener.", "<button onclick=\"guardar()\">", "boton.addEventListener(\"click\", guardar);"),
        "HTML_BLANK_NOOPENER" to Plantilla(TipoHallazgo.SEGURIDAD, CategoriaConcepto.HTML_ESTRUCTURA, { "Enlace target=_blank sin rel" },
            { "En navegadores antiguos la página abierta podía acceder a window.opener." },
            "Agrega rel=\"noopener noreferrer\".", "<a href=\"x\" target=\"_blank\">", "<a href=\"x\" target=\"_blank\" rel=\"noopener noreferrer\">"),
        "HTML_ID_DUPLICADO" to Plantilla(TipoHallazgo.CONFIRMADO, CategoriaConcepto.HTML_ESTRUCTURA, { "id duplicado: ${it.getOrElse(0) { "" }}" },
            { "El atributo id debe ser único en el documento, pero '${it.getOrElse(0) { "" }}' se repite." },
            "Usa un id distinto o una clase si quieres agrupar elementos.", "<p id=\"aviso\"></p><p id=\"aviso\"></p>", "<p class=\"aviso\"></p><p class=\"aviso\"></p>"),
        "HTML_LABEL" to Plantilla(TipoHallazgo.SUGERENCIA, CategoriaConcepto.HTML_ESTRUCTURA, { "Campo de formulario sin etiqueta" },
            { "El campo no tiene un <label> asociado ni aria-label." },
            "Asocia un <label for=\"id\"> al campo.", "<input id=\"correo\">", "<label for=\"correo\">Correo</label>\n<input id=\"correo\">")
    )

    fun construir(crudo: HallazgoCrudo): Hallazgo? {
        val p = plantillas[crudo.regla] ?: return null
        val partes = if (crudo.extra.isEmpty()) emptyList() else crudo.extra.split('|')
        return Hallazgo(crudo.regla, p.tipo, crudo.linea, crudo.columna, p.titulo(partes), p.detalle(partes), p.recomendacion, p.antes, p.despues, p.categoria)
    }

    fun confirmadoDesdeSintaxis(linea: Int, columna: Int, titulo: String, detalle: String, recomendacion: String, antes: String, despues: String, categoria: CategoriaConcepto) =
        Hallazgo("SINTAXIS", TipoHallazgo.CONFIRMADO, linea, columna, titulo, detalle, recomendacion, antes, despues, categoria)
}

object AnalisisGeneral {
    fun duplicados(codigo: String, tamanoBloque: Int = 4): List<HallazgoCrudo> {
        val lineas = codigo.lines()
        val normalizadas = lineas.map { it.trim() }
        val vistos = HashMap<String, Int>()
        val resultado = mutableListOf<HallazgoCrudo>()
        var i = 0
        while (i + tamanoBloque <= normalizadas.size) {
            val bloque = normalizadas.subList(i, i + tamanoBloque)
            val significativas = bloque.count { it.length > 3 && it !in setOf("}", "{", "};", "]", ")", "else:", "} else {") }
            if (significativas < tamanoBloque - 1) { i++; continue }
            val clave = bloque.joinToString("\n")
            val previo = vistos[clave]
            if (previo != null && i >= previo + tamanoBloque) {
                resultado += HallazgoCrudo("CODIGO_DUPLICADO", i + 1, 1, (previo + 1).toString())
                i += tamanoBloque
                continue
            }
            if (previo == null) vistos[clave] = i
            i++
        }
        return resultado
    }

    fun lineasLargas(codigo: String, limite: Int = 120): List<HallazgoCrudo> =
        codigo.lines().mapIndexedNotNull { indice, linea ->
            if (linea.length > limite) HallazgoCrudo("LINEA_LARGA", indice + 1, limite, linea.length.toString()) else null
        }.take(5)
}

object RevisorCss {
    fun revisar(codigo: String): List<HallazgoCrudo> {
        val resultado = AnalizadorCss(codigo).analizar()
        val hallazgos = mutableListOf<HallazgoCrudo>()
        val porSelector = resultado.hoja.reglas.filter { it.contexto == null && !it.selector.startsWith("@") }.groupBy { it.selector }
        porSelector.filter { it.value.size > 1 && it.key.isNotBlank() }.forEach { (selector, reglas) ->
            val (l, c) = posicion(codigo, reglas[1].desplazamiento)
            hallazgos += HallazgoCrudo("CSS_SELECTOR_REPETIDO", l, c, selector)
        }
        resultado.hoja.reglas.forEach { regla ->
            if (regla.declaraciones.isEmpty() && regla.selector.isNotBlank()) {
                val (l, c) = posicion(codigo, regla.desplazamiento)
                hallazgos += HallazgoCrudo("CSS_REGLA_VACIA", l, c, regla.selector)
            }
            regla.declaraciones.groupBy { it.propiedad }.filter { it.value.size > 1 }.forEach { (propiedad, lista) ->
                val (l, c) = posicion(codigo, lista[1].desplazamiento)
                hallazgos += HallazgoCrudo("CSS_PROPIEDAD_REPETIDA", l, c, propiedad)
            }
        }
        val importantes = resultado.hoja.reglas.flatMap { it.declaraciones }.filter { it.importante }
        if (importantes.size >= 3) {
            val (l, c) = posicion(codigo, importantes.first().desplazamiento)
            hallazgos += HallazgoCrudo("CSS_IMPORTANT", l, c, importantes.size.toString())
        }
        return hallazgos
    }

    private fun posicion(codigo: String, desplazamiento: Int) = pe.redactcode.nucleo.modelo.PosicionTexto.desdeDesplazamiento(codigo, desplazamiento)
}

object RevisorHtml {
    fun revisar(codigo: String): List<HallazgoCrudo> {
        if (codigo.isBlank()) return emptyList()
        val documento = AnalizadorHtml.analizar(codigo).documento
        val hallazgos = mutableListOf<HallazgoCrudo>()
        fun posicion(desplazamiento: Int) = pe.redactcode.nucleo.modelo.PosicionTexto.desdeDesplazamiento(codigo, desplazamiento)
        val esCompleto = codigo.contains("<html", ignoreCase = true)
        if (esCompleto) {
            val html = documento.selectFirst("html")
            if (html != null && !html.hasAttr("lang")) {
                val (l, c) = posicion(html.sourceRange().startPos().coerceAtLeast(0))
                hallazgos += HallazgoCrudo("HTML_LANG", l, c)
            }
            if (documento.selectFirst("title") == null) hallazgos += HallazgoCrudo("HTML_TITLE", 1, 1)
        }
        documento.select("img:not([alt])").forEach {
            val (l, c) = posicion(it.sourceRange().startPos().coerceAtLeast(0))
            hallazgos += HallazgoCrudo("HTML_IMG_ALT", l, c)
        }
        documento.select("a[target=_blank]:not([rel~=noopener])").forEach {
            val (l, c) = posicion(it.sourceRange().startPos().coerceAtLeast(0))
            hallazgos += HallazgoCrudo("HTML_BLANK_NOOPENER", l, c)
        }
        documento.allElements.forEach { elemento ->
            elemento.attributes().asList().firstOrNull { it.key.startsWith("on") }?.let { atributo ->
                val (l, c) = posicion(elemento.sourceRange().startPos().coerceAtLeast(0))
                hallazgos += HallazgoCrudo("HTML_EVENTO_EN_LINEA", l, c, atributo.key)
            }
        }
        documento.select("[id]").groupBy { it.id() }.filter { it.value.size > 1 && it.key.isNotBlank() }.forEach { (id, elementos) ->
            val (l, c) = posicion(elementos[1].sourceRange().startPos().coerceAtLeast(0))
            hallazgos += HallazgoCrudo("HTML_ID_DUPLICADO", l, c, id)
        }
        documento.select("input:not([type=hidden]):not([type=submit]):not([type=button]), textarea, select").forEach { campo ->
            val id = campo.id()
            val tieneEtiqueta = (id.isNotBlank() && documento.select("label[for=$id]").isNotEmpty()) ||
                campo.hasAttr("aria-label") || campo.parents().any { it.normalName() == "label" }
            if (!tieneEtiqueta) {
                val (l, c) = posicion(campo.sourceRange().startPos().coerceAtLeast(0))
                hallazgos += HallazgoCrudo("HTML_LABEL", l, c)
            }
        }
        return hallazgos
    }
}

fun interface FuenteHallazgos {
    suspend fun obtener(codigo: String): List<HallazgoCrudo>
}

class RevisorCodigo(
    private val revisorJavaScript: FuenteHallazgos,
    private val revisorPython: FuenteHallazgos?
) {
    suspend fun revisar(lenguaje: Lenguaje, codigo: String, diagnosticosSintaxis: List<pe.redactcode.nucleo.modelo.Diagnostico>): InformeRevision {
        val herramientas = mutableListOf<String>()
        val crudos = mutableListOf<HallazgoCrudo>()
        when (lenguaje) {
            Lenguaje.JAVASCRIPT -> { crudos += revisorJavaScript.obtener(codigo); herramientas += "Acorn (AST de JavaScript)" }
            Lenguaje.PYTHON -> revisorPython?.let { crudos += it.obtener(codigo); herramientas += "Módulos ast y symtable de CPython" }
            Lenguaje.CSS -> { crudos += RevisorCss.revisar(codigo); herramientas += "Analizador CSS de Redact Code" }
            Lenguaje.HTML -> { crudos += RevisorHtml.revisar(codigo); herramientas += "jsoup (DOM HTML5)" }
            else -> Unit
        }
        if (lenguaje != Lenguaje.JSON && lenguaje != Lenguaje.TEXTO) {
            crudos += AnalisisGeneral.duplicados(codigo)
            crudos += AnalisisGeneral.lineasLargas(codigo)
            herramientas += "Detector de duplicados por bloques normalizados"
        }
        val sintaxis = diagnosticosSintaxis.filter { it.severidad == Severidad.ERROR }.map {
            ReglasRevision.confirmadoDesdeSintaxis(
                it.linea, it.columna, it.titulo, it.explicacion?.explicacion ?: it.mensajeOriginal,
                it.explicacion?.recomendacion ?: "", it.explicacion?.ejemploIncorrecto ?: "", it.explicacion?.ejemploCorregido ?: "", it.categoria
            )
        }
        val hallazgos = sintaxis + crudos.mapNotNull { ReglasRevision.construir(it) }
            .distinctBy { Triple(it.regla, it.linea, it.titulo) }
            .sortedWith(compareBy({ it.tipo.ordinal }, { it.linea }))
        return InformeRevision(lenguaje, hallazgos, sintaxis.size, herramientas)
    }
}
