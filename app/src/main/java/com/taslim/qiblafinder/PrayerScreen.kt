package com.taslim.qiblafinder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.batoulapps.adhan.Prayer
import com.taslim.qiblafinder.ui.theme.MihrabType
import com.taslim.qiblafinder.ui.theme.QiblaTheme
import java.util.Date

@Composable
fun PrayerScreen(
    settings: PrayerSettingsStore,
    locationState: LocationState,
    actions: AppActions,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val colors = QiblaTheme.colors
    val now by rememberNow()
    val location = locationState.location
    val day = startOfDay(now)
    val schedule = remember(location, settings.madhab, settings.calculation, day) {
        location?.let {
            buildDaySchedule(it.latitude, it.longitude, settings.madhab, settings.calculation, Date(day))
        }
    }

    // A bell tapped before notifications are allowed is turned on once they are
    var pendingReminder by remember { mutableStateOf<Prayer?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val prayer = pendingReminder
        pendingReminder = null
        if (granted && prayer != null) {
            settings.setReminder(prayer, true)
        } else if (!granted) {
            Toast.makeText(context, "Allow notifications to get prayer reminders", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }
    val onToggleReminder: (Prayer, Boolean) -> Unit = { prayer, enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PrayerSettingsStore.hasNotificationPermission(context)
        ) {
            pendingReminder = prayer
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            settings.setReminder(prayer, enabled)
        }
    }

    MihrabBackground(glowHeight = 0.34f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Prayer times", style = MaterialTheme.typography.headlineLarge, color = colors.text)
                    Spacer(Modifier.height(6.dp))
                    Text(gregorianDate(now), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = remember(day) { hijriDate(now) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.gold
                    )
                }
                Spacer(Modifier.width(12.dp))
                RoundIconButton(
                    icon = MihrabIcons.Sliders,
                    contentDescription = "Calculation settings",
                    onClick = onOpenSettings
                )
            }
            Spacer(Modifier.height(22.dp))

            if (schedule == null) {
                LocationNeededCard(locationState, actions)
            } else {
                NextPrayerCard(
                    schedule = schedule,
                    now = now,
                    city = locationState.placeName?.substringBefore(",")
                )
                Spacer(Modifier.height(18.dp))
                PrayerList(
                    schedule = schedule,
                    now = now,
                    reminders = settings.reminders,
                    onToggleReminder = onToggleReminder
                )
                Text(
                    text = "${settings.calculation.title} · ${settings.madhab.asrLabel} Asr",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, letterSpacing = 0.04.em),
                    color = colors.textFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Change calculation method", onClick = onOpenSettings)
                        .padding(vertical = 12.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NextPrayerCard(schedule: DaySchedule, now: Date, city: String?) {
    val colors = QiblaTheme.colors
    val formatter = rememberTimeFormatter()
    val next = schedule.nextPrayer(now)
    val previous = schedule.previousSlot(now)
    val progress = schedule.progress(now)

    MihrabCard(glow = true, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Next prayer", modifier = Modifier.weight(1f))
            Text(
                text = listOfNotNull(if (next.isTomorrow) "Tomorrow" else "Today", city).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.textSoft
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = next.prayer.displayName,
                style = MaterialTheme.typography.displaySmall,
                color = colors.text,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = next.prayer.arabicName,
                style = MihrabType.arabic.copy(fontSize = 28.sp, lineHeight = 40.sp, textDirection = TextDirection.Rtl),
                color = colors.gold
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildAnnotatedString {
                append("Begins in ")
                withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold)) {
                    append(formatCountdown(now, next.time))
                }
                append(" · ${formatter.full(next.time)}")
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum"),
            color = colors.textSoft
        )

        Spacer(Modifier.height(18.dp))
        ProgressLine(progress)
        Spacer(Modifier.height(8.dp))
        Row {
            Text(
                text = "${previous.prayer.displayName} ${formatter.full(previous.time)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.textMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${next.prayer.displayName} ${formatter.full(next.time)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.textMuted
            )
        }
        if (schedule.currentPrayer(now) == Prayer.FAJR) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Fajr ends at sunrise, ${formatter.full(schedule.sunrise())}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.goldLight
            )
        }
    }
}

/** A hairline track with a glowing bead marking how far through this prayer's time we are. */
@Composable
private fun ProgressLine(progress: Float) {
    val colors = QiblaTheme.colors
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics { contentDescription = "${(progress * 100).toInt()}% of the way to the next prayer" }
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(colors.text.copy(alpha = 0.14f))
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .clip(CircleShape)
                .background(colors.gold)
        )
        Box(
            Modifier
                .offset(x = (maxWidth - 8.dp) * progress.coerceIn(0f, 1f))
                .size(8.dp)
                .shadow(6.dp, CircleShape, ambientColor = colors.gold, spotColor = colors.gold)
                .background(colors.goldLight, CircleShape)
        )
    }
}

