package it.miaterapia.family.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class DriveRestClient(private val accessToken: String) {
    data class RemoteFile(val id: String, val name: String, val modifiedTime: String)
    fun ensureFolder(name: String, parentId: String? = null): String {
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "Nome cartella non valido" }
        val clauses = mutableListOf("name = '$name'", "mimeType = 'application/vnd.google-apps.folder'", "trashed = false")
        parentId?.let { clauses += "'$it' in parents" }
        val q = URLEncoder.encode(clauses.joinToString(" and "), Charsets.UTF_8.name())
        val existing = request("GET", "https://www.googleapis.com/drive/v3/files?q=$q&spaces=drive&fields=files(id)")
        val files = JSONObject(existing).getJSONArray("files")
        if (files.length() > 0) return files.getJSONObject(0).getString("id")
        val metadata = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder")
        parentId?.let { metadata.put("parents", JSONArray().put(it)) }
        return JSONObject(request("POST", "https://www.googleapis.com/drive/v3/files?fields=id", metadata.toString().toByteArray(), "application/json; charset=UTF-8")).getString("id")
    }

    fun uploadEncrypted(name: String, parentId: String, payload: ByteArray): String {
        require(name.endsWith(".enc") && name.matches(Regex("[A-Za-z0-9._-]+"))) { "Il file remoto deve essere cifrato" }
        val boundary = "miaterapia_${System.nanoTime()}"
        val metadata = JSONObject().put("name", name).put("parents", JSONArray().put(parentId)).toString()
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray()
        val suffix = "\r\n--$boundary--".toByteArray()
        val body = ByteArray(prefix.size + payload.size + suffix.size)
        prefix.copyInto(body); payload.copyInto(body, prefix.size); suffix.copyInto(body, prefix.size + payload.size)
        val response = request("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", body, "multipart/related; boundary=$boundary")
        return JSONObject(response).getString("id")
    }

    fun listEncrypted(parentId: String): List<RemoteFile> {
        val q = URLEncoder.encode("'$parentId' in parents and trashed = false", Charsets.UTF_8.name())
        val body = request("GET", "https://www.googleapis.com/drive/v3/files?q=$q&spaces=drive&orderBy=modifiedTime%20desc&fields=files(id,name,modifiedTime)")
        val files = JSONObject(body).getJSONArray("files")
        return (0 until files.length()).mapNotNull { index ->
            val item = files.getJSONObject(index)
            val name = item.getString("name")
            if (!name.endsWith(".enc")) null else RemoteFile(item.getString("id"), name, item.optString("modifiedTime"))
        }
    }

    fun downloadEncrypted(fileId: String): ByteArray {
        require(fileId.matches(Regex("[A-Za-z0-9_-]+"))) { "ID Drive non valido" }
        return requestBytes("GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
    }

    fun updateEncrypted(fileId: String, payload: ByteArray) {
        require(fileId.matches(Regex("[A-Za-z0-9_-]+"))) { "ID Drive non valido" }
        requestBytes("PATCH", "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media", payload, "application/octet-stream")
    }

    fun createMiaTerapiaTree(patientId: String): String {
        val root = ensureFolder("MiaTerapia")
        val version = ensureFolder("v1", root)
        val patients = ensureFolder("patients", version)
        val patient = ensureFolder(patientId, patients)
        return ensureFolder("events", patient)
    }

    private fun request(method: String, url: String, body: ByteArray? = null, contentType: String? = null): String {
        return requestBytes(method, url, body, contentType).toString(Charsets.UTF_8)
    }

    private fun requestBytes(method: String, url: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) throw IOException("Drive HTTP $code: ${bytes.toString(Charsets.UTF_8).take(300)}")
            bytes
        } finally { connection.disconnect() }
    }
}
