package it.miaterapia.family.security

import org.json.JSONObject

object RecoveryPackageCodec {
    fun encode(patientId: String, value: RecoveryPackage): String = JSONObject()
        .put("format", "miaterapia-recovery")
        .put("patientId", patientId)
        .put("version", value.version)
        .put("salt", value.salt)
        .put("nonce", value.nonce)
        .put("wrappedKey", value.wrappedKey)
        .put("iterations", value.iterations)
        .toString()

    fun decode(text: String): Pair<String, RecoveryPackage> {
        val json = JSONObject(text)
        require(json.getString("format") == "miaterapia-recovery") { "Formato recupero non valido" }
        return json.getString("patientId") to RecoveryPackage(
            version = json.getInt("version"),
            salt = json.getString("salt"),
            nonce = json.getString("nonce"),
            wrappedKey = json.getString("wrappedKey"),
            iterations = json.getInt("iterations")
        )
    }
}
