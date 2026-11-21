package pe.redactcode.nucleo.terminal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class EntradaRegistro(
    val fecha: Long,
    val origen: String,
    val comando: String,
    val riesgo: String,
    val codigo: Int,
    val salida: String
)

class RegistroEjecuciones(private val archivo: File, private val maximoBytes: Long = 512 * 1024, private val maximoSalida: Int = 4000) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun registrar(fecha: Long, origen: String, comando: String, riesgo: NivelRiesgo, codigo: Int, salida: String): EntradaRegistro {
        val entrada = EntradaRegistro(
            fecha, origen, CensorSecretos.censurar(comando), riesgo.name, codigo,
            CensorSecretos.censurar(salida).take(maximoSalida)
        )
        archivo.parentFile?.mkdirs()
        archivo.appendText(json.encodeToString(EntradaRegistro.serializer(), entrada) + "\n")
        if (archivo.length() > maximoBytes) recortar()
        return entrada
    }

    @Synchronized
    fun leer(limite: Int = 50): List<EntradaRegistro> {
        if (!archivo.isFile) return emptyList()
        return archivo.readLines().takeLast(limite).mapNotNull { runCatching { json.decodeFromString(EntradaRegistro.serializer(), it) }.getOrNull() }.reversed()
    }

    @Synchronized
    fun borrar() { archivo.delete() }

    private fun recortar() {
        val lineas = archivo.readLines()
        archivo.writeText(lineas.takeLast(lineas.size / 2).joinToString("\n", postfix = "\n"))
    }
}
