package pe.redactcode.aplicacion.nucleo.base_datos

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "perfil")
data class PerfilEntidad(
    @PrimaryKey val id: Long = 1,
    val nombre: String,
    val nivel: String,
    @ColumnInfo(name = "lenguajes_interes") val lenguajesInteres: String,
    @ColumnInfo(name = "objetivo_minutos_diarios") val objetivoMinutosDiarios: Int = 20,
    @ColumnInfo(name = "objetivo_retos_semanales") val objetivoRetosSemanales: Int = 5,
    @ColumnInfo(name = "creado_en") val creadoEn: Long
)

@Entity(tableName = "proyecto", indices = [Index(value = ["carpeta"], unique = true)])
data class ProyectoEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val descripcion: String = "",
    val carpeta: String,
    @ColumnInfo(name = "lenguaje_principal") val lenguajePrincipal: String,
    val origen: String = ORIGEN_LOCAL,
    @ColumnInfo(name = "proyecto_guiado_id") val proyectoGuiadoId: String? = null,
    @ColumnInfo(name = "paso_actual") val pasoActual: Int = 0,
    @ColumnInfo(name = "github_repositorio") val githubRepositorio: String? = null,
    @ColumnInfo(name = "github_rama") val githubRama: String? = null,
    @ColumnInfo(name = "creado_en") val creadoEn: Long,
    @ColumnInfo(name = "modificado_en") val modificadoEn: Long
) {
    companion object {
        const val ORIGEN_LOCAL = "LOCAL"
        const val ORIGEN_GUIADO = "GUIADO"
        const val ORIGEN_GITHUB = "GITHUB"
    }
}

@Entity(
    tableName = "archivo",
    foreignKeys = [ForeignKey(entity = ProyectoEntidad::class, parentColumns = ["id"], childColumns = ["proyecto_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["proyecto_id", "ruta_relativa"], unique = true)]
)
data class ArchivoEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "proyecto_id") val proyectoId: Long,
    @ColumnInfo(name = "ruta_relativa") val rutaRelativa: String,
    val lenguaje: String,
    @ColumnInfo(name = "abierto_en") val abiertoEn: Long? = null,
    @ColumnInfo(name = "modificado_en") val modificadoEn: Long,
    @ColumnInfo(name = "github_sha") val githubSha: String? = null
)

data class ProyectoConArchivos(
    @Embedded val proyecto: ProyectoEntidad,
    @Relation(parentColumn = "id", entityColumn = "proyecto_id") val archivos: List<ArchivoEntidad>
)

@Entity(tableName = "fragmento_codigo")
data class FragmentoCodigoEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val descripcion: String = "",
    val codigo: String,
    val lenguaje: String,
    val etiquetas: String = "",
    @ColumnInfo(name = "repaso_activo") val repasoActivo: Boolean = true,
    @ColumnInfo(name = "creado_en") val creadoEn: Long,
    @ColumnInfo(name = "modificado_en") val modificadoEn: Long
) {
    val listaEtiquetas: List<String> get() = etiquetas.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "repaso",
    foreignKeys = [ForeignKey(entity = FragmentoCodigoEntidad::class, parentColumns = ["id"], childColumns = ["fragmento_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["fragmento_id"], unique = true), Index(value = ["proxima_fecha"])]
)
data class RepasoEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "fragmento_id") val fragmentoId: Long,
    val repeticiones: Int = 0,
    @ColumnInfo(name = "intervalo_dias") val intervaloDias: Int = 0,
    @ColumnInfo(name = "factor_facilidad") val factorFacilidad: Double = 2.5,
    @ColumnInfo(name = "proxima_fecha") val proximaFecha: Long,
    @ColumnInfo(name = "ultima_fecha") val ultimaFecha: Long? = null
)

@Entity(
    tableName = "historial_repaso",
    foreignKeys = [ForeignKey(entity = FragmentoCodigoEntidad::class, parentColumns = ["id"], childColumns = ["fragmento_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["fragmento_id"])]
)
data class HistorialRepasoEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "fragmento_id") val fragmentoId: Long,
    val fecha: Long,
    val calidad: Int,
    @ColumnInfo(name = "intervalo_resultante") val intervaloResultante: Int
)

data class FragmentoConRepaso(
    @Embedded val fragmento: FragmentoCodigoEntidad,
    @Relation(parentColumn = "id", entityColumn = "fragmento_id") val repaso: RepasoEntidad?
)

@Entity(tableName = "reto")
data class RetoEntidad(
    @PrimaryKey val id: String,
    val titulo: String,
    val categoria: String,
    val dificultad: String,
    val lenguaje: String,
    @ColumnInfo(name = "definicion_json") val definicionJson: String,
    val origen: String = ORIGEN_CATALOGO,
    val resuelto: Boolean = false,
    @ColumnInfo(name = "mejor_puntaje") val mejorPuntaje: Int = 0,
    @ColumnInfo(name = "codigo_guardado") val codigoGuardado: String? = null
) {
    companion object {
        const val ORIGEN_CATALOGO = "CATALOGO"
        const val ORIGEN_IMPORTADO = "IMPORTADO"
    }
}

@Entity(
    tableName = "intento_reto",
    foreignKeys = [ForeignKey(entity = RetoEntidad::class, parentColumns = ["id"], childColumns = ["reto_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["reto_id"]), Index(value = ["fecha"])]
)
data class IntentoRetoEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "reto_id") val retoId: String,
    val fecha: Long,
    val aprobado: Boolean,
    val puntaje: Int,
    val aprobados: Int,
    val total: Int,
    @ColumnInfo(name = "duracion_ms") val duracionMs: Long,
    val codigo: String
)

@Entity(tableName = "actividad_diaria")
data class ActividadDiariaEntidad(
    @PrimaryKey val fecha: Long,
    @ColumnInfo(name = "segundos_practica") val segundosPractica: Long = 0,
    @ColumnInfo(name = "retos_resueltos") val retosResueltos: Int = 0,
    val ejecuciones: Int = 0,
    @ColumnInfo(name = "archivos_guardados") val archivosGuardados: Int = 0,
    @ColumnInfo(name = "repasos_completados") val repasosCompletados: Int = 0
)

@Entity(tableName = "registro_error", indices = [Index(value = ["fecha"]), Index(value = ["codigo"])])
data class RegistroErrorEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codigo: String,
    val lenguaje: String,
    val categoria: String,
    val origen: String,
    val fecha: Long,
    @ColumnInfo(name = "marca_tiempo") val marcaTiempo: Long
)

@Entity(tableName = "perfil_aprendizaje")
data class PerfilAprendizajeEntidad(
    @PrimaryKey val categoria: String,
    val puntaje: Int,
    val estado: String,
    val intentos: Int,
    val errores: Int,
    @ColumnInfo(name = "actualizado_en") val actualizadoEn: Long
)

@Entity(tableName = "preferencia")
data class PreferenciaEntidad(
    @PrimaryKey val clave: String,
    val valor: String
)

data class ConteoCodigo(val codigo: String, val cantidad: Int)
data class ConteoLenguaje(val lenguaje: String, val cantidad: Int)
