package com.xanderscannell.startinggundetector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xanderscannell.startinggundetector.data.RaceRepository

class RaceViewModelFactory(
    private val raceRepository: RaceRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RaceViewModel(raceRepository) as T
    }
}
