package pe.redactcode.aplicacion.nucleo.base_datos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PerfilDao {
    @Query("SELECT * FROM perfil WHERE id = 1")
    fun observar(): Flow<PerfilEntidad?>

    @Query("SELECT * FROM perfil WHERE id = 1")
    suspend fun obtener(): PerfilEntidad?

    @Upsert
    suspend fun guardar(perfil: PerfilEntidad)

    @Query("DELETE FROM perfil")
    suspend fun eliminar()
}

@Dao
interface ProyectoDao {
    @Query("SELECT * FROM proyecto ORDER BY modificado_en DESC")
    fun observarTodos(): Flow<List<ProyectoEntidad>>

    @Query("SELECT * FROM proyecto ORDER BY modificado_en DESC")
    suspend fun todos(): List<ProyectoEntidad>

    @Query("SELECT * FROM proyecto WHERE id = :id")
    fun observar(id: Long): Flow<ProyectoEntidad?>

    @Query("SELECT * FROM proyecto WHERE id = :id")
    suspend fun obtener(id: Long): ProyectoEntidad?

    @Query("SELECT * FROM proyecto WHERE proyecto_guiado_id = :guiadoId LIMIT 1")
    suspend fun porProyectoGuiado(guiadoId: String): ProyectoEntidad?

    @Query("SELECT * FROM proyecto WHERE proyecto_guiado_id IS NOT NULL")
    fun observarGuiados(): Flow<List<ProyectoEntidad>>

    @Query("SELECT COUNT(*) FROM proyecto WHERE carpeta = :carpeta")
    suspend fun contarCarpeta(carpeta: String): Int

    @Insert
    suspend fun insertar(proyecto: ProyectoEntidad): Long

    @Update
    suspend fun actualizar(proyecto: ProyectoEntidad)

    @Query("UPDATE proyecto SET modificado_en = :momento WHERE id = :id")
    suspend fun tocar(id: Long, momento: Long)

    @Delete
    suspend fun eliminar(proyecto: ProyectoEntidad)

    @Transaction
    @Query("SELECT * FROM proyecto WHERE id = :id")
    suspend fun conArchivos(id: Long): ProyectoConArchivos?
}

@Dao
interface ArchivoDao {
    @Query("SELECT * FROM archivo WHERE proyecto_id = :proyectoId AND ruta_relativa = :ruta")
    suspend fun obtener(proyectoId: Long, ruta: String): ArchivoEntidad?

    @Query("SELECT * FROM archivo WHERE abierto_en IS NOT NULL ORDER BY abierto_en DESC LIMIT :limite")
    fun observarRecientes(limite: Int): Flow<List<ArchivoEntidad>>

    @Upsert
    suspend fun guardar(archivo: ArchivoEntidad)

    @Query("DELETE FROM archivo WHERE proyecto_id = :proyectoId AND (ruta_relativa = :ruta OR ruta_relativa LIKE :prefijo)")
    suspend fun eliminarRuta(proyectoId: Long, ruta: String, prefijo: String)

    @Query("SELECT * FROM archivo WHERE proyecto_id = :proyectoId")
    suspend fun delProyecto(proyectoId: Long): List<ArchivoEntidad>

    @Query("SELECT lenguaje, COUNT(*) AS cantidad FROM archivo GROUP BY lenguaje")
    suspend fun conteoPorLenguaje(): List<ConteoLenguaje>
}

@Dao
interface FragmentoDao {
    @Transaction
    @Query("SELECT * FROM fragmento_codigo ORDER BY modificado_en DESC")
    fun observarTodos(): Flow<List<FragmentoConRepaso>>

    @Transaction
    @Query("SELECT * FROM fragmento_codigo WHERE id = :id")
    suspend fun obtener(id: Long): FragmentoConRepaso?

    @Transaction
    @Query("SELECT f.* FROM fragmento_codigo f INNER JOIN repaso r ON r.fragmento_id = f.id WHERE f.repaso_activo = 1 AND r.proxima_fecha <= :hoy ORDER BY r.proxima_fecha ASC")
    fun observarPendientes(hoy: Long): Flow<List<FragmentoConRepaso>>

    @Query("SELECT COUNT(*) FROM fragmento_codigo f INNER JOIN repaso r ON r.fragmento_id = f.id WHERE f.repaso_activo = 1 AND r.proxima_fecha <= :hoy")
    suspend fun contarPendientes(hoy: Long): Int

    @Query("SELECT * FROM fragmento_codigo")
    suspend fun todos(): List<FragmentoCodigoEntidad>

    @Insert
    suspend fun insertar(fragmento: FragmentoCodigoEntidad): Long

    @Update
    suspend fun actualizar(fragmento: FragmentoCodigoEntidad)

    @Query("DELETE FROM fragmento_codigo WHERE id = :id")
    suspend fun eliminar(id: Long)

