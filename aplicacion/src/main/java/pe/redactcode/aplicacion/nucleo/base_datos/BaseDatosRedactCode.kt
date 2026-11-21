package pe.redactcode.aplicacion.nucleo.base_datos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        PerfilEntidad::class,
        ProyectoEntidad::class,
        ArchivoEntidad::class,
        FragmentoCodigoEntidad::class,
        RepasoEntidad::class,
        HistorialRepasoEntidad::class,
        RetoEntidad::class,
        IntentoRetoEntidad::class,
        ActividadDiariaEntidad::class,
        RegistroErrorEntidad::class,
        PerfilAprendizajeEntidad::class,
        PreferenciaEntidad::class
    ],
    version = BaseDatosRedactCode.VERSION,
    exportSchema = true
)
abstract class BaseDatosRedactCode : RoomDatabase() {
    abstract fun perfilDao(): PerfilDao
    abstract fun proyectoDao(): ProyectoDao
    abstract fun archivoDao(): ArchivoDao
    abstract fun fragmentoDao(): FragmentoDao
    abstract fun retoDao(): RetoDao
    abstract fun actividadDao(): ActividadDao
    abstract fun registroErrorDao(): RegistroErrorDao
    abstract fun perfilAprendizajeDao(): PerfilAprendizajeDao
    abstract fun preferenciaDao(): PreferenciaDao

    companion object {
        const val VERSION = 1
        const val NOMBRE = "redact_code.db"

        fun crear(contexto: Context): BaseDatosRedactCode =
            Room.databaseBuilder(contexto, BaseDatosRedactCode::class.java, NOMBRE)
                .addMigrations(*MigracionesBaseDatos.TODAS)
                .build()
    }
}

object MigracionesBaseDatos {
    val TODAS: Array<Migration> = emptyArray()
}
