package it.miaterapia.family

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.miaterapia.family.data.*
import it.miaterapia.family.notifications.AlarmScheduler
import it.miaterapia.family.notifications.NotificationHelper
import it.miaterapia.family.reports.PdfExporter
import it.miaterapia.family.security.PatientCrypto
import it.miaterapia.family.security.PatientKeyStore
import it.miaterapia.family.sync.DriveAuthorization
import it.miaterapia.family.sync.DriveRestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import it.miaterapia.family.ui.MiaTerapiaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MiaTerapiaTheme { App() } }
    }
}

private enum class Tab(val label: String) { TODAY("Oggi"), THERAPIES("Terapie"), STOCK("Scorte"), LOG("Registro"), SETTINGS("Altro") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App(vm: MainViewModel = viewModel()) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.TODAY) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Column { Text("MiaTerapia"); Text("Dati dimostrativi", style = MaterialTheme.typography.labelMedium) } }) },
        bottomBar = { NavigationBar { Tab.entries.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(when(item) { Tab.TODAY -> Icons.Outlined.Today; Tab.THERAPIES -> Icons.Outlined.Medication; Tab.STOCK -> Icons.Outlined.Inventory2; Tab.LOG -> Icons.Outlined.History; Tab.SETTINGS -> Icons.Outlined.Settings }, null) }, label = { Text(item.label) }) } } }
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { when(tab) { Tab.TODAY -> TodayScreen(snapshot, vm::confirm); Tab.THERAPIES -> TherapiesScreen(snapshot, vm::addMedication, vm::archiveMedication); Tab.STOCK -> StockScreen(snapshot, vm::addStock); Tab.LOG -> LogScreen(snapshot); Tab.SETTINGS -> SettingsScreen(snapshot, vm::addPatient) } } }
}

@Composable private fun Page(content: @Composable ColumnScope.() -> Unit) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Column(content = content) } }
@Composable private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

@Composable private fun TodayScreen(s: TherapySnapshot, confirm: (String) -> Unit) {
    val meds = s.medications.associateBy { it.id }
    Page {
        Heading("Oggi")
        Text("Le prossime dosi e le conferme del profilo attivo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        s.doses.forEach { dose ->
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(dose.scheduledAtEpochMillis), style = MaterialTheme.typography.titleLarge); AssistChip(onClick = {}, label = { Text(statusLabel(dose.status)) }) }
                Text(meds[dose.medicationId]?.name ?: "Farmaco", style = MaterialTheme.typography.titleMedium)
                Text("${dose.doseAmount} ${unitLabel(dose.doseUnit)}")
                if (dose.status != DoseStatus.TAKEN) Button(onClick = { confirm(dose.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Farmaco preso") }
            } }
            Spacer(Modifier.height(12.dp))
        }
        if (s.doses.isEmpty()) Text("Nessuna dose programmata.")
    }
}

@Composable private fun TherapiesScreen(s: TherapySnapshot, add: (String, String, String, String?, String, StockUnit, Int, Int) -> Unit, archive: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Page { Heading("Terapie"); Text("Farmaci attivi per ogni profilo."); Button(onClick = { editing = true }, enabled = s.patients.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Aggiungi terapia") }; Spacer(Modifier.height(8.dp)); s.medications.forEach { med -> OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(med.name, style = MaterialTheme.typography.titleMedium); Text(listOfNotNull(med.form, med.strength).joinToString(" · ")) }; IconButton(onClick = { archive(med.id) }) { Icon(Icons.Outlined.Archive, "Archivia") } } }; Spacer(Modifier.height(10.dp)) } }
    if (editing) MedicationDialog(s.patients, onDismiss = { editing = false }) { patient, name, form, strength, amount, unit, hour, minute -> add(patient, name, form, strength, amount, unit, hour, minute); editing = false }
}

@Composable private fun StockScreen(s: TherapySnapshot, addStock: (String, String, StockUnit) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val meds = s.medications.associateBy { it.id }
    val totals = s.movements.groupBy { it.medicationId to it.unit }.mapValues { (_, rows) -> rows.fold(java.math.BigDecimal.ZERO) { total, row -> total + row.amount() } }
    Page { Heading("Scorte"); Text("Saldo calcolato dai movimenti registrati."); Button(onClick = { editing = true }, enabled = s.medications.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AddBox, null); Spacer(Modifier.width(8.dp)); Text("Registra rifornimento") }; Spacer(Modifier.height(8.dp)); totals.forEach { (key, value) -> ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(meds[key.first]?.name ?: "Farmaco"); Text("${value.stripTrailingZeros().toPlainString()} ${unitLabel(key.second)}", style = MaterialTheme.typography.titleMedium) } }; Spacer(Modifier.height(10.dp)) } }
    if (editing) StockDialog(s.medications, onDismiss = { editing = false }) { med, amount, unit -> addStock(med, amount, unit); editing = false }
}

