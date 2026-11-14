package it.miaterapia.family.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import it.miaterapia.family.MiaTerapiaApplication
import it.miaterapia.family.data.OutboxStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Svuota l'outbox cifrata quando la rete torna disponibile.
 * Il lavoro è unico per evitare worker concorrenti; i fallimenti usano backoff esponenziale.
 */
class DriveSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = (applicationContext as MiaTerapiaApplication).database.therapyDao()
        val rows = dao.pendingOutbox(System.currentTimeMillis())
        if (rows.isEmpty()) return@withContext Result.success()
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveAuthorization.DRIVE_FILE_SCOPE))).build()
        val authorization = runCatching { Tasks.await(Identity.getAuthorizationClient(applicationContext).authorize(request)) }.getOrElse { return@withContext Result.retry() }
        if (authorization.hasResolution() || authorization.accessToken == null) return@withContext Result.retry()
        val client = DriveRestClient(authorization.accessToken!!)
        var failed = false
        rows.forEach { row ->
            dao.updateOutbox(row.id, OutboxStatus.UPLOADING, 0, 0, null)
            runCatching {
                val json = JSONObject(row.encryptedPayload)
                val patientId = json.getString("patientId")
                val eventId = json.getString("eventId")
                val payload = json.getString("payload").toByteArray()
                val folder = client.createMiaTerapiaTree(patientId)
                client.uploadEncrypted("$eventId.json.enc", folder, payload)
            }.onSuccess {
                dao.updateOutbox(row.id, OutboxStatus.SENT, 0, 0, null)
            }.onFailure {
                failed = true
                // 1, 2, 4… minuti, con tetto a 60 per non martellare Drive.
                val delayMinutes = (1L shl row.attempts.coerceAtMost(6)).coerceAtMost(60)
                dao.updateOutbox(row.id, OutboxStatus.FAILED, 1, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes), it.message?.take(300))
            }
        }
        if (failed) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "drive-outbox"
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
