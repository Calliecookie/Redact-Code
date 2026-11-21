package pe.redactcode.aplicacion.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pe.redactcode.aplicacion.nucleo.base_datos.BaseDatosRedactCode
import pe.redactcode.aplicacion.nucleo.ejecucion.MotorJavaScriptCompuesto
import pe.redactcode.aplicacion.nucleo.ejecucion.MotorJavaScriptSandbox
import pe.redactcode.aplicacion.nucleo.seguridad.AlmacenCredenciales
import pe.redactcode.nucleo.github.ClienteGitHub
import pe.redactcode.nucleo.javascript.MotorRhino
import pe.redactcode.nucleo.javascript.ServicioJavaScript
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuloAplicacion {
    @Provides
    @Singleton
    fun baseDatos(@ApplicationContext contexto: Context): BaseDatosRedactCode = BaseDatosRedactCode.crear(contexto)

    @Provides fun perfilDao(bd: BaseDatosRedactCode) = bd.perfilDao()
    @Provides fun proyectoDao(bd: BaseDatosRedactCode) = bd.proyectoDao()
    @Provides fun archivoDao(bd: BaseDatosRedactCode) = bd.archivoDao()
    @Provides fun fragmentoDao(bd: BaseDatosRedactCode) = bd.fragmentoDao()
    @Provides fun retoDao(bd: BaseDatosRedactCode) = bd.retoDao()
    @Provides fun actividadDao(bd: BaseDatosRedactCode) = bd.actividadDao()
    @Provides fun registroErrorDao(bd: BaseDatosRedactCode) = bd.registroErrorDao()
    @Provides fun perfilAprendizajeDao(bd: BaseDatosRedactCode) = bd.perfilAprendizajeDao()
    @Provides fun preferenciaDao(bd: BaseDatosRedactCode) = bd.preferenciaDao()

    @Provides
    @Singleton
    fun motorJavaScript(@ApplicationContext contexto: Context): MotorJavaScriptCompuesto =
        MotorJavaScriptCompuesto(MotorJavaScriptSandbox(contexto), MotorRhino())

    @Provides
    @Singleton
    fun servicioJavaScript(motor: MotorJavaScriptCompuesto): ServicioJavaScript = ServicioJavaScript(motor, motor)

    @Provides
    @Singleton
    fun clienteGitHub(credenciales: AlmacenCredenciales): ClienteGitHub =
        ClienteGitHub({ credenciales.leerSincrono(AlmacenCredenciales.TOKEN_GITHUB) })

    @Provides
    @Singleton
    fun clienteOpenVsx(): pe.redactcode.nucleo.extensiones.ClienteOpenVsx = pe.redactcode.nucleo.extensiones.ClienteOpenVsx()
}
