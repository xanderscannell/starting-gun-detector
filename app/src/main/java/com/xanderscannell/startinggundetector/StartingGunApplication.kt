package com.xanderscannell.startinggundetector

import android.app.Application
import com.xanderscannell.startinggundetector.device.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StartingGunApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AuthManager.startEagerSignIn(appScope)
    }
}
