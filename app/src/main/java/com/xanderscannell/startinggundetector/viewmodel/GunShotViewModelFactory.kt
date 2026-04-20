package com.xanderscannell.startinggundetector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xanderscannell.startinggundetector.device.UserPreferences
import com.xanderscannell.startinggundetector.session.SessionRepository

class GunShotViewModelFactory(
    private val deviceId: String,
    private val sessionRepository: SessionRepository,
    private val userPreferences: UserPreferences
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GunShotViewModel(deviceId, sessionRepository, userPreferences) as T
    }
}
