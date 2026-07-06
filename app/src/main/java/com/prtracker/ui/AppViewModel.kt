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

    var lastErrors: List<String> = emptyList()
        private set

    init {
        viewModelScope.launch { repository.seedDefaults() }
        refreshBackupStatus()
    }

    fun saveProfile(sex: Sex, preferredUnit: WeightUnit, bodyweight: Double?) {
        viewModelScope.launch { repository.saveProfile(sex, preferredUnit, bodyweight) }
    }

    fun addLift(name: String) {
        viewModelScope.launch { repository.addLift(name) }
    }

    fun toggleMajor(lift: LiftEntity) {
        viewModelScope.launch { repository.toggleMajor(lift) }
    }

    fun toggleArchived(lift: LiftEntity) {
        viewModelScope.launch {
            if (lift.archived) repository.restoreLift(lift) else repository.archiveLift(lift)
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }

    fun addEntry(
        liftId: Long,
        weight: Double,
        unit: WeightUnit,
        sets: Int,
        reps: Int,
        bodyweight: Double?,
        notes: String,
    ) {
        viewModelScope.launch {
            lastErrors = repository.addEntry(liftId, weight, unit, sets, reps, bodyweight, notes)
        }
    }

    fun handleSignInResult(data: Intent?) {
        val result = authManager.handleSignInResult(data)
        _backupState.value = _backupState.value.copy(
            accountEmail = backupCoordinator.accountEmail(),
            busy = false,
            message = result.fold(
                onSuccess = { "Signed in as $it." },
                onFailure = { it.message ?: "Google sign-in failed." },
            ),
            authResolutionIntent = null,
        )
        refreshBackupStatus()
    }

    fun refreshBackupStatus() {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(
                accountEmail = backupCoordinator.accountEmail(),
                busy = backupCoordinator.accountEmail() != null,
                message = null,
                authResolutionIntent = null,
            )
            if (backupCoordinator.accountEmail() == null) {
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
                BackupOperationResult.NotSignedIn -> _backupState.value = BackupUiState(message = "Sign in to use Google Drive backup.")
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
                    message = "Sign in to use Google Drive backup.",
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
                    message = "Sign in to use Google Drive backup.",
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
        }
    }
}

data class BackupUiState(
    val accountEmail: String? = null,
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
