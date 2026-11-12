package it.miaterapia.family

import androidx.test.core.app.ApplicationProvider
import it.miaterapia.family.security.PatientKeyStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PatientKeyStoreTest {
    @Test fun keyPersistsEncryptedAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<MiaTerapiaApplication>()
        val patientId = "instrumentation-${System.currentTimeMillis()}"
        val first = PatientKeyStore(context).getOrCreate(patientId)
        val second = PatientKeyStore(context).getOrCreate(patientId)
        assertArrayEquals(first, second)
        assertEquals(32, first.size)
        PatientKeyStore(context).delete(patientId)
    }

    @Test fun recoveredKeyCanReplaceLocalCopy() {
        val context = ApplicationProvider.getApplicationContext<MiaTerapiaApplication>()
        val patientId = "recovery-${System.currentTimeMillis()}"
        val expected = ByteArray(32) { it.toByte() }
        PatientKeyStore(context).importRecovered(patientId, expected)
        assertArrayEquals(expected, PatientKeyStore(context).getOrCreate(patientId))
        PatientKeyStore(context).delete(patientId)
    }
}
