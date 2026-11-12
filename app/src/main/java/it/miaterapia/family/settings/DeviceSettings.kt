package it.miaterapia.family.settings

import android.content.Context
import java.util.UUID

enum class UserRole { SELF, CAREGIVER, BOTH }

class DeviceSettings(context: Context) {
    private val preferences = context.getSharedPreferences("device_settings_v1", Context.MODE_PRIVATE)
    val deviceId: String
        get() = preferences.getString("deviceId", null) ?: UUID.randomUUID().toString().also { preferences.edit().putString("deviceId", it).apply() }
    var role: UserRole
        get() = UserRole.valueOf(preferences.getString("role", UserRole.SELF.name) ?: UserRole.SELF.name)
        set(value) { preferences.edit().putString("role", value.name).apply() }
    fun assignedPatients(): Set<String> = preferences.getStringSet("alarmPatients", emptySet()).orEmpty().toSet()
    fun isAlarmAssigned(patientId: String) = patientId in assignedPatients()
    fun setAlarmAssigned(patientId: String, assigned: Boolean) {
        val updated = assignedPatients().toMutableSet().apply { if (assigned) add(patientId) else remove(patientId) }
        preferences.edit().putStringSet("alarmPatients", updated).apply()
    }
}
