package com.prtracker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class DatabaseBackupManager(
    private val context: Context,
    private val databaseProvider: () -> AppDatabase,
    private val closeDatabase: () -> Unit,
    private val reopenDatabase: () -> Unit,
) {
    private val mutex = Mutex()

    suspend fun createSnapshot(): File = mutex.withLock {
        withContext(Dispatchers.IO) {
            val database = databaseProvider()
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()

            val source = context.getDatabasePath(DATABASE_NAME)
            require(source.exists()) { "Local database file does not exist yet." }

            val snapshot = File(context.cacheDir, BACKUP_FILE_NAME)
            source.copyTo(snapshot, overwrite = true)
            snapshot
        }
    }

    suspend fun restoreFrom(snapshot: File) = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(snapshot.exists()) { "Downloaded backup file is missing." }

            closeDatabase()

            val destination = context.getDatabasePath(DATABASE_NAME)
            destination.parentFile?.mkdirs()
            snapshot.copyTo(destination, overwrite = true)
            File(destination.parentFile, "$DATABASE_NAME-wal").delete()
            File(destination.parentFile, "$DATABASE_NAME-shm").delete()

            reopenDatabase()
        }
    }

    companion object {
        const val DATABASE_NAME = "pr-tracker.db"
        const val BACKUP_FILE_NAME = "pr-tracker.sqlite"
    }
}
