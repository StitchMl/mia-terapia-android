package it.miaterapia.family

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("MiaTerapia"); Text("Dati dimostrativi", style = MaterialTheme.typography.labelMedium) } }) },
        bottomBar = { NavigationBar { Tab.entries.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(when(item) { Tab.TODAY -> Icons.Outlined.Today; Tab.THERAPIES -> Icons.Outlined.Medication; Tab.STOCK -> Icons.Outlined.Inventory2; Tab.LOG -> Icons.Outlined.History; Tab.SETTINGS -> Icons.Outlined.Settings }, null) }, label = { Text(item.label) }) } } }
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { when(tab) { Tab.TODAY -> TodayScreen(snapshot, vm::confirm); Tab.THERAPIES -> TherapiesScreen(snapshot); Tab.STOCK -> StockScreen(snapshot); Tab.LOG -> LogScreen(snapshot); Tab.SETTINGS -> SettingsScreen() } } }
}

@Composable private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Column(content = content) } }
@Composable private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

@Composable private fun TodayScreen(s: TherapySnapshot, confirm: (String) -> Unit) {
    val meds = s.medications.associateBy { it.id }
    Page("Oggi") {
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

@Composable private fun TherapiesScreen(s: TherapySnapshot) = Page("Terapie") { Heading("Terapie"); Text("Farmaci attivi per ogni profilo."); Spacer(Modifier.height(8.dp)); s.medications.forEach { med -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(med.name, style = MaterialTheme.typography.titleMedium); Text(listOfNotNull(med.form, med.strength).joinToString(" · ")) } }; Spacer(Modifier.height(10.dp)) } }

@Composable private fun StockScreen(s: TherapySnapshot) {
    val meds = s.medications.associateBy { it.id }
    val totals = s.movements.groupBy { it.medicationId to it.unit }.mapValues { (_, rows) -> rows.fold(java.math.BigDecimal.ZERO) { total, row -> total + row.amount() } }
    Page("Scorte") { Heading("Scorte"); Text("Saldo calcolato dai movimenti registrati."); Spacer(Modifier.height(8.dp)); totals.forEach { (key, value) -> ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(meds[key.first]?.name ?: "Farmaco"); Text("${value.stripTrailingZeros().toPlainString()} ${unitLabel(key.second)}", style = MaterialTheme.typography.titleMedium) } }; Spacer(Modifier.height(10.dp)) } }
}

@Composable private fun LogScreen(s: TherapySnapshot) = Page("Registro") { Heading("Registro"); Text("Cronologia locale. Le correzioni future conserveranno gli eventi originali."); Spacer(Modifier.height(8.dp)); s.doses.filter { it.status == DoseStatus.TAKEN }.forEach { dose -> ListItem(headlineContent = { Text("Dose confermata") }, supportingContent = { Text("${formatDate(dose.confirmedAtEpochMillis ?: dose.scheduledAtEpochMillis)} · ${dose.doseAmount} ${unitLabel(dose.doseUnit)}") }, leadingContent = { Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }) } }

@Composable private fun SettingsScreen() {
    val context = LocalContext.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    var notificationsGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsGranted = it }
    val exact = alarmManager.canScheduleExactAlarms()
    Page("Impostazioni") {
        Heading("Impostazioni")
        PermissionRow("Notifiche", notificationsGranted) { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        PermissionRow("Sveglie esatte", exact) { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Google Drive", style = MaterialTheme.typography.titleMedium)
        Text("Disattivato. Prima servono test OAuth tra due account e una procedura di recupero chiavi verificata.")
        OutlinedButton(onClick = { NotificationHelper.show(context, "test") }, enabled = notificationsGranted, modifier = Modifier.fillMaxWidth()) { Text("Invia notifica di prova") }
    }
}

@Composable private fun PermissionRow(label: String, granted: Boolean, action: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(label, style = MaterialTheme.typography.titleMedium); Text(if (granted) "Concesso" else "Richiesto", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }; if (!granted) OutlinedButton(onClick = action) { Text("Configura") } } }
private fun formatTime(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun formatDate(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
private fun statusLabel(status: DoseStatus) = when(status) { DoseStatus.PLANNED -> "Prevista"; DoseStatus.TAKEN -> "Confermata"; DoseStatus.SNOOZED -> "Posposta"; DoseStatus.SKIPPED -> "Saltata"; DoseStatus.UNCONFIRMED -> "Non confermata"; DoseStatus.CANCELLED -> "Annullata"; DoseStatus.NOTIFIED -> "Notificata" }
private fun unitLabel(unit: StockUnit) = when(unit) { StockUnit.TABLET -> "compresse"; StockUnit.CAPSULE -> "capsule"; StockUnit.MILLILITER -> "ml"; StockUnit.DROP -> "gocce"; StockUnit.DOSE -> "dosi"; StockUnit.PUFF -> "erogazioni"; StockUnit.GRAM -> "g"; StockUnit.MANUAL -> "unità" }
