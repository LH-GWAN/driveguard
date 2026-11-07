package kr.driveguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kr.driveguard.App
import kr.driveguard.R
import kr.driveguard.core.RescueEvent
import kr.driveguard.core.RescueState
import kr.driveguard.data.MedicinePlace
import kr.driveguard.data.Profile
import kr.driveguard.data.Settings
import kr.driveguard.service.RescueView

private val TouchHeight = 76.dp

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 40.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Body(text: String) = Text(text, fontSize = 26.sp)

@Composable
private fun Note(text: String) = Text(text, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = TouchHeight)) { Text(text, fontSize = 28.sp) }
}

@Composable
private fun Secondary(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier = Modifier.fillMaxWidth().heightIn(min = TouchHeight)) { Text(text, fontSize = 26.sp) }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchHeight).selectable(checked, onClick = { onChange(!checked) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Checkbox(checked, onCheckedChange = null)
        Text(text, fontSize = 24.sp)
    }
}

@Composable
fun ConsentScreen(onAgree: () -> Unit) {
    var health by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf(false) }
    var guardian by remember { mutableStateOf(false) }
    Page {
        Title(stringResource(R.string.consent_title))
        Note(stringResource(R.string.tagline))
        CheckRow(stringResource(R.string.consent_health), health) { health = it }
        CheckRow(stringResource(R.string.consent_location), location) { location = it }
        CheckRow(stringResource(R.string.consent_guardian), guardian) { guardian = it }
        Primary(stringResource(R.string.consent_next), enabled = health && location && guardian, onClick = onAgree)
    }
}

@Composable
fun SetupScreen(app: App, canGoBack: Boolean, onDone: () -> Unit) {
    val settings = app.settings
    val saved = remember { app.store.load() ?: Profile() }
    var driver by remember { mutableStateOf(saved.driver) }
    var history by remember { mutableStateOf(saved.history) }
    var medication by remember { mutableStateOf(saved.medication) }
    var place by remember { mutableStateOf(saved.medicinePlace) }
    var other by remember { mutableStateOf(saved.medicineOther) }
    var guardianName by remember { mutableStateOf(saved.guardianName) }
    var guardianPhone by remember { mutableStateOf(saved.guardianPhone) }
    var wait by remember { mutableStateOf(settings.quietSeconds.toString()) }
    var windowOn by remember { mutableStateOf(settings.windowEnabled) }
    var percent by remember { mutableStateOf(settings.windowPercent.toString()) }
    val sampleDriver = stringResource(R.string.sample_driver)
    val sampleHistory = stringResource(R.string.sample_history)
    val sampleMedication = stringResource(R.string.sample_medication)
    val sampleGuardian = stringResource(R.string.sample_guardian_name)
    val samplePhone = stringResource(R.string.sample_guardian_phone)

    Page {
        Title(stringResource(R.string.setup_title))
        Note(stringResource(R.string.setup_virtual))
        Secondary(stringResource(R.string.setup_sample)) {
            driver = sampleDriver
            history = sampleHistory
            medication = sampleMedication
            place = MedicinePlace.GLOVE_BOX
            guardianName = sampleGuardian
            guardianPhone = samplePhone
        }
        Field(stringResource(R.string.setup_driver), driver) { driver = it }
        Field(stringResource(R.string.setup_history), history) { history = it }
        Field(stringResource(R.string.setup_medication), medication) { medication = it }
        Body(stringResource(R.string.setup_medicine_place))
        PlaceOption(stringResource(R.string.setup_medicine_none), place == MedicinePlace.NONE) { place = MedicinePlace.NONE }
        PlaceOption(stringResource(R.string.setup_medicine_glove), place == MedicinePlace.GLOVE_BOX) { place = MedicinePlace.GLOVE_BOX }
        PlaceOption(stringResource(R.string.setup_medicine_other), place == MedicinePlace.OTHER) { place = MedicinePlace.OTHER }
        if (place == MedicinePlace.OTHER) Field(stringResource(R.string.setup_medicine_other_text), other) { other = it }
        Field(stringResource(R.string.setup_guardian_name), guardianName) { guardianName = it }
        Field(stringResource(R.string.setup_guardian_phone), guardianPhone, KeyboardType.Phone) { guardianPhone = it }
        Field(stringResource(R.string.setup_wait), wait, KeyboardType.Number) { wait = it.filter(Char::isDigit) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Switch(windowOn, onCheckedChange = { windowOn = it })
            Body(stringResource(R.string.setup_window))
        }
        if (windowOn) Field(stringResource(R.string.setup_window_ratio), percent, KeyboardType.Number) { percent = it.filter(Char::isDigit) }
        Primary(stringResource(R.string.setup_save), enabled = driver.isNotBlank() && guardianName.isNotBlank()) {
            app.store.save(Profile(driver, history, medication, place, other, guardianName, guardianPhone))
            settings.quietSeconds = wait.toIntOrNull() ?: Settings.MIN_QUIET_SECONDS
            settings.windowEnabled = windowOn
            settings.windowPercent = percent.toIntOrNull() ?: 1
            settings.lastEditedAt = System.currentTimeMillis()
            onDone()
        }
        if (canGoBack) Secondary(stringResource(R.string.setup_back), onDone)
    }
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 20.sp) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 26.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlaceOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).selectable(selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected, onClick = null)
        Body(text)
    }
}