@Composable private fun LogScreen(s: TherapySnapshot) = Page { Heading("Registro"); Text("Cronologia locale. Le correzioni future conserveranno gli eventi originali."); Spacer(Modifier.height(8.dp)); s.doses.filter { it.status == DoseStatus.TAKEN }.forEach { dose -> ListItem(headlineContent = { Text("Dose confermata") }, supportingContent = { Text("${formatDate(dose.confirmedAtEpochMillis ?: dose.scheduledAtEpochMillis)} · ${dose.doseAmount} ${unitLabel(dose.doseUnit)}") }, leadingContent = { Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }) } }

@Composable private fun SettingsScreen(snapshot: TherapySnapshot, addPatient: (String, PatientType) -> Unit) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val scope = rememberCoroutineScope()
    var notificationsGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsGranted = it }
    val exact = AlarmScheduler(context).canScheduleExact()
    var newProfile by remember { mutableStateOf(false) }
    var driveStatus by remember { mutableStateOf("Non collegato") }
    fun uploadSpike(token: String) {
        val patientId = snapshot.patients.firstOrNull()?.id ?: return
        driveStatus = "Verifica cifrata in corso…"
        scope.launch {
            driveStatus = runCatching {
                withContext(Dispatchers.IO) {
                    val key = PatientKeyStore(context).getOrCreate(patientId)
                    val envelope = PatientCrypto.encrypt(patientId, "{\"kind\":\"oauth-spike\",\"version\":1}".toByteArray(), key)
                    val payload = JSONObject().put("version", envelope.version).put("nonce", envelope.nonce).put("ciphertext", envelope.ciphertext).toString().toByteArray()
                    val client = DriveRestClient(token)
                    val folder = client.createMiaTerapiaTree(patientId)
                    client.uploadEncrypted("oauth-spike-${System.currentTimeMillis()}.json.enc", folder, payload)
                }
                "Collegato. Scrittura cifrata riuscita"
            }.getOrElse { "Errore Drive: ${it.message}" }
        }
    }
    val driveResolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) runCatching { DriveAuthorization.tokenFromResult(activity, result.data) }.onSuccess(::uploadSpike).onFailure { driveStatus = "Errore OAuth: ${it.message}" }
        else driveStatus = "Consenso annullato"
    }
    Page {
        Heading("Impostazioni")
        Text("Profili", style = MaterialTheme.typography.titleMedium)
        snapshot.patients.forEach { Text("${if (it.type == PatientType.HUMAN) "Persona" else "Animale"}: ${it.alias}") }
        OutlinedButton(onClick = { newProfile = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Aggiungi profilo") }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        PermissionRow("Notifiche", notificationsGranted) { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        PermissionRow("Sveglie esatte", exact) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Documenti", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { PdfExporter.share(context, PdfExporter.adherence(context, snapshot)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Report aderenza PDF") }
        OutlinedButton(onClick = { PdfExporter.share(context, PdfExporter.refillRequest(context, snapshot)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RequestPage, null); Spacer(Modifier.width(8.dp)); Text("Richiesta farmaci PDF") }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Google Drive", style = MaterialTheme.typography.titleMedium)
        Text(driveStatus)
        Button(onClick = { DriveAuthorization.authorize(activity, { driveResolution.launch(IntentSenderRequest.Builder(it).build()) }, ::uploadSpike) { driveStatus = "Errore OAuth: $it" } }, enabled = snapshot.patients.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CloudSync, null); Spacer(Modifier.width(8.dp)); Text("Collega e verifica Drive") }
        OutlinedButton(onClick = { NotificationHelper.show(context, "test") }, enabled = notificationsGranted, modifier = Modifier.fillMaxWidth()) { Text("Invia notifica di prova") }
    }
    if (newProfile) PatientDialog(onDismiss = { newProfile = false }) { alias, type -> addPatient(alias, type); newProfile = false }
}

@Composable private fun PatientDialog(onDismiss: () -> Unit, onSave: (String, PatientType) -> Unit) {
    var alias by remember { mutableStateOf("") }; var animal by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuovo profilo") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(alias, { alias = it }, label = { Text("Nome o alias") }, singleLine = true); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(animal, { animal = it }); Text("Animale") } } }, confirmButton = { TextButton(onClick = { onSave(alias, if (animal) PatientType.ANIMAL else PatientType.HUMAN) }, enabled = alias.isNotBlank()) { Text("Salva") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}

@Composable private fun MedicationDialog(patients: List<PatientEntity>, onDismiss: () -> Unit, onSave: (String, String, String, String?, String, StockUnit, Int, Int) -> Unit) {
    var patient by remember { mutableStateOf(patients.first().id) }; var name by remember { mutableStateOf("") }; var form by remember { mutableStateOf("Compressa") }; var strength by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("1") }; var hour by remember { mutableStateOf("08") }; var minute by remember { mutableStateOf("00") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuova terapia") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("Profilo: ${patients.first { it.id == patient }.alias}"); if (patients.size > 1) TextButton(onClick = { patient = patients[(patients.indexOfFirst { it.id == patient } + 1) % patients.size].id }) { Text("Cambia profilo") } }; item { OutlinedTextField(name, { name = it }, label = { Text("Farmaco") }, singleLine = true) }; item { OutlinedTextField(form, { form = it }, label = { Text("Forma") }, singleLine = true) }; item { OutlinedTextField(strength, { strength = it }, label = { Text("Dosaggio confezione") }, singleLine = true) }; item { OutlinedTextField(amount, { amount = it }, label = { Text("Quantità per assunzione") }, singleLine = true) }; item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(hour, { hour = it.take(2) }, label = { Text("Ora") }, modifier = Modifier.weight(1f)); OutlinedTextField(minute, { minute = it.take(2) }, label = { Text("Minuti") }, modifier = Modifier.weight(1f)) } } } }, confirmButton = { TextButton(onClick = { onSave(patient, name, form, strength, amount, StockUnit.TABLET, hour.toIntOrNull()?.coerceIn(0,23) ?: 8, minute.toIntOrNull()?.coerceIn(0,59) ?: 0) }, enabled = name.isNotBlank() && amount.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true) { Text("Salva") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}

@Composable private fun StockDialog(medications: List<MedicationEntity>, onDismiss: () -> Unit, onSave: (String, String, StockUnit) -> Unit) {
    var selected by remember { mutableStateOf(medications.first()) }; var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rifornimento") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Farmaco: ${selected.name}"); if (medications.size > 1) TextButton(onClick = { selected = medications[(medications.indexOf(selected) + 1) % medications.size] }) { Text("Cambia farmaco") }; OutlinedTextField(amount, { amount = it }, label = { Text("Quantità") }, singleLine = true) } }, confirmButton = { TextButton(onClick = { onSave(selected.id, amount, StockUnit.TABLET) }, enabled = amount.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true) { Text("Registra") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}

@Composable private fun PermissionRow(label: String, granted: Boolean, action: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(label, style = MaterialTheme.typography.titleMedium); Text(if (granted) "Concesso" else "Richiesto", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }; if (!granted) OutlinedButton(onClick = action) { Text("Configura") } } }
private fun formatTime(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun formatDate(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
private fun statusLabel(status: DoseStatus) = when(status) { DoseStatus.PLANNED -> "Prevista"; DoseStatus.TAKEN -> "Confermata"; DoseStatus.SNOOZED -> "Posposta"; DoseStatus.SKIPPED -> "Saltata"; DoseStatus.UNCONFIRMED -> "Non confermata"; DoseStatus.CANCELLED -> "Annullata"; DoseStatus.NOTIFIED -> "Notificata" }
private fun unitLabel(unit: StockUnit) = when(unit) { StockUnit.TABLET -> "compresse"; StockUnit.CAPSULE -> "capsule"; StockUnit.MILLILITER -> "ml"; StockUnit.DROP -> "gocce"; StockUnit.DOSE -> "dosi"; StockUnit.PUFF -> "erogazioni"; StockUnit.GRAM -> "g"; StockUnit.MANUAL -> "unità" }
