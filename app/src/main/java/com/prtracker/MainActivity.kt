package com.prtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prtracker.ui.AppViewModel
import com.prtracker.ui.AppViewModelFactory
import com.prtracker.ui.PRTrackerApp
import com.prtracker.ui.PRTrackerTheme

class MainActivity : ComponentActivity() {
    private var appViewModel: AppViewModel? = null

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        appViewModel?.handleSignInResult(result.data)
    }

    private val authResolutionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        appViewModel?.refreshBackupStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PrTrackerApp).container
        setContent {
            PRTrackerTheme {
                val viewModel: AppViewModel = viewModel(
                    factory = AppViewModelFactory(
                        repository = container.repository,
                        backupCoordinator = container.driveBackupCoordinator,
                        authManager = container.driveAuthManager,
                        cacheDir = cacheDir,
                    ),
                )
                appViewModel = viewModel
                PRTrackerApp(
                    viewModel = viewModel,
                    onSignInRequested = {
                        signInLauncher.launch(container.driveAuthManager.signInIntent())
                    },
                    onAuthResolutionRequested = { intent ->
                        authResolutionLauncher.launch(intent)
                    },
                    onRestoreCompleted = { recreate() },
                )
            }
        }
    }
}
