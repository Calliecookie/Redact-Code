package pe.redactcode.nucleo.mentor

import kotlinx.serialization.Serializable
import pe.redactcode.nucleo.modelo.CategoriaConcepto
import pe.redactcode.nucleo.modelo.Diagnostico
import pe.redactcode.nucleo.modelo.Lenguaje
import pe.redactcode.nucleo.modelo.NivelExperiencia
import pe.redactcode.nucleo.retos.CatalogoRetos
import pe.redactcode.nucleo.retos.DefinicionReto
import pe.redactcode.nucleo.revision.Hallazgo
import pe.redactcode.nucleo.revision.TipoHallazgo
import pe.redactcode.nucleo.validacion.LectorRecursos

enum class TipoSolicitud(val nombreVisible: String) {
    EXPLICAR_CODIGO("Explicar código"),
    LINEA_POR_LINEA("Explicar línea por línea"),
    EXPLICAR_ERROR("Explicar error"),
    SUGERIR_CORRECCION("Sugerir corrección"),
    EXPLICAR_CONCEPTO("Explicar concepto"),
    PROPONER_EJERCICIO("Proponer ejercicio"),
    RECOMENDAR_MEJORAS("Recomendar mejoras"),
    MALAS_PRACTICAS("Identificar malas prácticas")
}

data class ContextoAprendizaje(
    val nivel: NivelExperiencia,
    val lenguajesUsados: List<Lenguaje>,
    val retosCompletados: Int,
    val conceptosDominados: List<CategoriaConcepto>,
    val conceptosPendientes: List<CategoriaConcepto>,
    val erroresRecurrentes: Map<String, Int>,
    val retosResueltos: Set<String> = emptySet()
)

data class SolicitudMentor(
    val tipo: TipoSolicitud,
    val lenguaje: Lenguaje,
    val codigo: String,
    val contexto: ContextoAprendizaje,
    val diagnostico: Diagnostico? = null,
    val concepto: CategoriaConcepto? = null,
    val hallazgos: List<Hallazgo> = emptyList(),
    val pregunta: String = ""
)

data class SeccionRespuesta(val titulo: String, val texto: String, val codigo: String? = null)

data class PropuestaCorreccion(val linea: Int, val original: String, val corregida: String, val descripcion: String)

data class RespuestaMentor(
    val titulo: String,
    val secciones: List<SeccionRespuesta>,
    val proveedor: String,
    val esLocal: Boolean,
    val lineas: List<LineaExplicada> = emptyList(),
    val propuesta: PropuestaCorreccion? = null,
    val retoSugerido: DefinicionReto? = null,
    val aviso: String? = null
)

interface ProveedorIA {
    val id: String
    val nombre: String
    val requiereRed: Boolean
    suspend fun disponible(): Boolean
    suspend fun responder(solicitud: SolicitudMentor): RespuestaMentor
}

fun interface AnalizadorEstructura {
    suspend fun estructura(lenguaje: Lenguaje, codigo: String): EstructuraCodigo
}

@Serializable
data class Concepto(
    val categoria: CategoriaConcepto,
    val titulo: String,
    val principiante: String,
    val intermedio: String,
    val avanzado: String,
    val ejemplos: Map<String, String> = emptyMap(),
    val erroresComunes: List<String> = emptyList()
) {
    fun explicacion(nivel: NivelExperiencia) = when (nivel) {
        NivelExperiencia.PRINCIPIANTE -> principiante
        NivelExperiencia.INTERMEDIO -> intermedio
        NivelExperiencia.AVANZADO -> avanzado
    }

    fun ejemplo(lenguaje: Lenguaje): String? = ejemplos[lenguaje.name.lowercase()] ?: ejemplos.values.firstOrNull()
}

object CatalogoConceptos {
    val conceptos: List<Concepto> by lazy {
        LectorRecursos.json.decodeFromString<List<Concepto>>(LectorRecursos.leerTexto("/pe/redactcode/nucleo/catalogos/conceptos.json"))
    }

    fun buscar(categoria: CategoriaConcepto): Concepto? = conceptos.firstOrNull { it.categoria == categoria }
}

