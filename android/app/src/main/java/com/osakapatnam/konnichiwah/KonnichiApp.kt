package com.osakapatnam.konnichiwah

import android.app.Application
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import com.osakapatnam.konnichiwah.reminders.DayReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KonnichiApp : Application() {

    @Inject lateinit var itineraries: ItineraryRepository
    @Inject lateinit var reminders: DayReminderScheduler

    // Lives as long as the app's process.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Keep the day-before reminders in step with the trip. This runs once
        // when the app starts and again whenever a new trip is made or its
        // dates or number of days change, wherever in the app that happens.
        // Stop edits don't matter here, because the reminder reads the stops
        // when it fires.
        appScope.launch {
            itineraries.latest
                .distinctUntilChangedBy { Triple(it?.localId, it?.startDate, it?.days?.size) }
                .collect { reminders.schedule(it) }
        }
    }
}
