package it.miaterapia.family

import it.miaterapia.family.notifications.ReminderIdentity
import org.junit.Assert.*
import org.junit.Test

class ReminderIdentityTest {
    @Test fun collidingHashesHaveDistinctAlarmAndActionIdentities() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val identities = listOf("Aa", "BB").flatMap { id ->
            (listOf(ReminderIdentity.ALARM) + ReminderIdentity.actions).map { ReminderIdentity.uri(id, it) }
        }
        assertEquals(identities.size, identities.toSet().size)
    }

    @Test fun legacyHashCollisionDoesNotCancelAnotherDose() {
        assertTrue(ReminderIdentity.safeLegacyCodes("Aa", listOf("Aa", "BB")).isEmpty())
        assertFalse(ReminderIdentity.safeLegacyNotification("Aa", listOf("Aa", "BB")))
    }

    @Test fun legacyActionOffsetCollisionIsAlsoProtected() {
        assertEquals("A".hashCode() + 5, "F".hashCode())
        assertFalse("F".hashCode() in ReminderIdentity.safeLegacyCodes("A", listOf("A", "F")))
        assertFalse("F".hashCode() in ReminderIdentity.safeLegacyCodes("F", listOf("A", "F")))
    }

    @Test fun legacyIdentifiersWithoutCollisionsRemainCancellable() {
        assertEquals(setOf(0, 1, 5, 15, 30, 99).map { "dose".hashCode() + it }.toSet(),
            ReminderIdentity.safeLegacyCodes("dose", listOf("dose")))
        assertTrue(ReminderIdentity.safeLegacyNotification("dose", listOf("dose")))
    }

    @Test fun identifiersAreEncodedAsASinglePathSegment() {
        assertNotEquals(ReminderIdentity.uri("dose/other", "taken"), ReminderIdentity.uri("dose", "other/taken"))
        assertTrue(ReminderIdentity.uri("dose/other", "taken").contains("dose%2Fother"))
    }
}
