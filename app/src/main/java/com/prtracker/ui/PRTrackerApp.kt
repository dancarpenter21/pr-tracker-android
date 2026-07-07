package com.prtracker.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prtracker.R
import com.prtracker.core.Sex
import com.prtracker.core.WeightUnit
import com.prtracker.data.AppState
import com.prtracker.data.EntryWithLift
import com.prtracker.data.LiftEntity
import com.prtracker.data.kgTo
import com.prtracker.data.toWeightUnit
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import kotlin.math.max

private enum class Tab(val label: String, val icon: ImageVector) {
    Lifts("Lifts", Icons.Default.FitnessCenter),
    Log("Log", Icons.Default.Add),
    Progress("Progress", Icons.Default.BarChart),
    Profile("Profile", Icons.Default.Person),
    Backup("Backup", Icons.Default.CloudUpload),
}

@Composable
fun PRTrackerApp(
    viewModel: AppViewModel,
    onSignInRequested: () -> Unit,
    onAuthResolutionRequested: (Intent) -> Unit,
    onRestoreCompleted: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    var tab by remember { mutableStateOf(Tab.Lifts) }

    LaunchedEffect(backupState.authResolutionIntent) {
        backupState.authResolutionIntent?.let {
            onAuthResolutionRequested(it)
            viewModel.consumeAuthResolution()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.app_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f)),
        )
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                    Tab.entries.forEach {
                        NavigationBarItem(
                            selected = tab == it,
                            onClick = { tab = it },
                            icon = { Icon(it.icon, contentDescription = it.label) },
                            label = { Text(it.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                Text(
                    "PR Tracker",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                when (tab) {
                    Tab.Lifts -> LiftsScreen(state)
                    Tab.Log -> LogScreen(state, viewModel)
                    Tab.Progress -> ProgressScreen(state)
                    Tab.Profile -> ProfileScreen(state, viewModel)
                    Tab.Backup -> BackupScreen(
                        backupState = backupState,
                        viewModel = viewModel,
                        onSignInRequested = onSignInRequested,
                        onRestoreCompleted = onRestoreCompleted,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiftsScreen(state: AppState) {
    val displayUnit = state.profile.preferredUnit.toWeightUnit()
    val activeLifts = state.lifts.filterNot { it.archived }
    val activeLiftIds = activeLifts.map { it.id }
    val preferredLiftId = state.entries
        .firstOrNull { it.liftId in activeLiftIds }
        ?.liftId
        ?: activeLiftIds.firstOrNull()
        ?: 0L
    var selectedLiftId by remember { mutableStateOf<Long?>(null) }
    val effectiveSelectedLiftId = selectedLiftId?.takeIf { it in activeLiftIds } ?: preferredLiftId
    val selectedLift = activeLifts.firstOrNull { it.id == effectiveSelectedLiftId }
    val entries = state.entries
        .filter { it.liftId == effectiveSelectedLiftId }
        .sortedByDescending { it.performedAt }
    var selectedRepChart by remember { mutableStateOf<Int?>(null) }
    val repMaxes = (1..10).map { reps ->
        reps to entries
            .filter { it.reps == reps }
            .maxByOrNull { it.weightKg }
    }
    val selectedRepEntries = selectedRepChart?.let { reps ->
        entries.filter { it.reps == reps }.sortedBy { it.performedAt }
    }.orEmpty()

    if (selectedRepChart != null && selectedLift != null) {
        AlertDialog(
            onDismissRequest = { selectedRepChart = null },
            title = { Text("${selectedLift.name} ${selectedRepChart}RM") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressChart(
                        points = selectedRepEntries.map {
                            ChartPoint(
                                value = it.weightKg.kgTo(displayUnit),
                                performedAt = it.performedAt,
                            )
                        },
                        yAxisLabel = "Weight (${displayUnit.label})",
                        valueLabel = displayUnit.label,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRepChart = null }) {
                    Text("Close")
                }
            },
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            LiftPicker(activeLifts, effectiveSelectedLiftId) { selectedLiftId = it }
        }
        if (selectedLift == null) {
            item {
                Text("Add lifts from Profile to start tracking.")
            }
            return@LazyColumn
        }

        item {
            Text(selectedLift.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (selectedLift.major) AssistChip(onClick = {}, label = { Text("Major lift") })
        }
        item {
            Text("Rep maxes", fontWeight = FontWeight.SemiBold)
        }
        item {
            MetricGrid(
                repMaxes.map { (reps, entry) ->
                    MetricItem(
                        label = "Best ${reps}RM",
                        value = entry?.weightKg?.formatWeight(displayUnit) ?: "-",
                        supporting = entry?.performedDate(),
                        onClick = entry?.let { { selectedRepChart = reps } },
                    )
                },
            )
        }
        item {
            Text("History", fontWeight = FontWeight.SemiBold)
        }
        if (entries.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))) {
                    Text("No entries for this lift yet.", modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            items(entries) { EntryRow(it, displayUnit, onDelete = null) }
        }
    }
}

@Composable
private fun MetricGrid(metrics: List<MetricItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { metric ->
                    val cardModifier = if (metric.onClick == null) {
                        Modifier.weight(1f)
                    } else {
                        Modifier
                            .weight(1f)
                            .clickable(onClick = metric.onClick)
                    }
                    Card(
                        modifier = cardModifier,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(metric.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                metric.value,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            metric.supporting?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class MetricItem(
    val label: String,
    val value: String,
    val supporting: String? = null,
    val onClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogScreen(state: AppState, viewModel: AppViewModel) {
    var selectedLiftId by remember(state.lifts) { mutableStateOf(state.lifts.firstOrNull { !it.archived }?.id ?: 0) }
    var weight by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("5") }
    var bodyweight by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var logError by remember { mutableStateOf<String?>(null) }
    var performedAt by remember { mutableStateOf(todayStartMillis()) }
    var unit by remember(state.profile.preferredUnit) { mutableStateOf(state.profile.preferredUnit.toWeightUnit()) }
    val savedBodyweight = state.profile.bodyweightKg?.kgTo(unit)?.oneDecimal()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            LiftPicker(state.lifts.filterNot { it.archived }, selectedLiftId) { selectedLiftId = it }
            DatePickerField(performedAt) { performedAt = it }
            UnitPicker(unit) { unit = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Weight", weight, { weight = it }, Modifier.weight(1f))
                NumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
                NumberField("Reps", reps, { reps = it }, Modifier.weight(1f))
            }
            NumberField(
                if (state.profile.bodyweightKg == null) "Bodyweight required" else "Bodyweight",
                bodyweight,
                {
                    bodyweight = it
                    logError = null
                },
                Modifier.fillMaxWidth(),
            )
            Text(
                if (savedBodyweight == null) {
                    "Enter bodyweight once. It will be saved for future logs."
                } else {
                    "Saved bodyweight: $savedBodyweight ${unit.label}. Enter a new bodyweight to update it."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            logError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (state.profile.bodyweightKg == null && bodyweight.toDoubleOrNull() == null) {
                        logError = "Bodyweight is required until one is saved."
                        return@Button
                    }
                    viewModel.addEntry(
                        liftId = selectedLiftId,
                        weight = weight.toDoubleOrNull() ?: 0.0,
                        unit = unit,
                        sets = sets.toIntOrNull() ?: 0,
                        reps = reps.toIntOrNull() ?: 0,
                        bodyweight = bodyweight.toDoubleOrNull(),
                        notes = notes,
                        performedAt = performedAt,
                    )
                    weight = ""
                    bodyweight = ""
                    notes = ""
                    logError = null
                    performedAt = todayStartMillis()
                },
                enabled = selectedLiftId != 0L,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Log lift")
            }
        }
        items(state.entries) { EntryRow(it, unit) { viewModel.deleteEntry(it.id) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(performedAt: Long, onDateSelected: (Long) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = performedAt.toUtcDateMillis(),
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.toLocalStartOfDayMillis()
                            ?.let(onDateSelected)
                        showPicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CalendarToday, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(DateFormat.getDateInstance().format(Date(performedAt)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiftPicker(lifts: List<LiftEntity>, selectedLiftId: Long, onSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = lifts.firstOrNull { it.id == selectedLiftId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "Select lift",
            onValueChange = {},
            readOnly = true,
            label = { Text("Lift") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lifts.forEach { lift ->
                DropdownMenuItem(
                    text = { Text(lift.name) },
                    onClick = {
                        onSelected(lift.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ProgressScreen(state: AppState) {
    val displayUnit = state.profile.preferredUnit.toWeightUnit()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.lifts.filterNot { it.archived }.forEach { lift ->
            val entries = state.entries.filter { it.liftId == lift.id }.sortedBy { it.performedAt }
            item {
                Text(lift.name, fontWeight = FontWeight.SemiBold)
                ProgressChart(
                    points = entries.map {
                        ChartPoint(
                            value = it.estimatedOneRmKg.kgTo(displayUnit),
                            performedAt = it.performedAt,
                        )
                    },
                    yAxisLabel = "e1RM (${displayUnit.label})",
                    valueLabel = displayUnit.label,
                )
            }
        }
    }
}

@Composable
private fun ProfileScreen(state: AppState, viewModel: AppViewModel) {
    val profileState by viewModel.profileState.collectAsState()
    var sex by remember(state.profile.sex) { mutableStateOf(if (state.profile.sex == "female") Sex.Female else Sex.Male) }
    var unit by remember(state.profile.preferredUnit) { mutableStateOf(state.profile.preferredUnit.toWeightUnit()) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Sex category", fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow {
                Sex.entries.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = sex == value,
                        onClick = { sex = value },
                        shape = SegmentedButtonDefaults.itemShape(index, Sex.entries.size),
                    ) { Text(value.label) }
                }
            }
        }
        item {
            Text("Preferred unit", fontWeight = FontWeight.SemiBold)
            UnitPicker(unit) { unit = it }
        }
        item {
            Button(
                onClick = { viewModel.saveProfile(sex, unit) },
                enabled = !profileState.saving,
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (profileState.saving) "Saving..." else "Save profile")
            }
            profileState.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            profileState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item {
            LiftConfigurationSection(state.lifts, viewModel)
        }
    }
}

@Composable
private fun LiftConfigurationSection(lifts: List<LiftEntity>, viewModel: AppViewModel) {
    var name by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Lifts", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Lift name") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                viewModel.addLift(name)
                name = ""
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
        lifts.forEach { lift ->
            ListItem(
                headlineContent = { Text(lift.name) },
                supportingContent = { Text(if (lift.archived) "Archived" else if (lift.major) "Major lift" else "Accessory lift") },
                leadingContent = {
                    IconButton(onClick = { viewModel.toggleMajor(lift) }) {
                        Icon(if (lift.major) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Toggle major")
                    }
                },
                trailingContent = {
                    IconButton(onClick = { viewModel.toggleArchived(lift) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Archive")
                    }
                },
            )
        }
    }
}

@Composable
private fun BackupScreen(
    backupState: BackupUiState,
    viewModel: AppViewModel,
    onSignInRequested: () -> Unit,
    onRestoreCompleted: () -> Unit,
) {
    var confirmRestore by remember { mutableStateOf(false) }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from Google Drive?") },
            text = { Text("This replaces the local database with the Drive backup.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        viewModel.restoreNow(onRestoreCompleted)
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Google Drive", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        backupState.accountEmail == null -> "Not signed in"
                        backupState.driveReady -> "${backupState.accountEmail} - Drive ready"
                        else -> "${backupState.accountEmail} - Drive authorization pending"
                    },
                    color = if (backupState.driveReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                backupState.latestBackup?.let {
                    Text("Last backup: ${it.modifiedTime.ifBlank { "unknown" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (it.size > 0) Text("Size: ${it.size} bytes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                backupState.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurface)
                }
                if (backupState.busy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text("Working...")
                    }
                }
            }
        }

        if (backupState.accountEmail == null) {
            Button(onClick = onSignInRequested, enabled = !backupState.busy) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign in")
            }
        } else {
            Button(onClick = { viewModel.backupNow() }, enabled = !backupState.busy && backupState.driveReady) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Back up now")
            }
            OutlinedButton(
                onClick = { confirmRestore = true },
                enabled = !backupState.busy && backupState.driveReady && backupState.latestBackup != null,
            ) {
                Text("Restore from Drive")
            }
            OutlinedButton(onClick = { viewModel.refreshBackupStatus() }, enabled = !backupState.busy) {
                Text("Refresh status")
            }
            if (!backupState.driveReady) {
                Text("If this stays pending, configure the Google Cloud Android OAuth client for com.prtracker and this APK's SHA-1.", color = MaterialTheme.colorScheme.secondary)
            }
            TextButton(onClick = { viewModel.signOut() }, enabled = !backupState.busy) {
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun UnitPicker(unit: WeightUnit, onUnit: (WeightUnit) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        WeightUnit.entries.forEachIndexed { index, value ->
            SegmentedButton(
                selected = unit == value,
                onClick = { onUnit(value) },
                shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
            ) { Text(value.label) }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValue(text.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        modifier = modifier,
    )
}

@Composable
private fun EntryRow(entry: EntryWithLift, displayUnit: WeightUnit, onDelete: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))) {
        ListItem(
            headlineContent = { Text("${entry.liftName}: ${entry.sets}x${entry.reps} @ ${entry.weightKg.formatWeight(displayUnit)}") },
            supportingContent = {
                Text("${DateFormat.getDateInstance().format(Date(entry.performedAt))} | e1RM ${entry.estimatedOneRmKg.formatWeight(displayUnit)} | Wilks ${entry.wilks?.oneDecimal() ?: "-"}")
            },
            leadingContent = {
                if (entry.isPr) AssistChip(onClick = {}, label = { Text("PR") })
            },
            trailingContent = {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete entry")
                    }
                }
            },
        )
    }
}

@Composable
private fun ProgressChart(points: List<ChartPoint>, yAxisLabel: String, valueLabel: String) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val values = points.map { it.value }
    val shortDateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (points.size < 2) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(yAxisLabel, color = axisColor, style = MaterialTheme.typography.labelMedium)
                Text("Add at least two entries", color = axisColor)
                points.firstOrNull()?.let {
                    Text(
                        "${it.value.oneDecimal()} $valueLabel on ${shortDateFormat.format(Date(it.performedAt))}",
                        color = axisColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        } else {
            val min = values.minOrNull() ?: 0.0
            val maxValue = max(values.maxOrNull() ?: 0.0, min + 1.0)
            val latest = points.last()
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(yAxisLabel, color = axisColor, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${maxValue.oneDecimal()} / ${min.oneDecimal()} $valueLabel",
                        color = axisColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    val axisInset = 18.dp.toPx()
                    val plotLeft = axisInset
                    val plotTop = 4.dp.toPx()
                    val plotRight = size.width - 4.dp.toPx()
                    val plotBottom = size.height - axisInset
                    val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
                    val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
                    val xStep = plotWidth / (points.lastIndex).coerceAtLeast(1)
                    val path = Path()

                    drawLine(
                        color = axisColor,
                        start = Offset(plotLeft, plotTop),
                        end = Offset(plotLeft, plotBottom),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = axisColor,
                        start = Offset(plotLeft, plotBottom),
                        end = Offset(plotRight, plotBottom),
                        strokeWidth = 1.dp.toPx(),
                    )

                    points.forEachIndexed { index, point ->
                        val x = plotLeft + (index * xStep)
                        val y = plotBottom - (((point.value - min) / (maxValue - min)).toFloat() * plotHeight)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        if (points.size <= 24 || index == 0 || index == points.lastIndex) {
                            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
                        }
                    }
                    drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        shortDateFormat.format(Date(points.first().performedAt)),
                        color = axisColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        shortDateFormat.format(Date(latest.performedAt)),
                        color = axisColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    "Latest: ${latest.value.oneDecimal()} $valueLabel on ${shortDateFormat.format(Date(latest.performedAt))}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private data class ChartPoint(
    val value: Double,
    val performedAt: Long,
)

private fun Double.formatWeight(unit: WeightUnit): String = "${kgTo(unit).oneDecimal()} ${unit.label}"
private fun Double.oneDecimal(): String = "%.1f".format(this)
private fun EntryWithLift.performedDate(): String = DateFormat.getDateInstance().format(Date(performedAt))

private fun todayStartMillis(): Long {
    return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun Long.toUtcDateMillis(): Long {
    val localDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun Long.toLocalStartOfDayMillis(): Long {
    val selectedDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    return selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