private enum class RowState { Passed, Current, Upcoming }

@Composable
private fun PrayerList(
    schedule: DaySchedule,
    now: Date,
    reminders: Set<Prayer>,
    onToggleReminder: (Prayer, Boolean) -> Unit
) {
    val formatter = rememberTimeFormatter()
    val current = schedule.currentPrayer(now)
    val states = schedule.today.map { slot ->
        when {
            // Before Fajr the current prayer is yesterday's Isha, not today's
            slot.prayer == current && !slot.time.after(now) -> RowState.Current
            !slot.time.after(now) -> RowState.Passed
            else -> RowState.Upcoming
        }
    }

    Column {
        schedule.today.forEachIndexed { index, slot ->
            PrayerRow(
                slot = slot,
                state = states[index],
                formatter = formatter,
                reminderOn = slot.prayer in reminders,
                onToggleReminder = { onToggleReminder(slot.prayer, it) }
            )
            // The highlighted row is its own block, so no rule touches it
            if (index < schedule.today.lastIndex &&
                states[index] != RowState.Current && states[index + 1] != RowState.Current
            ) {
                Hairline()
            }
        }
    }
}

@Composable
private fun PrayerRow(
    slot: PrayerSlot,
    state: RowState,
    formatter: TimeFormatter,
    reminderOn: Boolean,
    onToggleReminder: (Boolean) -> Unit
) {
    val colors = QiblaTheme.colors
    val isCurrent = state == RowState.Current
    val passed = state == RowState.Passed
    val canRemind = slot.prayer in PrayerSettingsStore.REMINDER_PRAYERS

    val nameColor = when {
        isCurrent -> colors.gold
        passed -> colors.textFaint
        else -> colors.text
    }
    val arabicColor = when {
        isCurrent -> colors.gold
        passed -> colors.textFaint
        else -> colors.textMuted
    }
    val timeColor = if (passed) colors.textFaint else colors.text
    val shape = RoundedCornerShape(16.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (isCurrent) {
                    Modifier
                        .clip(shape)
                        .background(colors.gold.copy(alpha = 0.09f))
                        .border(1.dp, colors.gold.copy(alpha = 0.22f), shape)
                } else {
                    Modifier
                }
            )
            .padding(start = 14.dp, end = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {}
        ) {
            Text(
                text = slot.prayer.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                color = nameColor,
                modifier = Modifier.alignByBaseline()
            )
            Text(
                text = slot.prayer.arabicName,
                style = MihrabType.arabic.copy(textDirection = TextDirection.Rtl),
                color = arabicColor,
                modifier = Modifier.alignByBaseline()
            )
            if (isCurrent) Tag("Now")
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatter.clock(slot.time),
                style = MaterialTheme.typography.headlineSmall,
                color = timeColor
            )
            formatter.meridiem(slot.time)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(start = 3.dp, bottom = 5.dp)
                )
            }
        }
        if (canRemind) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .toggleable(value = reminderOn, role = Role.Switch, onValueChange = onToggleReminder)
                    .semantics {
                        contentDescription = "${slot.prayer.displayName} reminder"
                        stateDescription = if (reminderOn) "On" else "Off"
                    }
            ) {
                Icon(
                    imageVector = if (reminderOn) MihrabIcons.Bell else MihrabIcons.BellOff,
                    contentDescription = null,
                    tint = if (reminderOn) colors.gold else colors.textFaint,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            // Sunrise marks the end of Fajr rather than a prayer, so it has no reminder
            Spacer(Modifier.size(44.dp))
        }
    }
}