object CorrectorAutomatico {
    fun proponer(diagnostico: Diagnostico, codigo: String): PropuestaCorreccion? {
        val lineas = codigo.lines()
        val indice = diagnostico.linea - 1
        val linea = lineas.getOrNull(indice) ?: return null
        val sinFinal = linea.trimEnd()
        return when (diagnostico.codigo) {
            "PY001" -> {
                val objetivo = buscarLineaBloque(lineas, indice) ?: return null
                val texto = lineas[objetivo].trimEnd()
                if (texto.endsWith(":")) null else PropuestaCorreccion(objetivo + 1, lineas[objetivo], "$texto:", "Agregar ':' al final de la línea que abre el bloque.")
            }
            "PY010" -> {
                val sangria = linea.takeWhile { it.isWhitespace() }
                val argumentos = linea.trim().removePrefix("print").trim()
                if (argumentos.isEmpty() || argumentos.startsWith("(")) null
                else PropuestaCorreccion(indice + 1, linea, "${sangria}print($argumentos)", "Encerrar los argumentos de print entre paréntesis.")
            }
            "PY007", "JS002" -> {
                val comilla = sinFinal.lastOrNull { it == '"' || it == '\'' } ?: '"'
                val terminador = if (diagnostico.lenguaje == Lenguaje.JAVASCRIPT && sinFinal.endsWith(";")) ";" else ""
                val base = sinFinal.removeSuffix(terminador)
                val cierre = cierreFaltante(base, comilla)
                PropuestaCorreccion(indice + 1, linea, base + comilla + cierre + terminador, "Cerrar la cadena con $comilla.")
            }
            "JSON001" -> {
                val ultimaComa = sinFinal.lastIndexOf(',')
                if (ultimaComa < 0) null else PropuestaCorreccion(indice + 1, linea, sinFinal.removeRange(ultimaComa, ultimaComa + 1), "Eliminar la coma sobrante.")
            }
            "JSON002" -> PropuestaCorreccion(indice + 1, linea, linea.replace('\'', '"'), "Reemplazar comillas simples por dobles.")
            "CSS004" -> {
                if (sinFinal.endsWith(";") || sinFinal.endsWith("{")) null
                else PropuestaCorreccion(indice + 1, linea, "$sinFinal;", "Agregar ';' al final de la declaración.")
            }
            "CSS003" -> {
                val sangria = linea.takeWhile { it.isWhitespace() }
                val contenido = linea.trim()
                val nombre = contenido.takeWhile { it.isLetterOrDigit() || it == '-' }
                if (nombre.isEmpty() || contenido.length == nombre.length) null
                else PropuestaCorreccion(indice + 1, linea, "$sangria$nombre: ${contenido.removePrefix(nombre).trim()}", "Insertar ':' entre la propiedad y el valor.")
            }
            "PY008", "JS006" -> {
                val posicion = linea.indexOf(" = ")
                if (posicion < 0 || !(linea.trimStart().startsWith("if") || linea.trimStart().startsWith("while") || linea.trimStart().startsWith("elif"))) null
                else PropuestaCorreccion(indice + 1, linea, linea.replaceFirst(" = ", if (diagnostico.lenguaje == Lenguaje.JAVASCRIPT) " === " else " == "), "Usar el operador de comparación en lugar de asignación.")
            }
            else -> null
        }
    }

    private fun buscarLineaBloque(lineas: List<String>, desde: Int): Int? {
        val palabras = listOf("if ", "elif ", "else", "for ", "while ", "def ", "class ", "try", "except", "finally", "with ")
        for (i in desde downTo maxOf(0, desde - 2)) {
            val t = lineas[i].trim()
            if (palabras.any { t.startsWith(it) }) return i
        }
        return null
    }

    private fun cierreFaltante(texto: String, comilla: Char): String {
        val pila = ArrayDeque<Char>()
        var dentro = false
        for (c in texto) {
            if (c == comilla) { dentro = !dentro; continue }
            if (dentro) continue
            when (c) {
                '(' -> pila.addLast(')')
                '[' -> pila.addLast(']')
                '{' -> pila.addLast('}')
                ')', ']', '}' -> if (pila.lastOrNull() == c) pila.removeLast()
            }
        }
        return pila.reversed().joinToString("")
    }
}

class MentorLocal(private val analizador: AnalizadorEstructura) : ProveedorIA {
    override val id = "local"
    override val nombre = "Mentor local de Redact Code"
    override val requiereRed = false

    override suspend fun disponible() = true

