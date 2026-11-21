package pe.redactcode.nucleo.mentor

import kotlinx.serialization.Serializable
import pe.redactcode.nucleo.modelo.CategoriaConcepto
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.NivelExperiencia
import pe.redactcode.nucleo.validacion.AnalizadorCss
import pe.redactcode.nucleo.validacion.AnalizadorHtml
import pe.redactcode.nucleo.validacion.AnalizadorJson
import pe.redactcode.nucleo.validacion.ErrorAnalisisJson
import pe.redactcode.nucleo.validacion.NodoJson
import pe.redactcode.nucleo.modelo.PosicionTexto

@Serializable
data class ElementoLinea(
    val linea: Int,
    val nivel: Int = 0,
    val tipo: String,
    val nombre: String = "",
    val cantidad: Int = 0,
    val extra: String = "",
    val valor: String = "",
    val operador: String = ""
)

@Serializable
data class EstructuraCodigo(val error: String? = null, val lineas: List<ElementoLinea> = emptyList())

data class LineaExplicada(val linea: Int, val nivel: Int, val codigo: String, val explicacion: String, val concepto: CategoriaConcepto?)

object ExplicadorLineas {
    private val nombresValor = mapOf(
        "numero" to "un número", "cadena" to "un texto", "booleano" to "un valor lógico (verdadero o falso)",
        "nulo" to "un valor vacío", "lista" to "una lista", "objeto" to "un objeto", "diccionario" to "un diccionario",
        "tupla" to "una tupla", "conjunto" to "un conjunto", "funcion" to "una función", "llamada" to "el resultado de una llamada",
        "instancia" to "una nueva instancia", "operacion" to "el resultado de una operación", "expresion" to "el resultado de una expresión",
        "sin_valor" to "ningún valor inicial", "literal" to "un valor literal"
    )

    fun explicar(elementos: List<ElementoLinea>, codigo: String, lenguaje: Lenguaje, nivel: NivelExperiencia): List<LineaExplicada> {
        val lineas = codigo.lines()
        return elementos.sortedBy { it.linea }.map { e ->
            LineaExplicada(e.linea, e.nivel, lineas.getOrElse(e.linea - 1) { "" }.trim(), describir(e, lenguaje, nivel), concepto(e.tipo))
        }
    }

    fun concepto(tipo: String): CategoriaConcepto? = when (tipo) {
        "declaracion_variable", "asignacion", "incremento" -> CategoriaConcepto.VARIABLES
        "condicional", "alternativa", "seleccion" -> CategoriaConcepto.CONDICIONALES
        "bucle_for", "bucle_por_cada", "bucle_por_clave", "bucle_while", "romper", "continuar" -> CategoriaConcepto.BUCLES
        "funcion", "metodo", "retorno", "llamada" -> CategoriaConcepto.FUNCIONES
        "clase" -> CategoriaConcepto.OBJETOS
        "intento", "captura", "lanzar" -> CategoriaConcepto.EXCEPCIONES
        "elemento_html" -> CategoriaConcepto.HTML_ESTRUCTURA
        "regla_css", "declaracion_css" -> CategoriaConcepto.CSS_ESTILOS
        "json_objeto", "json_arreglo", "json_valor" -> CategoriaConcepto.ESTRUCTURAS_DATOS
        else -> null
    }

    private fun valor(clave: String) = nombresValor[clave] ?: "un valor"

