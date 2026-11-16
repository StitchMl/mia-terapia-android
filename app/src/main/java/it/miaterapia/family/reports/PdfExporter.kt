package it.miaterapia.family.reports

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import it.miaterapia.family.data.DoseStatus
import it.miaterapia.family.data.TherapySnapshot
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Genera documenti A4 locali e li espone soltanto tramite URI temporanei FileProvider. */
object PdfExporter {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun adherence(context: Context, snapshot: TherapySnapshot): File = create(
        context,
        "report-aderenza.pdf",
        "MiaTerapia · Report aderenza",
        buildList {
            add("Generato: ${format(System.currentTimeMillis())}")
            add("Profili: ${snapshot.patients.joinToString { it.alias }}")
            add("")
            val month = YearMonth.now()
            // La specifica richiede due blocchi leggibili: giorni 1–15 e 16–fine mese.
            ReportLayout.dayRanges(month.lengthOfMonth()).forEachIndexed { index, range ->
                if (index > 0) add(PAGE_BREAK)
                add("Calendario ${month.month.name.lowercase()} ${month.year} · giorni ${range.first}–${range.last}")
                range.forEach { day ->
                    val daily = snapshot.doses.filter { Instant.ofEpochMilli(it.scheduledAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate() == month.atDay(day) }
                    if (daily.isEmpty()) add(String.format("%02d  Nessuna dose registrata", day))
                    daily.forEach { dose ->
                        val medicine = snapshot.medications.firstOrNull { it.id == dose.medicationId }?.name ?: "Farmaco"
                        add(String.format("%02d  %s · %s %s · %s", day, medicine, dose.doseAmount, dose.doseUnit.name.lowercase(), dose.status.name))
                    }
                }
            }
            val completed = snapshot.doses.count { it.status == DoseStatus.TAKEN }
            val relevant = snapshot.doses.count { it.status in setOf(DoseStatus.TAKEN, DoseStatus.SKIPPED, DoseStatus.UNCONFIRMED) }
            add("")
            add("Aderenza: ${if (relevant == 0) "n/d" else "${completed * 100 / relevant}%"}")
        }
    )

    fun refillRequest(context: Context, snapshot: TherapySnapshot): File = create(
        context,
        "richiesta-farmaci.pdf",
        "MiaTerapia · Richiesta farmaci",
        buildList {
            val request = snapshot.requests.firstOrNull { it.status.name == "READY" }
            val selectedIds = request?.medicationIdsCsv?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
            val patient = snapshot.patients.firstOrNull { it.id == request?.patientId } ?: snapshot.patients.firstOrNull()
            add("Generata: ${format(System.currentTimeMillis())}")
            add("")
            add("Buongiorno Dott.ssa/Dott.,")
            add("per ${patient?.alias ?: "il paziente"} avrei necessità del rinnovo delle seguenti prescrizioni:")
            snapshot.medications.filter { it.active && (selectedIds.isEmpty() || it.id in selectedIds) }.forEach { medicine ->
                add("- ${medicine.name} ${medicine.strength.orEmpty()} · ${medicine.form}")
            }
            request?.note?.let { add("Altre richieste: $it") }
            add("Grazie e cordiali saluti.")
        }
    )

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Condividi PDF"))
    }

    fun shareRecovery(context: Context, patientId: String, content: String) {
        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(directory, "recupero-$patientId.miaterapia").apply { writeText(content) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Salva pacchetto di recupero"))
    }

    private fun create(context: Context, name: String, title: String, lines: List<String>): File {
        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(directory, name)
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 19f; isFakeBoldText = true }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var y = 58f
        page.canvas.drawText(title, 42f, y, titlePaint)
        y += 34f
        lines.forEach { line ->
            if (line == PAGE_BREAK || y > 800f) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                y = 52f
                if (line == PAGE_BREAK) return@forEach
            }
            page.canvas.drawText(line.take(105), 42f, y, paint)
            y += 19f
        }
        document.finishPage(page)
        file.outputStream().use(document::writeTo)
        document.close()
        return file
    }

    private fun format(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateFormat)
    private const val PAGE_BREAK = "\u000C"
}

object ReportLayout {
    fun dayRanges(daysInMonth: Int): List<IntRange> {
        require(daysInMonth in 28..31) { "Numero giorni del mese non valido" }
        return listOf(1..15, 16..daysInMonth)
    }
}
