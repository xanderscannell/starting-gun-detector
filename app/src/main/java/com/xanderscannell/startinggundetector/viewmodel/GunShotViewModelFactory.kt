package com.xanderscannell.startinggundetector.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xanderscannell.startinggundetector.device.UserPreferences
import com.xanderscannell.startinggundetector.session.SessionRepository

class GunShotViewModelFactory(
    private val application: Application,
    private val deviceId: String,
    private val sessionRepository: SessionRepository,
    private val userPreferences: UserPreferences
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GunShotViewModel(application, deviceId, sessionRepository, userPreferences) as T
    }
}
