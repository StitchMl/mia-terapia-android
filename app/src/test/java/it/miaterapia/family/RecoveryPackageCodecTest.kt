package it.miaterapia.family

import it.miaterapia.family.security.PatientCrypto
import it.miaterapia.family.security.RecoveryPackageCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryPackageCodecTest {
    @Test fun recoveryFileRoundTrip() {
        val patientId = "p1"
        val code = "codice-recupero-2026".toCharArray()
        val key = PatientCrypto.newPatientKey()
        val encoded = RecoveryPackageCodec.encode(patientId, PatientCrypto.createRecoveryPackage(patientId, key, code))
        val decoded = RecoveryPackageCodec.decode(encoded)
        assertEquals(patientId, decoded.first)
        assertArrayEquals(key, PatientCrypto.recoverPatientKey(patientId, decoded.second, code))
    }
}