    fun describir(e: ElementoLinea, lenguaje: Lenguaje, nivel: NivelExperiencia): String {
        val basico = nivel == NivelExperiencia.PRINCIPIANTE
        val avanzado = nivel == NivelExperiencia.AVANZADO
        return when (e.tipo) {
            "declaracion_variable" -> {
                val tipoDecl = when (e.extra) {
                    "const" -> if (basico) "una constante (su valor no se podrá reasignar)" else "una constante"
                    "let" -> "una variable con alcance de bloque"
                    "var" -> "una variable con var (alcance de función)"
                    else -> "una variable"
                }
                if (avanzado) "Declara ${e.nombre} = ${valor(e.valor)}."
                else "Crea $tipoDecl llamada '${e.nombre}' y le guarda ${valor(e.valor)}." +
                    if (basico) " Una variable es un espacio con nombre donde se guarda un dato para usarlo después." else ""
            }
            "asignacion" -> when (e.operador) {
                "=", "" -> "Guarda un nuevo valor en '${e.nombre}'" + if (basico) ", reemplazando el que tenía antes." else "."
                "+=" -> "Suma un valor a '${e.nombre}' y guarda el resultado en la misma variable."
                "-=" -> "Resta un valor a '${e.nombre}' y guarda el resultado."
                "*=" -> "Multiplica '${e.nombre}' por un valor y guarda el resultado."
                "/=" -> "Divide '${e.nombre}' entre un valor y guarda el resultado."
                else -> "Actualiza '${e.nombre}' usando el operador ${e.operador}."
            }
            "incremento" -> if (e.operador.contains("+")) "Aumenta en 1 el valor de '${e.nombre}'." else "Disminuye en 1 el valor de '${e.nombre}'."
            "funcion" -> {
                val parametros = if (e.cantidad == 0) "sin parámetros" else if (e.cantidad == 1) "con 1 parámetro" else "con ${e.cantidad} parámetros"
                val asincrona = if (e.extra == "async") " asíncrona" else ""
                "Define la función$asincrona '${e.nombre}' $parametros." +
                    if (basico) " Una función agrupa instrucciones para reutilizarlas llamándola por su nombre. Las líneas con sangría debajo forman su cuerpo." else ""
            }
            "metodo" -> "Define el método '${e.nombre}' de la clase" + if (e.cantidad > 0) " con ${e.cantidad} parámetro(s)." else "."
            "clase" -> "Define la clase '${e.nombre}'" + (if (e.extra.isNotBlank()) ", que hereda de ${e.extra}." else ".") +
                if (basico) " Una clase es un molde para crear objetos con datos y comportamientos." else ""
            "retorno" -> if (e.valor == "sin_valor") "Termina la función sin devolver un valor." else "Termina la función y devuelve ${valor(e.valor)} a quien la llamó."
            "condicional" -> if (basico) "Evalúa una condición. Si es verdadera, se ejecutan las líneas del bloque que sigue; si no, se saltan." else "Bifurcación condicional (if)."
            "alternativa" -> "Bloque alternativo (else): se ejecuta cuando la condición anterior fue falsa."
            "bucle_for" -> if (basico) "Inicia un bucle que repite el bloque mientras se cumpla su condición, actualizando un contador en cada vuelta." else "Bucle for con inicialización, condición y actualización."
            "bucle_por_cada" -> "Recorre cada elemento" + (if (e.extra.isNotBlank()) " de '${e.extra}'" else "") + " guardándolo en '${e.nombre}' en cada vuelta."
            "bucle_por_clave" -> "Recorre cada clave" + (if (e.extra.isNotBlank()) " de '${e.extra}'" else "") + " usando '${e.nombre}'."
            "bucle_while" -> if (basico) "Repite el bloque mientras la condición sea verdadera. Asegúrate de que algo cambie dentro para que termine." else "Bucle while."
            "seleccion" -> "Compara un valor contra ${e.cantidad} casos posibles (switch/match)."
            "intento" -> if (basico) "Intenta ejecutar el bloque; si ocurre un error, el programa no se detiene y pasa al bloque de manejo." else "Bloque try."
            "captura" -> "Maneja el error que pudo ocurrir en el bloque anterior."
            "lanzar" -> "Lanza un error de forma intencional para avisar que algo salió mal."
            "romper" -> "Sale inmediatamente del bucle actual."
            "continuar" -> "Salta al siguiente ciclo del bucle sin ejecutar el resto del bloque."
            "importacion" -> "Importa '${e.nombre}' para usar sus funciones en este archivo."
            "llamada" -> describirLlamada(e, lenguaje, basico)
            "espera" -> "Espera a que termine una operación asíncrona antes de continuar."
            "pasar" -> "No hace nada; se usa como marcador en un bloque vacío."
            "elemento_html" -> describirHtml(e, basico)
            "regla_css" -> "Regla de estilos para '${e.nombre}'" + if (e.cantidad > 0) " con ${e.cantidad} declaración(es)." else "."
            "declaracion_css" -> "Asigna a la propiedad '${e.nombre}' el valor ${e.valor}."
            "json_objeto" -> "Objeto JSON" + (if (e.nombre.isNotBlank()) " en la clave '${e.nombre}'" else "") + " con ${e.cantidad} propiedad(es)."
            "json_arreglo" -> "Arreglo JSON" + (if (e.nombre.isNotBlank()) " en la clave '${e.nombre}'" else "") + " con ${e.cantidad} elemento(s)."
            "json_valor" -> "La clave '${e.nombre}' guarda ${valor(e.valor)}."
            else -> "Evalúa una expresión."
        }
    }

    private fun describirLlamada(e: ElementoLinea, lenguaje: Lenguaje, basico: Boolean): String {
        val nombre = e.nombre
        return when {
            nombre == "print" || nombre == "console.log" -> "Muestra un mensaje en la salida" + if (basico) " (la consola)." else "."
            nombre == "input" -> "Pide un dato al usuario y lo recibe como texto."
            nombre.endsWith(".append") || nombre.endsWith(".push") -> "Agrega un elemento al final de la lista '${nombre.substringBeforeLast('.')}'."
            nombre.endsWith(".addEventListener") -> "Registra una función que se ejecutará cuando ocurra un evento en '${nombre.substringBeforeLast('.')}'."
            nombre.startsWith("document.") -> "Usa el documento de la página: ${nombre.substringAfter('.')}()."
            else -> "Llama a la función '$nombre'" + when (e.cantidad) {
                0 -> " sin argumentos."
                1 -> " con 1 argumento."
                else -> " con ${e.cantidad} argumentos."
            }
        }
    }

