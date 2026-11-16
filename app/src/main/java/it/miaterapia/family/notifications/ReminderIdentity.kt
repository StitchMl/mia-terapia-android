package it.miaterapia.family.notifications

object ReminderIdentity {
    const val ALARM = "alarm"
    val actions = listOf("taken", "skip", "snooze5", "snooze15", "snooze30", "continue")
    private val legacyOffsets = listOf(0, 1, 5, 15, 30, 99)

    fun uri(occurrenceId: String, action: String): String =
        "miaterapia://dose/${java.net.URLEncoder.encode(occurrenceId, "UTF-8")}/$action"

    // Gli extra non distinguono i PendingIntent. Per i vecchi identificativi
    // cancellare solo i codici che non possono appartenere a un'altra dose.
    fun safeLegacyCodes(id: String, allIds: List<String>): Set<Int> {
        val others = allIds.filter { it != id }.flatMap { other -> legacyOffsets.map { other.hashCode() + it } }.toSet()
        return legacyOffsets.map { id.hashCode() + it }.filterNot { it in others }.toSet()
    }

    fun safeLegacyNotification(id: String, allIds: List<String>) =
        allIds.none { it != id && it.hashCode() == id.hashCode() }
}
