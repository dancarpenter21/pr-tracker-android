package com.prtracker.data

import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class DriveBackupCoordinator(
    private val authManager: DriveAuthManager,
    private val driveClient: DriveBackupClient,
    private val databaseBackupManager: DatabaseBackupManager,
) {
    private val databaseOperationMutex = Mutex()

    fun accountEmail(): String? = authManager.accountEmail()
    fun isDriveReady(): Boolean = authManager.hasDrivePermission()

    suspend fun signOut() = databaseOperationMutex.withLock {
        authManager.signOut()
    }

    suspend fun latestBackup(): BackupOperationResult {
        val token = tokenOrReturn() ?: return BackupOperationResult.NotSignedIn
        return runCatching {
            driveClient.findBackup(token)
        }.fold(
            onSuccess = { BackupOperationResult.Success(it) },
            onFailure = { BackupOperationResult.Failure(it.message ?: "Unable to load backup status.") },
        )
    }

    suspend fun backupNow(): BackupOperationResult = databaseOperationMutex.withLock {
        val token = tokenOrReturn() ?: return@withLock BackupOperationResult.NotSignedIn
        runCatching {
            val snapshot = databaseBackupManager.createSnapshot()
            driveClient.uploadBackup(token, snapshot)
        }.fold(
            onSuccess = { BackupOperationResult.Success(it) },
            onFailure = { BackupOperationResult.Failure(it.message ?: "Unable to back up database.") },
        )
    }

    suspend fun restoreNow(cacheDir: File): BackupOperationResult = databaseOperationMutex.withLock {
        val token = tokenOrReturn() ?: return@withLock BackupOperationResult.NotSignedIn
        runCatching {
            val destination = withContext(Dispatchers.IO) {
                File(cacheDir, "downloaded-${DatabaseBackupManager.BACKUP_FILE_NAME}")
            }
            val backup = driveClient.downloadBackup(token, destination)
            databaseBackupManager.restoreFrom(destination)
            backup
        }.fold(
            onSuccess = { BackupOperationResult.Success(it) },
            onFailure = { BackupOperationResult.Failure(it.message ?: "Unable to restore database.") },
        )
    }

    private suspend fun tokenOrReturn(): String? {
        return when (val token = authManager.accessToken()) {
            is AuthTokenResult.Token -> token.value
            is AuthTokenResult.NeedsResolution -> throw NeedsGoogleResolution(token.intent)
            is AuthTokenResult.Failure -> throw IllegalStateException(token.message)
            AuthTokenResult.NotSignedIn -> null
        }
    }
}

sealed interface BackupOperationResult {
    data class Success(val file: DriveBackupFile?) : BackupOperationResult
    data class Failure(val message: String) : BackupOperationResult
    data object NotSignedIn : BackupOperationResult
}

class NeedsGoogleResolution(val intent: Intent) : RuntimeException("Google Drive authorization needs user approval.")
