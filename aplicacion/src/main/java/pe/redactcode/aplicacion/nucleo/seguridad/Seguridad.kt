package pe.redactcode.aplicacion.nucleo.seguridad

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class CifradorKeystore @Inject constructor() {
    private val alias = "redact_code_credenciales_v1"
    private val proveedor = "AndroidKeyStore"
    private val transformacion = "AES/GCM/NoPadding"

    private fun clave(): SecretKey {
        val almacen = KeyStore.getInstance(proveedor).apply { load(null) }
        (almacen.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, proveedor)
        generador.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }

    open fun cifrar(texto: String): String {
        val cifrador = Cipher.getInstance(transformacion)
        cifrador.init(Cipher.ENCRYPT_MODE, clave())
        val datos = cifrador.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cifrador.iv + datos, Base64.NO_WRAP)
    }

    open fun descifrar(cifrado: String): String {
        val bytes = Base64.decode(cifrado, Base64.NO_WRAP)
        val vector = bytes.copyOfRange(0, 12)
        val datos = bytes.copyOfRange(12, bytes.size)
        val cifrador = Cipher.getInstance(transformacion)
        cifrador.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, vector))
        return String(cifrador.doFinal(datos), Charsets.UTF_8)
    }
}

@Singleton
class AlmacenCredenciales @Inject constructor(
    @ApplicationContext contexto: Context,
    private val cifrador: CifradorKeystore
) {
    private val preferencias = contexto.getSharedPreferences("credenciales_cifradas", Context.MODE_PRIVATE)

    suspend fun guardar(clave: String, valor: String) = withContext(Dispatchers.IO) {
        preferencias.edit { putString(clave, cifrador.cifrar(valor)) }
    }

    suspend fun leer(clave: String): String? = withContext(Dispatchers.IO) {
        val cifrado = preferencias.getString(clave, null) ?: return@withContext null
        try {
            cifrador.descifrar(cifrado)
        } catch (e: Exception) {
            preferencias.edit { remove(clave) }
            null
        }
    }

    fun leerSincrono(clave: String): String? {
        val cifrado = preferencias.getString(clave, null) ?: return null
        return try { cifrador.descifrar(cifrado) } catch (e: Exception) { null }
    }

    fun existe(clave: String): Boolean = preferencias.contains(clave)

    suspend fun eliminar(clave: String) = withContext(Dispatchers.IO) { preferencias.edit { remove(clave) } }

    suspend fun eliminarTodo() = withContext(Dispatchers.IO) { preferencias.edit { clear() } }

    companion object {
        const val TOKEN_GITHUB = "token_github"
        const val USUARIO_GITHUB = "usuario_github"
        const val CLAVE_IA = "clave_proveedor_ia"
        const val CLAVE_COMPATIBLE = "clave_proveedor_compatible"
        const val HASH_PIN = "hash_pin"
        const val SAL_PIN = "sal_pin"
        const val INTENTOS_PIN = "intentos_pin"
        const val BLOQUEO_PIN = "bloqueo_pin"
    }
}

@Singleton
class GestorPin @Inject constructor(private val almacen: AlmacenCredenciales) {
    private var intentosFallidos = 0
    private var bloqueadoHasta = 0L
    internal var reloj: () -> Long = { System.currentTimeMillis() }

    val tienePin: Boolean get() = almacen.existe(AlmacenCredenciales.HASH_PIN)

    fun esPinValido(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }

    suspend fun establecer(pin: String) {
        require(esPinValido(pin)) { "El PIN debe tener entre 4 y 8 dígitos" }
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derivar(pin, sal)
        almacen.guardar(AlmacenCredenciales.SAL_PIN, Base64.encodeToString(sal, Base64.NO_WRAP))
        almacen.guardar(AlmacenCredenciales.HASH_PIN, Base64.encodeToString(hash, Base64.NO_WRAP))
    }

    suspend fun eliminar() {
        almacen.eliminar(AlmacenCredenciales.HASH_PIN)
        almacen.eliminar(AlmacenCredenciales.SAL_PIN)
        almacen.eliminar(AlmacenCredenciales.INTENTOS_PIN)
        almacen.eliminar(AlmacenCredenciales.BLOQUEO_PIN)
        intentosFallidos = 0
        bloqueadoHasta = 0L
    }

    fun segundosBloqueo(): Long = ((bloqueadoHasta - reloj()) / 1000).coerceAtLeast(0)

    suspend fun consultarSegundosBloqueo(): Long {
        cargarEstado()
        return segundosBloqueo()
    }

    private var estadoCargado = false

    private suspend fun cargarEstado() {
        if (estadoCargado) return
        intentosFallidos = almacen.leer(AlmacenCredenciales.INTENTOS_PIN)?.toIntOrNull() ?: 0
        bloqueadoHasta = almacen.leer(AlmacenCredenciales.BLOQUEO_PIN)?.toLongOrNull() ?: 0L
        val maximo = reloj() + 30_000L * (intentosFallidos - 4).coerceAtLeast(0)
        if (bloqueadoHasta > maximo) bloqueadoHasta = maximo
        estadoCargado = true
    }

    private suspend fun guardarEstado() {
        almacen.guardar(AlmacenCredenciales.INTENTOS_PIN, intentosFallidos.toString())
        almacen.guardar(AlmacenCredenciales.BLOQUEO_PIN, bloqueadoHasta.toString())
    }

    suspend fun verificar(pin: String): Boolean {
        cargarEstado()
        if (segundosBloqueo() > 0) return false
        val sal = almacen.leer(AlmacenCredenciales.SAL_PIN)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val esperado = almacen.leer(AlmacenCredenciales.HASH_PIN)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val correcto = MessageDigest.isEqual(esperado, derivar(pin, sal))
        if (correcto) {
            intentosFallidos = 0
            bloqueadoHasta = 0L
        } else {
            intentosFallidos++
            if (intentosFallidos >= 5) {
                bloqueadoHasta = reloj() + 30_000L * (intentosFallidos - 4)
            }
        }
        guardarEstado()
        return correcto
    }

    private suspend fun derivar(pin: String, sal: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val especificacion = PBEKeySpec(pin.toCharArray(), sal, 60_000, 256)
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).encoded
    }
}

@Singleton
class AutenticadorBiometrico @Inject constructor(@ApplicationContext private val contexto: Context) {
    private val autenticadores = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun disponible(): Boolean = BiometricManager.from(contexto).canAuthenticate(autenticadores) == BiometricManager.BIOMETRIC_SUCCESS

    fun autenticar(actividad: FragmentActivity, alExito: () -> Unit, alError: (String) -> Unit) {
        val prompt = BiometricPrompt(actividad, ContextCompat.getMainExecutor(actividad), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult) = alExito()
            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) = alError(mensaje.toString())
        })
        val informacion = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear Redact Code")
            .setSubtitle("Confirma tu identidad para continuar")
            .setNegativeButtonText("Usar PIN")
            .setAllowedAuthenticators(autenticadores)
            .build()
        prompt.authenticate(informacion)
    }
}
