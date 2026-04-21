package com.xanderscannell.startinggundetector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xanderscannell.startinggundetector.data.FinishSplit
import com.xanderscannell.startinggundetector.data.Race
import com.xanderscannell.startinggundetector.data.RaceRepository
import com.xanderscannell.startinggundetector.data.StartTime
import com.xanderscannell.startinggundetector.data.StartTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class RaceViewModel(
    private val raceRepository: RaceRepository
) : ViewModel() {

    private val _raceList = MutableStateFlow<List<Race>>(emptyList())
    val raceList: StateFlow<List<Race>> = _raceList.asStateFlow()

    private val _currentRace = MutableStateFlow<Race?>(null)
    val currentRace: StateFlow<Race?> = _currentRace.asStateFlow()

    fun loadAllRaces() {
        viewModelScope.launch(Dispatchers.IO) {
            _raceList.value = raceRepository.listAllRaces()
        }
    }

    fun createRace(
        meetName: String,
        meetDate: String,
        eventName: String,
        recordingStartMillis: Long,
        serverOffsetMs: Long?,
        videoFile: File,
        detections: List<DetectionEntry>,
        isSession: Boolean
    ): String {
        val raceId = UUID.randomUUID().toString()
        val startTimes = detections
            .filter { it.serverTimestampMillis != null }
            .map { entry ->
                StartTime(
                    id = UUID.randomUUID().toString(),
                    timestampMillis = entry.serverTimestampMillis!!,
                    source = if (isSession) StartTimeSource.FIRESTORE_SESSION
                             else StartTimeSource.AUDIO_DETECTION,
                    label = entry.displayName
                )
            }
        val race = Race(
            id = raceId,
            meetName = meetName,
            meetDate = meetDate,
            eventName = eventName,
            createdAtMillis = System.currentTimeMillis(),
            recordingStartMillis = recordingStartMillis,
            serverOffsetMs = serverOffsetMs,
            startTimes = startTimes
        )
        raceRepository.saveRace(race)
        raceRepository.moveVideoToRace(raceId, videoFile)
        _currentRace.value = race
        return raceId
    }

    fun loadRace(raceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentRace.value = raceRepository.loadRace(raceId)
        }
    }

    fun getRaceVideoFile(raceId: String): File {
        return raceRepository.getRaceVideoFile(raceId)
    }

    fun setOfficialStartTime(startTimeId: String) {
        val race = _currentRace.value ?: return
        val updated = race.copy(officialStartTimeId = startTimeId)
        _currentRace.value = updated
        viewModelScope.launch(Dispatchers.IO) { raceRepository.saveRace(updated) }
    }

    fun addManualStartTime(timestampMillis: Long, label: String = "") {
        val race = _currentRace.value ?: return
        val newStart = StartTime(
            id = UUID.randomUUID().toString(),
            timestampMillis = timestampMillis,
            source = StartTimeSource.MANUAL_ENTRY,
            label = label
        )
        val updated = race.copy(startTimes = race.startTimes + newStart)
        _currentRace.value = updated
        viewModelScope.launch(Dispatchers.IO) { raceRepository.saveRace(updated) }
    }

    fun addFinishSplit(timestampMillis: Long) {
        val race = _currentRace.value ?: return
        val nextPlace = race.finishSplits.size + 1
        val split = FinishSplit(place = nextPlace, timestampMillis = timestampMillis)
        val updated = race.copy(finishSplits = race.finishSplits + split)
        _currentRace.value = updated
        viewModelScope.launch(Dispatchers.IO) { raceRepository.saveRace(updated) }
    }

    fun removeLastFinishSplit() {
        val race = _currentRace.value ?: return
        if (race.finishSplits.isEmpty()) return
        val updated = race.copy(finishSplits = race.finishSplits.dropLast(1))
        _currentRace.value = updated
        viewModelScope.launch(Dispatchers.IO) { raceRepository.saveRace(updated) }
    }

    fun saveCurrentRace() {
        val race = _currentRace.value ?: return
        viewModelScope.launch(Dispatchers.IO) { raceRepository.saveRace(race) }
    }

    fun deleteRace(raceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            raceRepository.deleteRace(raceId)
            _raceList.value = raceRepository.listAllRaces()
            if (_currentRace.value?.id == raceId) _currentRace.value = null
        }
    }

    fun clearCurrentRace() {
        _currentRace.value = null
    }
}
