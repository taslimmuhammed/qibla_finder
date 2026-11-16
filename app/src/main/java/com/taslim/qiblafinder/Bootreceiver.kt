package com.taslim.qiblafinder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores the notification and alarms after events that clear or invalidate them: a reboot,
 * an app update, or a change to the clock or time zone (the day's times move with it).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        if (!PrayerSettingsStore.isOnboardingComplete(context)) return

        PrayerTimesNotification.update(context)
        PrayerAlarmScheduler.sync(context)
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