    override suspend fun responder(solicitud: SolicitudMentor): RespuestaMentor = when (solicitud.tipo) {
        TipoSolicitud.EXPLICAR_CODIGO -> explicarCodigo(solicitud)
        TipoSolicitud.LINEA_POR_LINEA -> lineaPorLinea(solicitud)
        TipoSolicitud.EXPLICAR_ERROR -> explicarError(solicitud)
        TipoSolicitud.SUGERIR_CORRECCION -> sugerirCorreccion(solicitud)
        TipoSolicitud.EXPLICAR_CONCEPTO -> explicarConcepto(solicitud)
        TipoSolicitud.PROPONER_EJERCICIO -> proponerEjercicio(solicitud)
        TipoSolicitud.RECOMENDAR_MEJORAS, TipoSolicitud.MALAS_PRACTICAS -> mejoras(solicitud)
    }

    private fun respuesta(titulo: String, secciones: List<SeccionRespuesta>, lineas: List<LineaExplicada> = emptyList(), propuesta: PropuestaCorreccion? = null, reto: DefinicionReto? = null, aviso: String? = null) =
        RespuestaMentor(titulo, secciones, nombre, true, lineas, propuesta, reto, aviso)

    private suspend fun lineasExplicadas(solicitud: SolicitudMentor): Pair<List<LineaExplicada>, String?> {
        if (solicitud.lenguaje == Lenguaje.TEXTO) return emptyList<LineaExplicada>() to "El texto plano no contiene código para analizar."
        val estructura = analizador.estructura(solicitud.lenguaje, solicitud.codigo)
        if (estructura.error != null && estructura.lineas.isEmpty()) {
            return emptyList<LineaExplicada>() to "No se pudo analizar el código porque tiene un error de sintaxis: ${estructura.error}. Corrígelo primero desde el panel de problemas."
        }
        return ExplicadorLineas.explicar(estructura.lineas, solicitud.codigo, solicitud.lenguaje, solicitud.contexto.nivel) to null
    }

    private suspend fun explicarCodigo(solicitud: SolicitudMentor): RespuestaMentor {
        val (lineas, problema) = lineasExplicadas(solicitud)
        if (problema != null) return respuesta("Explicación del código", listOf(SeccionRespuesta("No se pudo analizar", problema)))
        val conceptos = lineas.mapNotNull { it.concepto }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        val resumen = buildString {
            append("Este fragmento de ${solicitud.lenguaje.nombreVisible} tiene ${solicitud.codigo.lines().count { it.isNotBlank() }} líneas con contenido.")
            val funciones = lineas.filter { it.explicacion.startsWith("Define la función") }
            if (funciones.isNotEmpty()) append(" Define ${funciones.size} función(es).")
            if (conceptos.isNotEmpty()) append(" Usa principalmente: ${conceptos.take(4).joinToString(", ") { it.key.nombreVisible.lowercase() }}.")
        }
        val secciones = mutableListOf(SeccionRespuesta("Resumen", resumen))
        val principales = lineas.filter { it.nivel == 0 }.take(12)
        if (principales.isNotEmpty()) {
            secciones += SeccionRespuesta("Partes principales", principales.joinToString("\n") { "Línea ${it.linea}: ${it.explicacion}" })
        }
        val pendientes = conceptos.map { it.key }.filter { it in solicitud.contexto.conceptosPendientes }
        if (pendientes.isNotEmpty() && solicitud.contexto.nivel != NivelExperiencia.AVANZADO) {
            val concepto = CatalogoConceptos.buscar(pendientes.first())
            if (concepto != null) secciones += SeccionRespuesta("Concepto para reforzar: ${concepto.titulo}", concepto.explicacion(solicitud.contexto.nivel))
        }
        return respuesta("Explicación del código", secciones, lineas)
    }

    private suspend fun lineaPorLinea(solicitud: SolicitudMentor): RespuestaMentor {
        val (lineas, problema) = lineasExplicadas(solicitud)
        if (problema != null) return respuesta("Explicación línea por línea", listOf(SeccionRespuesta("No se pudo analizar", problema)))
        return respuesta("Explicación línea por línea", listOf(SeccionRespuesta("Cómo leerla", "Cada entrada corresponde a una instrucción. La sangría indica que la línea está dentro del bloque anterior.")), lineas)
    }