@Composable
fun HomeScreen(
    app: App,
    view: RescueView,
    permissionsMissing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    val settings = app.settings
    val driver = remember { app.store.load()?.driver.orEmpty() }
    var lastEdited by remember { mutableStateOf(settings.lastEditedAt) }
    Page {
        Title(stringResource(R.string.app_name))
        Note(stringResource(R.string.tagline))
        Note(stringResource(R.string.virtual_notice))
        if (!view.running) {
            Body(stringResource(R.string.home_off))
            Body(stringResource(R.string.home_confirm_driver, driver))
            Primary(stringResource(R.string.home_confirm_yes), onClick = onStart)
            Secondary(stringResource(R.string.home_delete)) {
                app.store.delete()
                settings.clear()
                onDeleted()
            }
        } else {
            Body(stringResource(R.string.home_on))
            Body(stringResource(R.string.home_state, stateLabel(view.state)))
            Note(stringResource(R.string.home_vehicle, gearLabel(view.vehicle.park), movingLabel(view.vehicle.stopped)))
            if (permissionsMissing) Note(stringResource(R.string.home_permission))
            Primary(stringResource(R.string.home_help)) { app.hub.send(RescueEvent.UserHelp) }
            if (view.guardianNotified) Secondary(stringResource(R.string.home_cancel_alert)) { app.hub.send(RescueEvent.UserCancel) }
            Secondary(stringResource(R.string.home_logout), onStop)
        }
        Body(stringResource(R.string.home_hidden))
        Note(stringResource(R.string.home_last_edit, formatDate(lastEdited)))
        if (System.currentTimeMillis() - lastEdited > Settings.RECONFIRM_MILLIS) {
            Body(stringResource(R.string.home_reconfirm))
            Secondary(stringResource(R.string.home_reconfirm_done)) {
                lastEdited = System.currentTimeMillis()
                settings.lastEditedAt = lastEdited
            }
        }
        if (view.running) Secondary(stringResource(R.string.home_edit), onEdit)
    }
}

/** 구조 단계에서만 불린다. 입력된 사실만 보여 주고 추정하거나 지시하지 않는다. */
@Composable
fun RescueScreen(app: App, view: RescueView) {
    val profile = remember { app.store.load() }
    val unknown = stringResource(R.string.rescue_unknown)
    Page {
        Title(stringResource(R.string.rescue_title))
        Item(stringResource(R.string.rescue_history), profile?.history.orEmpty().ifBlank { unknown })
        Item(stringResource(R.string.rescue_medication), profile?.medication.orEmpty().ifBlank { unknown })
        Item(stringResource(R.string.rescue_medicine_place), medicineLabel(profile).ifBlank { unknown })
        Item(stringResource(R.string.rescue_guardian), listOfNotNull(profile?.guardianName, profile?.guardianPhone).joinToString(" "))
        Item(stringResource(R.string.rescue_last_response), view.lastResponseAtMillis?.let(::formatTime) ?: unknown)
        Item(stringResource(R.string.rescue_stopped), view.stoppedAtMillis?.let(::formatTime) ?: unknown)
        Note(stringResource(R.string.rescue_guide_title))
        Body(stringResource(R.string.rescue_guide))
        Note(stringResource(R.string.virtual_notice))
        Primary(stringResource(R.string.rescue_end)) { app.hub.send(RescueEvent.UserEnd) }
    }
}

@Composable
private fun Item(label: String, value: String) {
    Column {
        Note(label)
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun medicineLabel(profile: Profile?): String = when (profile?.medicinePlace) {
    MedicinePlace.GLOVE_BOX -> stringResource(R.string.setup_medicine_glove)
    MedicinePlace.OTHER -> profile.medicineOther
    MedicinePlace.NONE -> stringResource(R.string.setup_medicine_none)
    null -> ""
}

@Composable
private fun stateLabel(state: RescueState): String = stringResource(
    when (state) {
        RescueState.IDLE -> R.string.state_idle
        RescueState.REQUESTED -> R.string.state_requested
        RescueState.ASK1, RescueState.ASK2 -> R.string.state_ask
        RescueState.ALERTED -> R.string.state_alerted
        RescueState.UNLOCK_NOTICE -> R.string.state_notice
        RescueState.RESCUE -> R.string.state_rescue
    },
)

@Composable
private fun gearLabel(park: Boolean?): String = stringResource(
    when (park) {
        true -> R.string.gear_park
        false -> R.string.gear_other
        null -> R.string.gear_unknown
    },
)

@Composable
private fun movingLabel(stopped: Boolean?): String = stringResource(
    when (stopped) {
        true -> R.string.moving_stopped
        false -> R.string.moving_moving
        null -> R.string.moving_unknown
    },
)

private fun formatDate(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date(millis))

private fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(millis))
