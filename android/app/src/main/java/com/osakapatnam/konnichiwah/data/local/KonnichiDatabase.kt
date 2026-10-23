package com.osakapatnam.konnichiwah.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// Bump the version and add a Migration whenever an entity changes. The schema
// for each version is exported to app/schemas.
@Database(entities = [ItineraryEntity::class], version = 1, exportSchema = true)
abstract class KonnichiDatabase : RoomDatabase() {
    abstract fun itineraryDao(): ItineraryDao
}
