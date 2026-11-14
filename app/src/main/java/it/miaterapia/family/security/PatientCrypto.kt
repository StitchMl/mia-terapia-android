package it.miaterapia.family.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedEnvelope(val version: Int = 1, val nonce: String, val ciphertext: String)
data class RecoveryPackage(val version: Int = 1, val salt: String, val nonce: String, val wrappedKey: String, val iterations: Int)

/**
 * Cifratura applicativa per paziente.
 *
 * Il patientId è AAD: un payload spostato nella cartella di un altro paziente
 * non supera l'autenticazione GCM. Le chiavi di recupero derivano da PBKDF2 e
 * vengono azzerate appena terminato l'uso.
 */
object PatientCrypto {
    private const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val RECOVERY_ITERATIONS = 210_000
    private val random = SecureRandom()

    fun newPatientKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    fun encrypt(patientId: String, plaintext: ByteArray, key: ByteArray): EncryptedEnvelope {
        require(key.size == KEY_BYTES) { "La chiave paziente deve contenere 256 bit" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        // Lega crittograficamente il payload alla sua identità logica.
        cipher.updateAAD(patientId.toByteArray(Charsets.UTF_8))
        return EncryptedEnvelope(nonce = nonce.b64(), ciphertext = cipher.doFinal(plaintext).b64())
    }

    @Throws(AEADBadTagException::class)
    fun decrypt(patientId: String, envelope: EncryptedEnvelope, key: ByteArray): ByteArray {
        require(envelope.version == 1) { "Versione cifratura non supportata" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, envelope.nonce.b64Bytes()))
        cipher.updateAAD(patientId.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(envelope.ciphertext.b64Bytes())
    }

    fun createRecoveryPackage(patientId: String, key: ByteArray, recoveryCode: CharArray): RecoveryPackage {
        require(recoveryCode.size >= 12) { "Il codice di recupero richiede almeno 12 caratteri" }
        val salt = ByteArray(16).also(random::nextBytes)
        val wrappingKey = derive(recoveryCode, salt, RECOVERY_ITERATIONS)
        val envelope = encrypt(patientId, key, wrappingKey)
        wrappingKey.fill(0) // Riduce la permanenza della chiave derivata in memoria.
        return RecoveryPackage(salt = salt.b64(), nonce = envelope.nonce, wrappedKey = envelope.ciphertext, iterations = RECOVERY_ITERATIONS)
    }

    fun recoverPatientKey(patientId: String, recoveryPackage: RecoveryPackage, recoveryCode: CharArray): ByteArray {
        require(recoveryPackage.version == 1 && recoveryPackage.iterations >= RECOVERY_ITERATIONS) { "Pacchetto di recupero non valido" }
        val wrappingKey = derive(recoveryCode, recoveryPackage.salt.b64Bytes(), recoveryPackage.iterations)
        return try {
            decrypt(patientId, EncryptedEnvelope(nonce = recoveryPackage.nonce, ciphertext = recoveryPackage.wrappedKey), wrappingKey)
        } finally {
            wrappingKey.fill(0)
        }
    }

    private fun derive(code: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(code, salt, iterations, KEY_BYTES * 8)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    private fun ByteArray.b64(): String = Base64.getEncoder().withoutPadding().encodeToString(this)
    private fun String.b64Bytes(): ByteArray = Base64.getDecoder().decode(this)
}
