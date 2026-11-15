package com.taslim.qiblafinder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.batoulapps.adhan.Madhab
import com.taslim.qiblafinder.ui.theme.Midnight
import com.taslim.qiblafinder.ui.theme.QiblaTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: PrayerSettingsStore,
    locationState: LocationState,
    actions: AppActions,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = QiblaTheme.colors
    var showMethodPicker by rememberSaveable { mutableStateOf(false) }
    var showTips by rememberSaveable { mutableStateOf(false) }
    var showCitySearch by rememberSaveable { mutableStateOf(false) }
    val recommended = locationState.countryCode?.let(RegionDefaults::methodFor)

    // What to switch on once the notification permission comes back granted
    var pendingEnable by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingEnable?.invoke()
        } else {
            // Take the user straight to the switch they need instead of leaving them stuck
            Toast.makeText(context, "Allow notifications to get prayer alerts", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
        pendingEnable = null
    }
    fun withNotificationPermission(enable: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PrayerSettingsStore.hasNotificationPermission(context)
        ) {
            pendingEnable = enable
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enable()
        }
    }

    MihrabBackground(glowHeight = 0.3f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            RoundIconButton(icon = MihrabIcons.Back, contentDescription = "Back", onClick = onBack)
            Spacer(Modifier.height(20.dp))
            Text("Settings", style = MaterialTheme.typography.headlineLarge, color = colors.text)
            Spacer(Modifier.height(28.dp))

            CardCaption("Location")
            MihrabCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = locationState.placeName
                                ?: if (locationState.location != null) "Saved location" else "Not set",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text
                        )
                        Text(
                            text = when {
                                locationState.location == null -> locationState.status.title
                                locationState.isManual -> "Chosen city"
                                locationState.accuracyMeters > 0f ->
                                    "Live location · accurate to ${locationState.accuracyMeters.roundToInt()} m"
                                else -> "Live location"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    RoundIconButton(
                        icon = MihrabIcons.Crosshair,
                        contentDescription = "Use my current location",
                        tint = colors.gold,
                        onClick = { actions.resolveLocation(locationState) }
                    )
                }
                Hairline(Modifier.padding(horizontal = 20.dp))
                SettingsRow(
                    title = "Choose a city",
                    subtitle = "Set your location by hand, without GPS",
                    onClick = { showCitySearch = true }
                )
            }

            Spacer(Modifier.height(24.dp))
            CardCaption("Prayer times")
            MihrabCard {
                SettingsRow(
                    title = "Calculation method",
                    subtitle = settings.calculation.title,
                    badge = if (settings.calculation == recommended) "Recommended" else null,
                    onClick = { showMethodPicker = true }
                )
                Hairline(Modifier.padding(horizontal = 20.dp))
                Column(Modifier.padding(20.dp)) {
                    Text("Asr time", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Spacer(Modifier.height(12.dp))
                    AsrSelector(selected = settings.madhab, onSelect = settings::updateMadhab)
                    Spacer(Modifier.height(10.dp))
                    Text(settings.madhab.asrDescription, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }

            Spacer(Modifier.height(24.dp))
            CardCaption("Notifications")
            MihrabCard {
                val reminders = settings.reminders
                SwitchRow(
                    title = "Prayer reminders",
                    subtitle = when {
                        reminders.isEmpty() -> "An alert as each prayer time begins"
                        reminders.size == PrayerSettingsStore.REMINDER_PRAYERS.size -> "On for all five prayers"
                        else -> "On for " + PrayerSettingsStore.REMINDER_PRAYERS
                            .filter { it in reminders }
                            .joinToString(", ") { it.displayName }
                    },
                    checked = reminders.isNotEmpty(),
                    onCheckedChange = { on ->
                        if (on) {
                            withNotificationPermission {
                                settings.updateReminders(PrayerSettingsStore.REMINDER_PRAYERS.toSet())
                            }
                        } else {
                            settings.updateReminders(emptySet())
                        }
                    }
                )
                Hairline(Modifier.padding(horizontal = 20.dp))
                SwitchRow(
                    title = "Today's times in the notification bar",
                    subtitle = "Silent, with the current prayer highlighted",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { on ->
                        if (on) {
                            withNotificationPermission { settings.updateNotificationsEnabled(true) }
                        } else {
                            settings.updateNotificationsEnabled(false)
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            CardCaption("Help")
            MihrabCard {
                SettingsRow(
                    title = "Compass tips",
                    subtitle = "How to get an accurate Qibla reading",
                    onClick = { showTips = true }
                )
                Hairline(Modifier.padding(horizontal = 20.dp))
                SettingsRow(
                    title = "Version",
                    subtitle = remember { appVersionName(context) },
                    onClick = null
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showMethodPicker) {
        MethodPickerSheet(
            selected = settings.calculation,
            recommended = recommended,
            onSelect = settings::updateCalculation,
            onDismiss = { showMethodPicker = false }
        )
    }
    if (showTips) {
        CompassTipsSheet(
            accuracyMeters = locationState.accuracyMeters,
            onDismiss = { showTips = false }
        )
    }
    if (showCitySearch) {
        CitySearchSheet(
            onPick = {
                showCitySearch = false
                actions.setManualLocation(it)
            },
            onDismiss = { showCitySearch = false },
            onUseCurrentLocation = {
                showCitySearch = false
                actions.requestLocation()
            }
        )
    }
}

/** A tappable row inside a [MihrabCard]: title, value underneath, and a chevron. */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    badge: String? = null
) {
    val colors = QiblaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
            badge?.let {
                Spacer(Modifier.height(6.dp))
                Tag(it)
            }
        }
        if (onClick != null) {
            Icon(MihrabIcons.Chevron, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/** A row whose whole surface toggles the switch, so the target is never just the thumb. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = QiblaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Midnight,
                checkedTrackColor = colors.gold,
                checkedBorderColor = colors.gold,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = colors.border
            )
        )
    }
}

/** Standard or Hanafi Asr, as a two-option capsule. */
@Composable
fun AsrSelector(selected: Madhab, onSelect: (Madhab) -> Unit) {
    val colors = QiblaTheme.colors
    val options = listOf(Madhab.SHAFI, Madhab.HANAFI)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .padding(4.dp)
            .selectableGroup()
    ) {
        options.forEach { madhab ->
            val isSelected = selected == madhab
            val fill by animateColorAsState(if (isSelected) colors.gold else Color.Transparent, tween(200), label = "asrFill")
            val text by animateColorAsState(if (isSelected) Midnight else colors.textSoft, tween(200), label = "asrText")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(madhab) })
            ) {
                Text(madhab.asrLabel, style = MaterialTheme.typography.titleSmall, color = text)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodPickerSheet(
    selected: CalculationOption,
    recommended: CalculationOption?,
    onSelect: (CalculationOption) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = QiblaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        contentColor = colors.text
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Calculation method", style = MaterialTheme.typography.headlineLarge, color = colors.text)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Regions use slightly different sun angles for Fajr and Isha. Choose the one your local mosque follows.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
            Spacer(Modifier.height(12.dp))
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .selectableGroup()
        ) {
            // The recommendation goes first so most people never need to scroll
            val ordered = listOfNotNull(recommended) + CalculationOption.entries.filter { it != recommended }
            items(ordered) { option ->
                MethodOptionRow(
                    option = option,
                    isSelected = option == selected,
                    isRecommended = option == recommended,
                    onClick = {
                        onSelect(option)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }
                )
            }
        }
    }
}

@Composable
private fun MethodOptionRow(
    option: CalculationOption,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    val colors = QiblaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.gold, unselectedColor = colors.textFaint),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) colors.goldLight else colors.text,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isRecommended) Tag("Recommended")
            }
            Text(option.regions, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.size(12.dp))
    }
}