    private fun explicarError(solicitud: SolicitudMentor): RespuestaMentor {
        val d = solicitud.diagnostico ?: return respuesta("Explicar error", listOf(SeccionRespuesta("Sin error seleccionado", "Selecciona un problema en el panel de diagnósticos para explicarlo.")))
        val e = d.explicacion
        val nivel = solicitud.contexto.nivel
        val secciones = mutableListOf<SeccionRespuesta>()
        secciones += SeccionRespuesta("${d.tipo} en la línea ${d.linea}, columna ${d.columna}", e?.explicacion ?: d.mensajeOriginal)
        if (e != null) {
            secciones += SeccionRespuesta("Causa probable", e.causaProbable)
            if (nivel != NivelExperiencia.AVANZADO && e.ejemploIncorrecto.isNotBlank()) {
                secciones += SeccionRespuesta("Ejemplo incorrecto", "", e.ejemploIncorrecto)
                secciones += SeccionRespuesta("Ejemplo corregido", "", e.ejemploCorregido)
            }
            secciones += SeccionRespuesta("Recomendación", e.recomendacion)
        }
        if (nivel == NivelExperiencia.AVANZADO) secciones += SeccionRespuesta("Mensaje del analizador", d.mensajeOriginal)
        val repeticiones = solicitud.contexto.erroresRecurrentes[d.codigo] ?: 0
        if (repeticiones >= 2) {
            val concepto = CatalogoConceptos.buscar(d.categoria)
            secciones += SeccionRespuesta(
                "Patrón detectado",
                "Este tipo de error (${d.codigo}) apareció $repeticiones veces en tu historial." +
                    (concepto?.let { " Repasar «${it.titulo}» puede ayudarte a evitarlo." } ?: "")
            )
        }
        val propuesta = CorrectorAutomatico.proponer(d, solicitud.codigo)
        return respuesta(e?.titulo ?: "Explicación del error", secciones, propuesta = propuesta)
    }

    private fun sugerirCorreccion(solicitud: SolicitudMentor): RespuestaMentor {
        val d = solicitud.diagnostico ?: return respuesta("Sugerir corrección", listOf(SeccionRespuesta("Sin error seleccionado", "Selecciona un problema para proponer una corrección.")))
        val propuesta = CorrectorAutomatico.proponer(d, solicitud.codigo)
        val secciones = mutableListOf<SeccionRespuesta>()
        if (propuesta != null) {
            secciones += SeccionRespuesta("Cambio propuesto en la línea ${propuesta.linea}", propuesta.descripcion)
            secciones += SeccionRespuesta("Antes", "", propuesta.original.trim())
            secciones += SeccionRespuesta("Después", "", propuesta.corregida.trim())
            secciones += SeccionRespuesta("Verificación", "Al aplicar el cambio, Redact Code volverá a validar el archivo para confirmar si el error desaparece.")
        } else {
            secciones += SeccionRespuesta("Corrección guiada", d.explicacion?.recomendacion ?: "Revisa la línea indicada.")
            d.explicacion?.ejemploCorregido?.takeIf { it.isNotBlank() }?.let { secciones += SeccionRespuesta("Ejemplo de referencia", "", it) }
        }
        return respuesta("Sugerir corrección", secciones, propuesta = propuesta)
    }

    private fun explicarConcepto(solicitud: SolicitudMentor): RespuestaMentor {
        val categoria = solicitud.concepto ?: solicitud.contexto.conceptosPendientes.firstOrNull() ?: CategoriaConcepto.FUNDAMENTOS
        val concepto = CatalogoConceptos.buscar(categoria)
            ?: return respuesta("Concepto", listOf(SeccionRespuesta("Sin información", "No hay información local para este concepto.")))
        val secciones = mutableListOf(SeccionRespuesta("Qué es", concepto.explicacion(solicitud.contexto.nivel)))
        concepto.ejemplo(solicitud.lenguaje)?.let { secciones += SeccionRespuesta("Ejemplo", "", it) }
        if (concepto.erroresComunes.isNotEmpty()) secciones += SeccionRespuesta("Errores comunes", concepto.erroresComunes.joinToString("\n") { "• $it" })
        val reto = elegirReto(categoria, solicitud)
        return respuesta(concepto.titulo, secciones, reto = reto)
    }

