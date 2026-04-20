package com.xanderscannell.startinggundetector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xanderscannell.startinggundetector.audio.AudioDetector
import com.xanderscannell.startinggundetector.device.DeviceIdProvider
import com.xanderscannell.startinggundetector.device.UserPreferences
import com.xanderscannell.startinggundetector.session.SessionRepository
import com.xanderscannell.startinggundetector.utils.TimestampFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DetectorState { IDLE, LISTENING }

data class DetectionEntry(
    val timestamp: String,
    val starred: Boolean = false,
    val deviceId: String = "",
    val displayName: String = "",
    val isMine: Boolean = true
)

data class UiState(
    val detectorState: DetectorState = DetectorState.IDLE,
    val lastDetectedTimestamp: String = "",
    val detectionHistory: List<DetectionEntry> = emptyList(),
    val sensitivity: Float = 7f,
    val latencyOffsetMs: Int = 0,
    val username: String = "",
    val errorMessage: String? = null,
    val sessionCode: String? = null,
    val isInSession: Boolean = false,
    val sessionLoading: Boolean = false,
    val showSessionDialog: Boolean = false,
    val sessionError: String? = null
)

class GunShotViewModel(
    private val deviceId: String,
    private val sessionRepository: SessionRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UiState(
            latencyOffsetMs = userPreferences.latencyOffsetMs,
            username = userPreferences.username
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val detector = AudioDetector()
    private var detectionJob: Job? = null
    private var streamJob: Job? = null

    // Starred state is kept locally so it survives Firestore stream re-emissions.
    // Key format: "$deviceId:$timestamp"
    private val starredKeys = mutableSetOf<String>()

    init {
        detector.onDetected = { event ->
            val adjusted = event.wallMillis + _uiState.value.latencyOffsetMs
            val formatted = TimestampFormatter.format(adjusted)
            val current = _uiState.value

            // "Last detected" always reflects this device's own detections only
            _uiState.value = current.copy(lastDetectedTimestamp = formatted)

            if (current.isInSession && current.sessionCode != null) {
                // Session mode: write to Firestore; the stream listener owns the history list
                val displayName = current.username.ifBlank { DeviceIdProvider.shortId(deviceId) }
                viewModelScope.launch {
                    try {
                        sessionRepository.writeDetection(current.sessionCode, formatted, displayName)
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to sync detection"
                        )
                    }
                }
            } else {
                // Solo mode: update history locally
                val entry = DetectionEntry(
                    timestamp = formatted,
                    deviceId = deviceId,
                    displayName = current.username.ifBlank { DeviceIdProvider.shortId(deviceId) },
                    isMine = true
                )
                _uiState.value = _uiState.value.copy(
                    detectionHistory = listOf(entry) + _uiState.value.detectionHistory
                )
            }
        }
    }

    // ── Listening ────────────────────────────────────────────

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

    // ── Session ──────────────────────────────────────────────

    fun showSessionDialog() {
        _uiState.value = _uiState.value.copy(showSessionDialog = true, sessionError = null)
    }

    fun dismissSessionDialog() {
        _uiState.value = _uiState.value.copy(showSessionDialog = false, sessionError = null)
    }

    fun createSession() {
        _uiState.value = _uiState.value.copy(sessionLoading = true, sessionError = null)
        viewModelScope.launch {
            try {
                val code = sessionRepository.createSession()
                _uiState.value = _uiState.value.copy(
                    sessionCode = code,
                    isInSession = true,
                    sessionLoading = false,
                    showSessionDialog = false,
                    detectionHistory = emptyList()
                )
                startStream(code)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    sessionLoading = false,
                    sessionError = "Failed to create session"
                )
            }
        }
    }

    fun joinSession(code: String) {
        val upperCode = code.trim().uppercase()
        if (upperCode.length != 4) {
            _uiState.value = _uiState.value.copy(sessionError = "Code must be 4 characters")
            return
        }
        _uiState.value = _uiState.value.copy(sessionLoading = true, sessionError = null)
        viewModelScope.launch {
            try {
                val exists = sessionRepository.joinSession(upperCode)
                if (exists) {
                    _uiState.value = _uiState.value.copy(
                        sessionCode = upperCode,
                        isInSession = true,
                        sessionLoading = false,
                        showSessionDialog = false,
                        detectionHistory = emptyList()
                    )
                    startStream(upperCode)
                } else {
                    _uiState.value = _uiState.value.copy(
                        sessionLoading = false,
                        sessionError = "Session \"$upperCode\" not found"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    sessionLoading = false,
                    sessionError = "Failed to join session"
                )
            }
        }
    }

    fun leaveSession() {
        streamJob?.cancel()
        streamJob = null
        starredKeys.clear()
        _uiState.value = _uiState.value.copy(
            sessionCode = null,
            isInSession = false,
            detectionHistory = emptyList(),
            lastDetectedTimestamp = ""
        )
    }

    private fun startStream(sessionCode: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            sessionRepository.streamDetections(sessionCode).collect { detections ->
                val mapped = detections.map { fd ->
                    val key = "${fd.deviceId}:${fd.timestamp}"
                    DetectionEntry(
                        timestamp = fd.timestamp,
                        starred = key in starredKeys,
                        deviceId = fd.deviceId,
                        displayName = fd.displayName,
                        isMine = fd.deviceId == deviceId
                    )
                }
                _uiState.value = _uiState.value.copy(detectionHistory = mapped)
            }
        }
    }

    // ── History ──────────────────────────────────────────────

    fun toggleStar(index: Int) {
        val current = _uiState.value
        if (index !in current.detectionHistory.indices) return
        val entry = current.detectionHistory[index]
        val key = "${entry.deviceId}:${entry.timestamp}"
        if (entry.starred) starredKeys.remove(key) else starredKeys.add(key)
        _uiState.value = current.copy(
            detectionHistory = current.detectionHistory.mapIndexed { i, e ->
                if (i == index) e.copy(starred = !e.starred) else e
            }
        )
    }

    fun clearHistory() {
        starredKeys.clear()
        _uiState.value = _uiState.value.copy(
            lastDetectedTimestamp = "",
            detectionHistory = emptyList()
        )
    }

    fun setSensitivity(value: Float) {
        if (_uiState.value.detectorState == DetectorState.LISTENING) return
        _uiState.value = _uiState.value.copy(sensitivity = value)
    }

    fun setLatencyOffset(ms: Int) {
        val clamped = ms.coerceIn(-500, 500)
        userPreferences.latencyOffsetMs = clamped
        _uiState.value = _uiState.value.copy(latencyOffsetMs = clamped)
    }

    fun setUsername(name: String) {
        userPreferences.username = name
        _uiState.value = _uiState.value.copy(username = name)
    }

    override fun onCleared() {
        super.onCleared()
        detectionJob?.cancel()
        streamJob?.cancel()
    }

    private fun sliderToMultiplier(slider: Float): Float {
        val normalized = (slider - 1f) / 9f
        return 20f - (normalized * 16f)
    }
}
