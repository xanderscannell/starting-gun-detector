package com.xanderscannell.startinggundetector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xanderscannell.startinggundetector.audio.AudioDetector
import com.xanderscannell.startinggundetector.utils.TimestampFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DetectorState { IDLE, LISTENING }

data class DetectionEntry(
    val timestamp: String,
    val starred: Boolean = false
)

data class UiState(
    val detectorState: DetectorState = DetectorState.IDLE,
    val lastDetectedTimestamp: String = "",
    val detectionHistory: List<DetectionEntry> = emptyList(),
    val sensitivity: Float = 7f,
    val errorMessage: String? = null
)

class GunShotViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val detector = AudioDetector()
    private var detectionJob: Job? = null

    init {
        detector.onDetected = { event ->
            val formatted = TimestampFormatter.format(event.wallMillis)
            val current = _uiState.value
            _uiState.value = current.copy(
                lastDetectedTimestamp = formatted,
                detectionHistory = listOf(DetectionEntry(formatted)) + current.detectionHistory
            )
        }
    }

    fun startListening() {
        if (_uiState.value.detectorState != DetectorState.IDLE) return

        detector.sensitivityMultiplier = sliderToMultiplier(_uiState.value.sensitivity)
        _uiState.value = _uiState.value.copy(
            detectorState = DetectorState.LISTENING,
            errorMessage = null
        )

        detectionJob = viewModelScope.launch {
            try {
                detector.run()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    detectorState = DetectorState.IDLE,
                    errorMessage = "Audio error: ${e.message}"
                )
            }
        }
    }

    fun stopListening() {
        detectionJob?.cancel()
        detectionJob = null
        _uiState.value = _uiState.value.copy(detectorState = DetectorState.IDLE)
    }

    fun toggleStar(index: Int) {
        val current = _uiState.value
        if (index !in current.detectionHistory.indices) return
        _uiState.value = current.copy(
            detectionHistory = current.detectionHistory.mapIndexed { i, entry ->
                if (i == index) entry.copy(starred = !entry.starred) else entry
            }
        )
    }

    fun clearHistory() {
        _uiState.value = _uiState.value.copy(
            lastDetectedTimestamp = "",
            detectionHistory = emptyList()
        )
    }

    fun setSensitivity(value: Float) {
        if (_uiState.value.detectorState == DetectorState.LISTENING) return
        _uiState.value = _uiState.value.copy(sensitivity = value)
    }

    override fun onCleared() {
        super.onCleared()
        detectionJob?.cancel()
    }

    // Slider 1–10: higher slider = more sensitive = lower multiplier threshold
    // Slider 10 → multiplier 4x (triggers easily)
    // Slider 1  → multiplier 20x (requires very loud sound)
    private fun sliderToMultiplier(slider: Float): Float {
        val normalized = (slider - 1f) / 9f  // 0..1
        return 20f - (normalized * 16f)       // 20..4
    }
}