    private fun proponerEjercicio(solicitud: SolicitudMentor): RespuestaMentor {
        val categoria = solicitud.concepto ?: solicitud.contexto.conceptosPendientes.firstOrNull()
        val reto = elegirReto(categoria, solicitud)
            ?: return respuesta("Ejercicio sugerido", listOf(SeccionRespuesta("¡Buen trabajo!", "Ya resolviste todos los retos disponibles para este tema. Prueba con un proyecto guiado.")))
        val motivo = when {
            categoria != null && categoria in solicitud.contexto.conceptosPendientes -> "Se eligió porque tu perfil muestra dificultades en ${categoria.nombreVisible.lowercase()}."
            else -> "Se eligió según tu nivel (${solicitud.contexto.nivel.nombreVisible.lowercase()}) y los retos que aún no resuelves."
        }
        return respuesta(
            "Ejercicio sugerido",
            listOf(SeccionRespuesta(reto.titulo, reto.enunciado), SeccionRespuesta("Por qué este ejercicio", motivo)),
            reto = reto
        )
    }

    private fun elegirReto(categoria: CategoriaConcepto?, solicitud: SolicitudMentor): DefinicionReto? {
        val maximo = when (solicitud.contexto.nivel) {
            NivelExperiencia.PRINCIPIANTE -> 1
            NivelExperiencia.INTERMEDIO -> 2
            NivelExperiencia.AVANZADO -> 3
        }
        val candidatos = CatalogoRetos.retos.filter { it.id !in solicitud.contexto.retosResueltos }
        val porCategoria = if (categoria != null) candidatos.filter { it.categoria == categoria } else candidatos
        val lenguajePreferido = solicitud.lenguaje.takeIf { it == Lenguaje.PYTHON || it == Lenguaje.JAVASCRIPT }
        return (porCategoria.ifEmpty { candidatos })
            .sortedWith(compareBy({ if (it.lenguaje == lenguajePreferido) 0 else 1 }, { if (it.dificultad.orden <= maximo) 0 else 1 }, { -it.dificultad.orden.coerceAtMost(maximo) }))
            .firstOrNull()
    }

    private fun mejoras(solicitud: SolicitudMentor): RespuestaMentor {
        val hallazgos = solicitud.hallazgos
        if (hallazgos.isEmpty()) {
            return respuesta("Revisión de calidad", listOf(SeccionRespuesta("Sin observaciones", "El revisor no encontró problemas con las reglas disponibles. Esto no garantiza que el código esté libre de errores lógicos.")))
        }
        val soloPracticas = solicitud.tipo == TipoSolicitud.MALAS_PRACTICAS
        val filtrados = if (soloPracticas) hallazgos.filter { it.tipo != TipoHallazgo.CONFIRMADO } else hallazgos
        val secciones = filtrados.take(10).map { h ->
            SeccionRespuesta("${h.tipo.nombreVisible} · línea ${h.linea}: ${h.titulo}", "${h.detalle}\n\nRecomendación: ${h.recomendacion}", h.ejemploDespues.takeIf { it.isNotBlank() })
        }
        return respuesta(
            if (soloPracticas) "Malas prácticas detectadas" else "Mejoras recomendadas",
            secciones.ifEmpty { listOf(SeccionRespuesta("Sin malas prácticas", "Solo se encontraron errores de sintaxis. Corrígelos y vuelve a revisar.")) },
            aviso = "Las advertencias provienen de reglas heurísticas y pueden no aplicar a tu caso."
        )
    }
}

object ConstructorPrompt {
    fun sistema(contexto: ContextoAprendizaje): String = buildString {
        appendLine("Eres el Mentor de Programación de Redact Code, una aplicación móvil para aprender a programar.")
        appendLine("Responde siempre en español de Perú, con un tono claro, respetuoso y profesional.")
        appendLine("Nivel del estudiante: ${contexto.nivel.nombreVisible}. Ajusta la profundidad: más ejemplos y analogías para principiantes, más precisión técnica para avanzados.")
        if (contexto.lenguajesUsados.isNotEmpty()) appendLine("Lenguajes que usa: ${contexto.lenguajesUsados.joinToString { it.nombreVisible }}.")
        if (contexto.conceptosDominados.isNotEmpty()) appendLine("Conceptos que domina: ${contexto.conceptosDominados.joinToString { it.nombreVisible }}.")
        if (contexto.conceptosPendientes.isNotEmpty()) appendLine("Conceptos con dificultad: ${contexto.conceptosPendientes.joinToString { it.nombreVisible }}.")
        if (contexto.erroresRecurrentes.isNotEmpty()) appendLine("Errores recurrentes (código: veces): ${contexto.erroresRecurrentes.entries.take(5).joinToString { "${it.key}: ${it.value}" }}.")
        appendLine("Formato: secciones breves con títulos en una línea que empiece con '## '. Coloca el código entre bloques ``` .")
        appendLine("No inventes resultados de ejecución. Si no estás seguro, dilo. Explica el porqué de cada recomendación.")
        append("Mantén la respuesta por debajo de 400 palabras salvo que se pida explicar línea por línea.")
    }

