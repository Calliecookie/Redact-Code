package pe.redactcode.nucleo.extensiones

object FragmentosBase {
    const val ID = "redactcode.fragmentos-base"

    private fun f(nombre: String, prefijo: String, lenguaje: String, descripcion: String, vararg lineas: String) =
        FragmentoExtension(nombre, prefijo, lineas.joinToString("\n"), descripcion, lenguaje)

    val manifiesto = ManifiestoExtension(
        id = ID,
        nombre = "Fragmentos base de Redact Code",
        version = "2.0.0",
        autor = "Redact Code",
        descripcion = "Fragmentos frecuentes para Python, JavaScript, HTML, CSS y JSON.",
        licencia = "MIT",
        fragmentos = listOf(
            f("Función", "def", "PYTHON", "Define una función", "def \${1:nombre}(\${2:parametros}):", "\t\${0:pass}"),
            f("Clase", "class", "PYTHON", "Define una clase con constructor", "class \${1:Nombre}:", "\tdef __init__(self\${2:, parametros}):", "\t\t\${0:pass}"),
            f("Bucle for", "for", "PYTHON", "Recorre un rango", "for \${1:i} in range(\${2:10}):", "\t\${0:pass}"),
            f("Condicional", "if", "PYTHON", "Bloque if/else", "if \${1:condicion}:", "\t\${2:pass}", "else:", "\t\${0:pass}"),
            f("Programa principal", "main", "PYTHON", "Punto de entrada", "def main():", "\t\${0:pass}", "", "", "if __name__ == \"__main__\":", "\tmain()"),
            f("Try/except", "try", "PYTHON", "Manejo de errores", "try:", "\t\${1:pass}", "except \${2:Exception} as error:", "\t\${0:print(error)}"),
            f("Función", "fn", "JAVASCRIPT", "Declaración de función", "function \${1:nombre}(\${2:parametros}) {", "\t\${0}", "}"),
            f("Función flecha", "arrow", "JAVASCRIPT", "Función flecha constante", "const \${1:nombre} = (\${2:parametros}) => {", "\t\${0}", "};"),
            f("Bucle for", "for", "JAVASCRIPT", "Bucle for clásico", "for (let \${1:i} = 0; \$1 < \${2:limite}; \$1++) {", "\t\${0}", "}"),
            f("Bucle for…of", "forof", "JAVASCRIPT", "Recorre un iterable", "for (const \${1:elemento} of \${2:lista}) {", "\t\${0}", "}"),
            f("console.log", "log", "JAVASCRIPT", "Muestra un valor en la consola", "console.log(\${0});"),
            f("Clase", "class", "JAVASCRIPT", "Clase con constructor", "class \${1:Nombre} {", "\tconstructor(\${2}) {", "\t\t\${0}", "\t}", "}"),
            f("Evento", "evento", "JAVASCRIPT", "Escucha un evento del DOM", "document.querySelector(\"\${1:#id}\").addEventListener(\"\${2:click}\", (evento) => {", "\t\${0}", "});"),
            f("Documento HTML5", "html5", "HTML", "Estructura base de una página", "<!DOCTYPE html>", "<html lang=\"es-PE\">", "<head>", "\t<meta charset=\"UTF-8\">", "\t<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">", "\t<title>\${1:Mi página}</title>", "\t<link rel=\"stylesheet\" href=\"estilos.css\">", "</head>", "<body>", "\t\${0}", "\t<script src=\"app.js\"></script>", "</body>", "</html>"),
            f("Enlace", "a", "HTML", "Enlace", "<a href=\"\${1:#}\">\${0:texto}</a>"),
            f("Imagen", "img", "HTML", "Imagen con texto alternativo", "<img src=\"\${1:imagen.png}\" alt=\"\${0:descripción}\">"),
            f("Formulario", "form", "HTML", "Formulario con etiqueta y botón", "<form>", "\t<label for=\"\${1:campo}\">\${2:Etiqueta}</label>", "\t<input id=\"\$1\" name=\"\$1\" type=\"\${3:text}\">", "\t<button type=\"submit\">\${0:Enviar}</button>", "</form>"),
            f("Media query", "media", "CSS", "Regla para pantallas pequeñas", "@media (max-width: \${1:600px}) {", "\t\${0}", "}"),
            f("Flexbox", "flex", "CSS", "Contenedor flexible centrado", "display: flex;", "align-items: \${1:center};", "justify-content: \${2:center};", "gap: \${0:8px};"),
            f("Grid", "grid", "CSS", "Rejilla de columnas", "display: grid;", "grid-template-columns: repeat(\${1:3}, 1fr);", "gap: \${0:16px};"),
            f("Objeto", "obj", "JSON", "Objeto con una clave", "{", "\t\"\${1:clave}\": \${0:\"valor\"}", "}")
        )
    )
}