    @Upsert
    suspend fun guardarRepaso(repaso: RepasoEntidad)

    @Query("SELECT * FROM repaso WHERE fragmento_id = :fragmentoId")
    suspend fun repasoDe(fragmentoId: Long): RepasoEntidad?

    @Insert
    suspend fun registrarHistorial(historial: HistorialRepasoEntidad)

    @Query("SELECT * FROM historial_repaso WHERE fragmento_id = :fragmentoId ORDER BY fecha DESC")
    suspend fun historial(fragmentoId: Long): List<HistorialRepasoEntidad>
}

@Dao
interface RetoDao {
    @Query("SELECT * FROM reto ORDER BY origen, dificultad, titulo")
    fun observarTodos(): Flow<List<RetoEntidad>>

    @Query("SELECT * FROM reto")
    suspend fun todos(): List<RetoEntidad>

    @Query("SELECT * FROM reto WHERE id = :id")
    suspend fun obtenerPorId(id: String): RetoEntidad?

    @Query("SELECT * FROM reto WHERE id = :id")
    fun observar(id: String): Flow<RetoEntidad?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarSiNoExiste(retos: List<RetoEntidad>)

    @Query("UPDATE reto SET titulo = :titulo, categoria = :categoria, dificultad = :dificultad, lenguaje = :lenguaje, definicion_json = :definicion WHERE id = :id")
    suspend fun actualizarDefinicion(id: String, titulo: String, categoria: String, dificultad: String, lenguaje: String, definicion: String)

    @Upsert
    suspend fun guardar(reto: RetoEntidad)

    @Query("UPDATE reto SET codigo_guardado = :codigo WHERE id = :id")
    suspend fun guardarCodigo(id: String, codigo: String)

    @Insert
    suspend fun registrarIntento(intento: IntentoRetoEntidad)

    @Query("SELECT * FROM intento_reto ORDER BY fecha DESC")
    fun observarIntentos(): Flow<List<IntentoRetoEntidad>>

    @Query("SELECT * FROM intento_reto ORDER BY fecha DESC")
    suspend fun intentos(): List<IntentoRetoEntidad>

    @Query("SELECT * FROM intento_reto WHERE reto_id = :retoId ORDER BY fecha DESC")
    fun observarIntentosDe(retoId: String): Flow<List<IntentoRetoEntidad>>

    @Query("SELECT COUNT(*) FROM reto WHERE resuelto = 1")
    fun observarResueltos(): Flow<Int>
}

@Dao
interface ActividadDao {
    @Query("SELECT * FROM actividad_diaria WHERE fecha >= :desde ORDER BY fecha")
    fun observarDesde(desde: Long): Flow<List<ActividadDiariaEntidad>>

    @Query("SELECT * FROM actividad_diaria ORDER BY fecha")
    fun observarTodas(): Flow<List<ActividadDiariaEntidad>>

    @Query("SELECT * FROM actividad_diaria WHERE fecha = :fecha")
    suspend fun obtener(fecha: Long): ActividadDiariaEntidad?

    @Upsert
    suspend fun guardar(actividad: ActividadDiariaEntidad)
}

@Dao
interface RegistroErrorDao {
    @Insert
    suspend fun insertar(registro: RegistroErrorEntidad)

    @Query("SELECT * FROM registro_error WHERE fecha >= :desde")
    suspend fun desde(desde: Long): List<RegistroErrorEntidad>

    @Query("SELECT * FROM registro_error WHERE fecha >= :desde")
    fun observarDesde(desde: Long): Flow<List<RegistroErrorEntidad>>

    @Query("SELECT codigo, COUNT(*) AS cantidad FROM registro_error GROUP BY codigo ORDER BY cantidad DESC")
    suspend fun conteoPorCodigo(): List<ConteoCodigo>

    @Query("SELECT lenguaje, COUNT(*) AS cantidad FROM registro_error GROUP BY lenguaje")
    suspend fun conteoPorLenguaje(): List<ConteoLenguaje>
}

@Dao
interface PerfilAprendizajeDao {
    @Query("SELECT * FROM perfil_aprendizaje")
    fun observar(): Flow<List<PerfilAprendizajeEntidad>>

    @Upsert
    suspend fun guardar(entradas: List<PerfilAprendizajeEntidad>)
}

@Dao
interface PreferenciaDao {
    @Query("SELECT valor FROM preferencia WHERE clave = :clave")
    suspend fun obtener(clave: String): String?

    @Query("SELECT * FROM preferencia")
    suspend fun todas(): List<PreferenciaEntidad>

    @Upsert
    suspend fun guardar(preferencia: PreferenciaEntidad)

    @Query("DELETE FROM preferencia WHERE clave = :clave")
    suspend fun eliminar(clave: String)
}
