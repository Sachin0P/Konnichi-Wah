package com.osakapatnam.konnichiwah.data

import com.osakapatnam.konnichiwah.data.local.ItineraryDao
import com.osakapatnam.konnichiwah.data.local.ItineraryEntity
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.CreateItineraryRequestDto
import com.osakapatnam.konnichiwah.data.remote.DayDto
import com.osakapatnam.konnichiwah.data.remote.ItineraryDto
import com.osakapatnam.konnichiwah.data.remote.KonnichiApi
import com.osakapatnam.konnichiwah.data.remote.PlanDto
import com.osakapatnam.konnichiwah.data.remote.UpdateItineraryRequestDto
import com.osakapatnam.konnichiwah.data.remote.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// What the screens see: the phone's copy of a trip.
data class LocalItinerary(
    val localId: Long,
    val serverId: Long?,
    val title: String,
    val days: List<DayDto>,
    val pendingSync: Boolean,
)

// Offline-first: Room is the source of truth for the screens. Creating a trip
// needs the network (it follows straight on from the planner, which does
// too), but every edit is saved on the phone first and then synced with a
// PATCH. If the sync fails (no signal on the Shinkansen), the edit is kept
// and marked pendingSync, and we try again on the next edit or when the trip
// is opened.
@Singleton
class ItineraryRepository @Inject constructor(
    private val dao: ItineraryDao,
    private val api: KonnichiApi,
    private val json: Json,
) {
    // Only one sync runs at a time, so two quick edits can't race each other.
    private val syncMutex = Mutex()

    val latest: Flow<LocalItinerary?> = dao.observeLatest().map { it?.toLocal() }

    suspend fun hasItinerary(): Boolean = dao.count() > 0

    suspend fun create(title: String, plan: PlanDto, selectedIds: Set<String>): ApiResult<Long> {
        val request = CreateItineraryRequestDto(
            title = title,
            cities = plan.cities,
            suggestions = plan.suggestions.filter { it.id in selectedIds },
        )

        return when (val result = apiCall(json) { api.createItinerary(request).itinerary }) {
            is ApiResult.Success -> ApiResult.Success(dao.insert(result.value.toEntity()))
            is ApiResult.Failure -> result
        }
    }

    // Save the edit on the phone straight away, then try to sync it.
    suspend fun updateDays(localId: Long, days: List<DayDto>) {
        val entity = dao.get(localId) ?: return
        dao.update(
            entity.copy(
                daysJson = json.encodeToString(days),
                pendingSync = true,
                updatedAt = System.nanoTime(),
            ),
        )
        sync(localId)
    }

    // Sends pending local edits to the server. Returns true once the server
    // has the latest version.
    suspend fun sync(localId: Long): Boolean = syncMutex.withLock {
        val entity = dao.get(localId) ?: return false
        if (!entity.pendingSync) return true
        val serverId = entity.serverId ?: return false

        val days = json.decodeFromString<List<DayDto>>(entity.daysJson)
        val result = apiCall(json) {
            api.updateItinerary(serverId, UpdateItineraryRequestDto(days)).itinerary
        }

        if (result !is ApiResult.Success) return false

        // If the user edited again while the request was in flight, the
        // server's reply is already out of date: keep the newer local edit
        // (still pending) and let the next sync send it.
        val current = dao.get(localId) ?: return false
        if (current.updatedAt != entity.updatedAt) return false

        // Take the server's days, which carry the tip ids it attached.
        dao.update(
            current.copy(
                daysJson = json.encodeToString(result.value.days),
                version = result.value.version,
                pendingSync = false,
            ),
        )
        true
    }

    private fun ItineraryDto.toEntity() = ItineraryEntity(
        serverId = id,
        title = title,
        startDate = startDate,
        version = version,
        daysJson = json.encodeToString(days),
        pendingSync = false,
        updatedAt = System.nanoTime(),
    )

    private fun ItineraryEntity.toLocal() = LocalItinerary(
        localId = localId,
        serverId = serverId,
        title = title,
        days = json.decodeFromString(daysJson),
        pendingSync = pendingSync,
    )
}
