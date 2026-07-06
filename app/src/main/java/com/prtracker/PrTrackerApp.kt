package com.prtracker

import android.app.Application
import com.prtracker.data.AppContainer

class PrTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
