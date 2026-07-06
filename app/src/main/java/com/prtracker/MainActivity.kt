package com.prtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prtracker.ui.AppViewModel
import com.prtracker.ui.AppViewModelFactory
import com.prtracker.ui.PRTrackerApp
import com.prtracker.ui.PRTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PrTrackerApp).container
        setContent {
            PRTrackerTheme {
                val viewModel: AppViewModel = viewModel(factory = AppViewModelFactory(container.repository))
                PRTrackerApp(viewModel)
            }
        }
    }
}
