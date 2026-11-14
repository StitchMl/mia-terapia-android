package it.miaterapia.family.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Custodisce le chiavi paziente avvolte da una chiave non esportabile di Android Keystore.
 * SharedPreferences contiene solo nonce e testo cifrato, mai la chiave in chiaro.
 */
class PatientKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("patient_keys_v1", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    fun getOrCreate(patientId: String): ByteArray {
        val stored = preferences.getString(patientId, null)
        if (stored != null) return unwrap(patientId, stored)
        val key = PatientCrypto.newPatientKey()
        preferences.edit().putString(patientId, wrap(patientId, key)).apply()
        return key
    }

    @Synchronized
    fun importRecovered(patientId: String, key: ByteArray) {
        require(key.size == 32) { "Chiave paziente non valida" }
        preferences.edit().putString(patientId, wrap(patientId, key)).commit()
    }

    @Synchronized
    fun delete(patientId: String) { preferences.edit().remove(patientId).apply() }

    /** Recupera la chiave hardware-backed, creandola solo al primo utilizzo. */
    private fun masterKey(): SecretKey {
        (keyStore.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(MASTER_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    private fun wrap(patientId: String, key: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        cipher.updateAAD(patientId.toByteArray())
        return "${cipher.iv.b64()}.${cipher.doFinal(key).b64()}"
    }

    private fun unwrap(patientId: String, value: String): ByteArray {
        val parts = value.split('.')
        require(parts.size == 2) { "Chiave paziente archiviata non valida" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, parts[0].b64Bytes()))
        cipher.updateAAD(patientId.toByteArray())
        return cipher.doFinal(parts[1].b64Bytes())
    }

    private fun ByteArray.b64() = Base64.getEncoder().withoutPadding().encodeToString(this)
    private fun String.b64Bytes() = Base64.getDecoder().decode(this)

    private companion object { const val MASTER_ALIAS = "miaterapia.patient-key-wrap.v1" }
}
