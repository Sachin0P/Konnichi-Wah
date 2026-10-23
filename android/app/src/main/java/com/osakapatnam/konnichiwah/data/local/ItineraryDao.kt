package com.osakapatnam.konnichiwah.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItineraryDao {

    @Insert
    suspend fun insert(itinerary: ItineraryEntity): Long

    @Update
    suspend fun update(itinerary: ItineraryEntity)

    @Query("SELECT * FROM itineraries WHERE localId = :localId")
    suspend fun get(localId: Long): ItineraryEntity?

    // The prototype shows one trip at a time: the most recently created one.
    @Query("SELECT * FROM itineraries ORDER BY localId DESC LIMIT 1")
    fun observeLatest(): Flow<ItineraryEntity?>

    @Query("SELECT COUNT(*) FROM itineraries")
    suspend fun count(): Int
}
