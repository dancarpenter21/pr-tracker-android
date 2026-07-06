package com.prtracker.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, LiftEntity::class, LiftEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun liftDao(): LiftDao
    abstract fun entryDao(): EntryDao
}