    private fun describirHtml(e: ElementoLinea, basico: Boolean): String {
        val significado = mapOf(
            "html" to "la raíz del documento", "head" to "la cabecera con información de la página", "body" to "el contenido visible",
            "title" to "el título que aparece en la pestaña", "meta" to "metadatos de la página", "link" to "un recurso externo, como una hoja de estilos",
            "script" to "código JavaScript", "style" to "estilos CSS internos", "h1" to "el título principal", "h2" to "un subtítulo",
            "h3" to "un subtítulo de tercer nivel", "p" to "un párrafo", "a" to "un enlace", "img" to "una imagen", "ul" to "una lista sin orden",
            "ol" to "una lista numerada", "li" to "un elemento de lista", "div" to "un contenedor genérico", "span" to "un contenedor en línea",
            "form" to "un formulario", "input" to "un campo de entrada", "button" to "un botón", "label" to "la etiqueta de un campo",
            "nav" to "la navegación", "header" to "el encabezado", "footer" to "el pie de página", "main" to "el contenido principal",
            "section" to "una sección", "article" to "un artículo", "table" to "una tabla", "tr" to "una fila", "td" to "una celda",
            "select" to "una lista desplegable", "textarea" to "un área de texto"
        )
        val descripcion = significado[e.nombre] ?: "un elemento <${e.nombre}>"
        return "Elemento <${e.nombre}>: $descripcion" + (if (e.extra.isNotBlank()) " (${e.extra})" else "") + "." +
            if (basico && e.nivel == 0) " Las etiquetas HTML describen la estructura del contenido." else ""
    }

    fun estructuraHtml(codigo: String): EstructuraCodigo {
        val documento = AnalizadorHtml.analizar(codigo).documento
        val elementos = documento.allElements.filter { it.sourceRange().isTracked && !it.sourceRange().isImplicit }.map { el ->
            val (l, _) = PosicionTexto.desdeDesplazamiento(codigo, el.sourceRange().startPos())
            val extra = listOfNotNull(
                el.id().takeIf { it.isNotBlank() }?.let { "id=$it" },
                el.className().takeIf { it.isNotBlank() }?.let { "clase=$it" },
                el.attr("href").takeIf { it.isNotBlank() }?.let { "destino=$it" },
                el.attr("type").takeIf { it.isNotBlank() }?.let { "tipo=$it" }
            ).joinToString(", ")
            ElementoLinea(l, el.parents().size.coerceAtMost(6), "elemento_html", el.normalName(), extra = extra)
        }
        return EstructuraCodigo(null, elementos.distinctBy { it.linea })
    }

    fun estructuraCss(codigo: String): EstructuraCodigo {
        val resultado = AnalizadorCss(codigo).analizar()
        val elementos = mutableListOf<ElementoLinea>()
        resultado.hoja.reglas.forEach { regla ->
            elementos += ElementoLinea(PosicionTexto.desdeDesplazamiento(codigo, regla.desplazamiento).first, 0, "regla_css", regla.selector, regla.declaraciones.size)
            regla.declaraciones.forEach { d ->
                elementos += ElementoLinea(PosicionTexto.desdeDesplazamiento(codigo, d.desplazamiento).first, 1, "declaracion_css", d.propiedad, valor = d.valor)
            }
        }
        return EstructuraCodigo(null, elementos.distinctBy { it.linea })
    }

    fun estructuraJson(codigo: String): EstructuraCodigo {
        val raiz = try { AnalizadorJson(codigo).analizar() } catch (e: ErrorAnalisisJson) { return EstructuraCodigo(e.message, emptyList()) }
        val elementos = mutableListOf<ElementoLinea>()
        fun visitar(nodo: NodoJson, clave: String, nivel: Int) {
            val linea = PosicionTexto.desdeDesplazamiento(codigo, nodo.inicio).first
            when (nodo) {
                is NodoJson.Objeto -> {
                    elementos += ElementoLinea(linea, nivel, "json_objeto", clave, nodo.miembros.size)
                    nodo.miembros.forEach { (k, v) -> visitar(v, k, nivel + 1) }
                }
                is NodoJson.Arreglo -> {
                    elementos += ElementoLinea(linea, nivel, "json_arreglo", clave, nodo.elementos.size)
                    nodo.elementos.forEach { visitar(it, "", nivel + 1) }
                }
                is NodoJson.Cadena -> if (clave.isNotBlank()) elementos += ElementoLinea(linea, nivel, "json_valor", clave, valor = "cadena")
                is NodoJson.Numero -> if (clave.isNotBlank()) elementos += ElementoLinea(linea, nivel, "json_valor", clave, valor = "numero")
                is NodoJson.Logico -> if (clave.isNotBlank()) elementos += ElementoLinea(linea, nivel, "json_valor", clave, valor = "booleano")
                is NodoJson.Nulo -> if (clave.isNotBlank()) elementos += ElementoLinea(linea, nivel, "json_valor", clave, valor = "nulo")
            }
        }
        visitar(raiz, "", 0)
        return EstructuraCodigo(null, elementos.distinctBy { it.linea })
    }
}
