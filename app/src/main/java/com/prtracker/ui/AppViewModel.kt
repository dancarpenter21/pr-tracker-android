package com.prtracker.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prtracker.core.Sex
import com.prtracker.core.WeightUnit
import com.prtracker.data.AppState
import com.prtracker.data.BackupOperationResult
import com.prtracker.data.DriveAuthManager
import com.prtracker.data.DriveBackupCoordinator
import com.prtracker.data.DriveBackupFile
import com.prtracker.data.LiftEntity
import com.prtracker.data.NeedsGoogleResolution
import com.prtracker.data.Repository
import com.prtracker.data.SignInResult
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val repository: Repository,
    private val backupCoordinator: DriveBackupCoordinator,
    private val authManager: DriveAuthManager,
    private val cacheDir: File,
) : ViewModel() {
    val state: StateFlow<AppState> = repository.appState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppState(),
    )
    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()
    private val _profileState = MutableStateFlow(ProfileUiState())
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    var lastErrors: List<String> = emptyList()
        private set

    init {
        viewModelScope.launch { repository.seedDefaults() }
        refreshBackupStatus()
    }

    fun saveProfile(sex: Sex, preferredUnit: WeightUnit) {
        viewModelScope.launch {
            _profileState.value = ProfileUiState(saving = true)
            repository.saveProfile(sex, preferredUnit).fold(
                onSuccess = { changed ->
                    _profileState.value = ProfileUiState(message = "Profile saved.")
                    if (changed) backupAfterDataChange()
                },
                onFailure = {
                    _profileState.value = ProfileUiState(
                        error = it.message ?: "Unable to save profile.",
                    )
                },
            )
        }
    }

    fun addLift(name: String) {
        viewModelScope.launch {
            if (repository.addLift(name)) backupAfterDataChange()
        }
    }

    fun toggleMajor(lift: LiftEntity) {
        viewModelScope.launch {
            if (repository.toggleMajor(lift)) backupAfterDataChange()
        }
    }

    fun toggleArchived(lift: LiftEntity) {
        viewModelScope.launch {
            val changed = if (lift.archived) repository.restoreLift(lift) else repository.archiveLift(lift)
            if (changed) backupAfterDataChange()
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            if (repository.deleteEntry(id)) backupAfterDataChange()
        }
    }

    fun addEntry(
        liftId: Long,
        weight: Double,
        unit: WeightUnit,
        sets: Int,
        reps: Int,
        bodyweight: Double?,
        notes: String,
        performedAt: Long,
    ) {
        viewModelScope.launch {
            lastErrors = repository.addEntry(liftId, weight, unit, sets, reps, bodyweight, notes, performedAt)
            if (lastErrors.isEmpty()) backupAfterDataChange()
        }
    }

    fun handleSignInResult(data: Intent?) {
        val result = authManager.handleSignInResult(data)
        val accountEmail = backupCoordinator.accountEmail()
        _backupState.value = _backupState.value.copy(
            accountEmail = accountEmail,
            driveReady = backupCoordinator.isDriveReady(),
            busy = false,
            message = when (result) {
                is SignInResult.Success -> "Signed in as ${result.accountEmail}. Checking Drive access..."
                is SignInResult.Failure -> result.message
            },
            authResolutionIntent = null,
        )
        if (result is SignInResult.Success) {
            refreshBackupStatus(keepMessage = true)
        }
    }

    fun refreshBackupStatus(keepMessage: Boolean = false) {
        viewModelScope.launch {
            val accountEmail = backupCoordinator.accountEmail()
            _backupState.value = _backupState.value.copy(
                accountEmail = accountEmail,
                driveReady = backupCoordinator.isDriveReady(),
                busy = accountEmail != null,
                message = if (keepMessage) _backupState.value.message else null,
                authResolutionIntent = null,
            )
            if (accountEmail == null) {
                _backupState.value = _backupState.value.copy(busy = false, latestBackup = null)
                return@launch
            }
            when (val result = runBackupOperation { backupCoordinator.latestBackup() }) {
                is BackupOperationResult.Success -> _backupState.value = _backupState.value.copy(
                    busy = false,
                    latestBackup = result.file,
                    message = if (result.file == null) "No Drive backup found." else null,
                )
                is BackupOperationResult.Failure -> _backupState.value = _backupState.value.copy(
                    busy = false,
                    message = result.message,
                )
                BackupOperationResult.NotSignedIn -> _backupState.value = _backupState.value.copy(
                    busy = false,
                    driveReady = false,
                    message = "Signed in, but Drive app-data authorization is not ready. Verify the Google Cloud OAuth client package and SHA-1.",
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            backupCoordinator.signOut()
            _backupState.value = BackupUiState(message = "Signed out.")
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(busy = true, message = "Backing up database...")
            when (val result = runBackupOperation { backupCoordinator.backupNow() }) {
                is BackupOperationResult.Success -> _backupState.value = _backupState.value.copy(
                    busy = false,
                    latestBackup = result.file,
                    message = "Backup complete.",
                )
                is BackupOperationResult.Failure -> _backupState.value = _backupState.value.copy(
                    busy = false,
                    message = result.message,
                )
                BackupOperationResult.NotSignedIn -> _backupState.value = _backupState.value.copy(
                    busy = false,
                    driveReady = false,
                    message = "Drive authorization is not ready. Verify the Google Cloud OAuth client package and SHA-1.",
                )
            }
        }
    }

    fun restoreNow(onRestored: () -> Unit) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(busy = true, message = "Restoring database...")
            when (val result = runBackupOperation { backupCoordinator.restoreNow(cacheDir) }) {
                is BackupOperationResult.Success -> {
                    _backupState.value = _backupState.value.copy(
                        busy = false,
                        latestBackup = result.file,
                        message = "Restore complete.",
                    )
                    onRestored()
                }
                is BackupOperationResult.Failure -> _backupState.value = _backupState.value.copy(
                    busy = false,
                    message = result.message,
                )
                BackupOperationResult.NotSignedIn -> _backupState.value = _backupState.value.copy(
                    busy = false,
                    driveReady = false,
                    message = "Drive authorization is not ready. Verify the Google Cloud OAuth client package and SHA-1.",
                )
            }
        }
    }

    fun consumeAuthResolution() {
        _backupState.value = _backupState.value.copy(authResolutionIntent = null)
    }

    private suspend fun runBackupOperation(block: suspend () -> BackupOperationResult): BackupOperationResult {
        return try {
            block()
        } catch (resolution: NeedsGoogleResolution) {
            _backupState.value = _backupState.value.copy(
                busy = false,
                authResolutionIntent = resolution.intent,
                message = "Google Drive needs authorization.",
            )
            BackupOperationResult.Failure("Google Drive needs authorization.")
        } catch (error: Exception) {
            BackupOperationResult.Failure(error.message ?: "Google Drive operation failed.")
        }
    }

    private suspend fun backupAfterDataChange() {
        if (backupCoordinator.accountEmail() == null) return

        val result = runBackupOperation { backupCoordinator.backupNow() }
        if (backupCoordinator.accountEmail() == null) return

        when (result) {
            is BackupOperationResult.Success -> _backupState.value = _backupState.value.copy(
                driveReady = true,
                latestBackup = result.file,
                message = null,
                authResolutionIntent = null,
            )
            is BackupOperationResult.Failure -> _backupState.value = _backupState.value.copy(
                message = "Changes saved locally, but automatic backup failed: ${result.message}",
            )
            BackupOperationResult.NotSignedIn -> _backupState.value = _backupState.value.copy(
                driveReady = false,
                message = "Changes saved locally, but Drive authorization is not ready.",
            )
        }
    }
}

data class ProfileUiState(
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class BackupUiState(
    val accountEmail: String? = null,
    val driveReady: Boolean = false,
    val latestBackup: DriveBackupFile? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val authResolutionIntent: Intent? = null,
)

class AppViewModelFactory(
    private val repository: Repository,
    private val backupCoordinator: DriveBackupCoordinator,
    private val authManager: DriveAuthManager,
    private val cacheDir: File,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(repository, backupCoordinator, authManager, cacheDir) as T
    }
}
