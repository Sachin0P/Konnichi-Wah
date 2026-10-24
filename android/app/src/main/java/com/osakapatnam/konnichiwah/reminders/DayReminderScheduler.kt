package com.osakapatnam.konnichiwah.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.osakapatnam.konnichiwah.data.LocalItinerary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// Schedules one WorkManager job per trip day, to fire the evening before.
// WorkManager keeps them across reboots and app restarts, and they need no
// network.
@Singleton
class DayReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    // Replaces every scheduled reminder with ones for this trip. Call it when
    // the trip, its start date or its number of days changes. Edits to the
    // stops don't need it: the worker reads the trip from Room when it runs.
    fun schedule(itinerary: LocalItinerary?) {
        val workManager = WorkManager.getInstance(context)

        // WorkManager runs these operations in order, so the old reminders
        // are gone before the new ones are added.
        workManager.cancelAllWorkByTag(TAG)

        val startDate = itinerary?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val now = Instant.now()

        for (reminder in dayReminders(startDate, itinerary.days.size, now)) {
            val request = OneTimeWorkRequestBuilder<DayReminderWorker>()
                .setInitialDelay(Duration.between(now, reminder.at))
                .setInputData(
                    workDataOf(
                        DayReminderWorker.KEY_LOCAL_ID to itinerary.localId,
                        DayReminderWorker.KEY_DAY_INDEX to reminder.dayIndex,
                        DayReminderWorker.KEY_DATE to reminder.date.toString(),
                    ),
                )
                .addTag(TAG)
                .build()

            workManager.enqueueUniqueWork("$TAG-${reminder.dayIndex}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    companion object {
        private const val TAG = "day-reminder"
    }
}
