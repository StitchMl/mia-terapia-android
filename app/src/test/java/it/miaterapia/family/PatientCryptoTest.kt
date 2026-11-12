package it.miaterapia.family

import it.miaterapia.family.security.PatientCrypto
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class PatientCryptoTest {
    @Test fun t20EncryptedEventRoundTrip() {
        val key = PatientCrypto.newPatientKey()
        val encrypted = PatientCrypto.encrypt("patient-1", "evento clinico".toByteArray(), key)
        assertFalse(encrypted.ciphertext.contains("evento"))
        assertEquals("evento clinico", PatientCrypto.decrypt("patient-1", encrypted, key).decodeToString())
    }

    @Test(expected = AEADBadTagException::class)
    fun t21PatientBindingRejectsCiphertextSwap() {
        val key = PatientCrypto.newPatientKey()
        val encrypted = PatientCrypto.encrypt("patient-1", "dose".toByteArray(), key)
        PatientCrypto.decrypt("patient-2", encrypted, key)
    }

    @Test fun t22RecoveryPackageRestoresKey() {
        val key = PatientCrypto.newPatientKey()
        val code = "mare-luna-vento-2026".toCharArray()
        val pack = PatientCrypto.createRecoveryPackage("patient-1", key, code)
        assertArrayEquals(key, PatientCrypto.recoverPatientKey("patient-1", pack, code))
    }

    @Test(expected = AEADBadTagException::class)
    fun t23WrongRecoveryCodeFailsClosed() {
        val key = PatientCrypto.newPatientKey()
        val pack = PatientCrypto.createRecoveryPackage("patient-1", key, "mare-luna-vento-2026".toCharArray())
        PatientCrypto.recoverPatientKey("patient-1", pack, "codice-errato-lungo".toCharArray())
    }
}
