package com.osakapatnam.konnichiwah.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.osakapatnam.konnichiwah.MainActivity
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import com.osakapatnam.konnichiwah.data.RulebookAssets
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate

// Shows the reminder for one trip day. It reads the trip from Room when it
// runs, rather than when it was scheduled, so it shows the stops as they are
// now, after any edits.
class DayReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    // WorkManager creates workers itself, so we fetch Hilt's singletons
    // through an entry point. (The alternative, @HiltWorker, needs the
    // androidx.hilt:hilt-work library and a custom WorkManager setup.)
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun itineraryRepository(): ItineraryRepository
        fun rulebookAssets(): RulebookAssets
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)

        val localId = inputData.getLong(KEY_LOCAL_ID, -1)
        val dayIndex = inputData.getInt(KEY_DAY_INDEX, -1)
        val date = inputData.getString(KEY_DATE)

        // Skip a reminder that's out of date: the trip was replaced or its
        // dates moved since it was scheduled. (Rescheduling cancels these, so
        // this is only a safety net.)
        val itinerary = deps.itineraryRepository().latest.first()
        if (itinerary == null || itinerary.localId != localId) return Result.success()
        val startDate = itinerary.startDate ?: return Result.success()
        if (LocalDate.parse(startDate).plusDays(dayIndex - 1L).toString() != date) return Result.success()

        val day = itinerary.days.getOrNull(dayIndex - 1) ?: return Result.success()

        if (!canNotify(applicationContext)) return Result.success()

        val text = reminderText(day, deps.rulebookAssets().rulebook())
        show(text, dayIndex)

        return Result.success()
    }

    private fun show(text: ReminderText, dayIndex: Int) {
        val context = applicationContext
        createChannel(context)

        // Tapping the notification opens the app on the trip.
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, dayIndex, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        // canNotify() checked the permission, but lint can't see that.
        try {
            NotificationManagerCompat.from(context).notify(dayIndex, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check and here.
        }
    }

    companion object {
        const val KEY_LOCAL_ID = "local_id"
        const val KEY_DAY_INDEX = "day_index"
        const val KEY_DATE = "date"

        private const val CHANNEL_ID = "day_reminders"

        // Notifications need a runtime permission from Android 13.
        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        // Creating a channel that already exists does nothing, so it's safe to
        // call before every notification.
        private fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Day-before reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "The evening before each trip day: tomorrow's stops and a culture tip."
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