    fun usuario(solicitud: SolicitudMentor): String = buildString {
        appendLine("Tarea: ${solicitud.tipo.nombreVisible}.")
        appendLine("Lenguaje: ${solicitud.lenguaje.nombreVisible}.")
        solicitud.concepto?.let { appendLine("Concepto: ${it.nombreVisible}.") }
        solicitud.diagnostico?.let {
            appendLine("Error detectado por el analizador local: ${it.codigo} en la línea ${it.linea}, columna ${it.columna}: ${it.mensajeOriginal}.")
        }
        if (solicitud.hallazgos.isNotEmpty()) {
            appendLine("Hallazgos del revisor local:")
            solicitud.hallazgos.take(10).forEach { appendLine("- Línea ${it.linea}: ${it.titulo} (${it.tipo.nombreVisible})") }
        }
        if (solicitud.pregunta.isNotBlank()) appendLine("Pregunta del estudiante: ${solicitud.pregunta}")
        if (solicitud.codigo.isNotBlank()) {
            appendLine("Código:")
            appendLine("```${solicitud.lenguaje.name.lowercase()}")
            appendLine(solicitud.codigo)
            append("```")
        }
    }

    fun interpretar(texto: String): List<SeccionRespuesta> {
        val secciones = mutableListOf<SeccionRespuesta>()
        var titulo = "Respuesta"
        val cuerpo = StringBuilder()
        val codigo = StringBuilder()
        var enCodigo = false
        var codigoPendiente: String? = null
        fun cerrar() {
            val t = cuerpo.toString().trim()
            if (t.isNotEmpty() || codigoPendiente != null) secciones += SeccionRespuesta(titulo, t, codigoPendiente)
            cuerpo.clear()
            codigoPendiente = null
        }
        for (linea in texto.lines()) {
            when {
                linea.trimStart().startsWith("```") -> {
                    if (enCodigo) {
                        codigoPendiente = codigo.toString().trimEnd()
                        codigo.clear()
                        cerrar()
                    } else {
                        if (cuerpo.isNotBlank()) cerrar()
                    }
                    enCodigo = !enCodigo
                }
                enCodigo -> codigo.appendLine(linea)
                linea.startsWith("## ") || linea.startsWith("# ") -> {
                    cerrar()
                    titulo = linea.trimStart('#').trim()
                }
                else -> cuerpo.appendLine(linea)
            }
        }
        if (enCodigo) codigoPendiente = codigo.toString().trimEnd()
        cerrar()
        return secciones
    }
}

class MentorProgramacion(
    private val local: ProveedorIA,
    private val externo: ProveedorIA?,
    private val usarExterno: suspend () -> Boolean
) {
    suspend fun responder(solicitud: SolicitudMentor): RespuestaMentor {
        val proveedor = externo
        val tipoSoloLocal = solicitud.tipo == TipoSolicitud.LINEA_POR_LINEA || solicitud.tipo == TipoSolicitud.PROPONER_EJERCICIO
        if (proveedor != null && !tipoSoloLocal && usarExterno() && proveedor.disponible()) {
            return try {
                val externa = proveedor.responder(solicitud)
                val localComplemento = local.responder(solicitud)
                externa.copy(propuesta = externa.propuesta ?: localComplemento.propuesta, retoSugerido = externa.retoSugerido ?: localComplemento.retoSugerido)
            } catch (e: Exception) {
                local.responder(solicitud).copy(aviso = "No se pudo usar ${proveedor.nombre} (${e.message?.take(120)}). Se muestra la explicación local.")
            }
        }
        return local.responder(solicitud)
    }
}
