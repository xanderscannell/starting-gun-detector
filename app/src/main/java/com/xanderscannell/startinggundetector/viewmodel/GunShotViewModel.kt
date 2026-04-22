package com.xanderscannell.startinggundetector.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xanderscannell.startinggundetector.audio.ListeningService
import com.xanderscannell.startinggundetector.device.DeviceIdProvider
import com.xanderscannell.startinggundetector.device.UserPreferences
import com.xanderscannell.startinggundetector.session.SessionMember
import com.xanderscannell.startinggundetector.session.SessionRepository
import com.xanderscannell.startinggundetector.utils.TimestampFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DetectorState { IDLE, LISTENING }

data class WaveformBar(val normalizedRms: Float, val isDetection: Boolean = false)

data class DetectionEntry(
    val timestamp: String,
    val starred: Boolean = false,
    val deviceId: String = "",
    val displayName: String = "",
    val isMine: Boolean = true,
    val serverTimestampMillis: Long? = null
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
    val sessionError: String? = null,
    val waveformBars: List<WaveformBar> = emptyList(),
    val sessionMembers: List<SessionMember> = emptyList(),
    // Offset in ms to convert System.currentTimeMillis() to Firestore server time.
    // Null = not yet calibrated. Set automatically on join/create; can be refreshed manually.
    val serverOffsetMs: Long? = null
)

class GunShotViewModel(
    application: Application,
    private val deviceId: String,
    private val sessionRepository: SessionRepository,
    private val userPreferences: UserPreferences
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        UiState(
            latencyOffsetMs = userPreferences.latencyOffsetMs,
            username = userPreferences.username
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var membersJob: Job? = null
    private var waveformIdleJob: Job? = null
    private var rmsJob: Job? = null
    private var detectionJob: Job? = null

    companion object {
        private const val WAVEFORM_BAR_COUNT = 60
        private const val RMS_VISUAL_MAX = 8000f
        private const val IDLE_BAR_INTERVAL_MS = 40L
    }

    // Starred state is kept locally so it survives Firestore stream re-emissions.
    // Key format: "$deviceId:$timestamp"
    private val starredKeys = mutableSetOf<String>()

    init {
        startIdleWaveform()
    }

    private fun startIdleWaveform() {
        waveformIdleJob?.cancel()
        waveformIdleJob = viewModelScope.launch {
            while (true) {
                val tiny = (Math.random() * 0.03 + 0.01).toFloat()
                val current = _uiState.value
                val bars = (current.waveformBars + WaveformBar(tiny))
                    .takeLast(WAVEFORM_BAR_COUNT)
                _uiState.value = current.copy(waveformBars = bars)
                kotlinx.coroutines.delay(IDLE_BAR_INTERVAL_MS)
            }
        }
    }

    private fun startServiceCollectors() {
        rmsJob?.cancel()
        rmsJob = viewModelScope.launch {
            ListeningService.rmsFlow.collect { rms ->
                if (_uiState.value.detectorState == DetectorState.LISTENING) {
                    val normalized = (rms / RMS_VISUAL_MAX).coerceIn(0f, 1f)
                    val current = _uiState.value
                    val bars = (current.waveformBars + WaveformBar(normalized))
                        .takeLast(WAVEFORM_BAR_COUNT)
                    _uiState.value = current.copy(waveformBars = bars)
                }
            }
        }

        detectionJob?.cancel()
        detectionJob = viewModelScope.launch {
            ListeningService.detectionFlow.collect { event ->
                val adjusted = event.wallMillis + _uiState.value.latencyOffsetMs
                val formatted = TimestampFormatter.format(adjusted)
                val current = _uiState.value

                val updatedBars = if (current.waveformBars.isNotEmpty()) {
                    current.waveformBars.dropLast(1) + current.waveformBars.last().copy(isDetection = true)
                } else current.waveformBars

                _uiState.value = current.copy(lastDetectedTimestamp = formatted, waveformBars = updatedBars)

                if (current.isInSession && current.sessionCode != null) {
                    val displayName = current.username.ifBlank { DeviceIdProvider.shortId(deviceId) }
                    val serverOffset = current.serverOffsetMs ?: 0L
                    val serverCorrected = adjusted + serverOffset
                    try {
                        sessionRepository.writeDetection(
                            current.sessionCode, formatted, displayName,
                            detectionMillis = adjusted,
                            serverCorrectedMillis = serverCorrected
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(errorMessage = "Failed to sync detection")
                    }
                } else {
                    val entry = DetectionEntry(
                        timestamp = formatted,
                        deviceId = deviceId,
                        displayName = current.username.ifBlank { DeviceIdProvider.shortId(deviceId) },
                        isMine = true,
                        serverTimestampMillis = adjusted
                    )
                    _uiState.value = _uiState.value.copy(
                        detectionHistory = listOf(entry) + _uiState.value.detectionHistory
                    )
                }
            }
        }
    }

    // ── Listening ────────────────────────────────────────────

    fun startListening() {
        if (_uiState.value.detectorState != DetectorState.IDLE) return
        waveformIdleJob?.cancel()
        waveformIdleJob = null
        _uiState.value = _uiState.value.copy(
            detectorState = DetectorState.LISTENING,
            errorMessage = null
        )
        val sessionCode = _uiState.value.sessionCode
        if (sessionCode != null) {
            viewModelScope.launch {
                try { sessionRepository.updateListeningStatus(sessionCode, true) } catch (_: Exception) {}
            }
        }
        startServiceCollectors()
        ListeningService.start(getApplication(), sliderToMultiplier(_uiState.value.sensitivity))
    }

    fun stopListening() {
        ListeningService.stop(getApplication())
        rmsJob?.cancel()
        rmsJob = null
        detectionJob?.cancel()
        detectionJob = null
        _uiState.value = _uiState.value.copy(detectorState = DetectorState.IDLE)
        val sessionCode = _uiState.value.sessionCode
        if (sessionCode != null) {
            viewModelScope.launch {
                try { sessionRepository.updateListeningStatus(sessionCode, false) } catch (_: Exception) {}
            }
        }
        startIdleWaveform()
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
                val displayName = _uiState.value.username.ifBlank { DeviceIdProvider.shortId(deviceId) }
                sessionRepository.writeMember(code, displayName)
                _uiState.value = _uiState.value.copy(
                    sessionCode = code,
                    isInSession = true,
                    sessionLoading = false,
                    showSessionDialog = false,
                    detectionHistory = emptyList()
                )
                startStream(code)
                calibrateServerOffset()
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
                    val displayName = _uiState.value.username.ifBlank { DeviceIdProvider.shortId(deviceId) }
                    sessionRepository.writeMember(upperCode, displayName)
                    _uiState.value = _uiState.value.copy(
                        sessionCode = upperCode,
                        isInSession = true,
                        sessionLoading = false,
                        showSessionDialog = false,
                        detectionHistory = emptyList()
                    )
                    startStream(upperCode)
                    calibrateServerOffset()
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

    fun calibrateServerOffset() {
        viewModelScope.launch {
            try {
                val offset = sessionRepository.measureServerOffset()
                _uiState.value = _uiState.value.copy(serverOffsetMs = offset)
            } catch (e: Exception) {
                // Leave serverOffsetMs as-is — UI will show uncalibrated warning
            }
        }
    }

    fun leaveSession() {
        val sessionCode = _uiState.value.sessionCode
        if (sessionCode != null) {
            viewModelScope.launch {
                try { sessionRepository.updateListeningStatus(sessionCode, false) } catch (_: Exception) {}
            }
        }
        streamJob?.cancel()
        streamJob = null
        membersJob?.cancel()
        membersJob = null
        starredKeys.clear()
        _uiState.value = _uiState.value.copy(
            sessionCode = null,
            isInSession = false,
            detectionHistory = emptyList(),
            sessionMembers = emptyList(),
            lastDetectedTimestamp = "",
            serverOffsetMs = null
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
                        isMine = fd.deviceId == deviceId,
                        serverTimestampMillis = fd.serverTimestampMillis
                    )
                }
                _uiState.value = _uiState.value.copy(detectionHistory = mapped)
            }
        }

        membersJob?.cancel()
        membersJob = viewModelScope.launch {
            sessionRepository.streamMembers(sessionCode).collect { members ->
                val mapped = members.map { it.copy(isMine = it.deviceId == deviceId) }
                _uiState.value = _uiState.value.copy(sessionMembers = mapped)
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
        streamJob?.cancel()
        membersJob?.cancel()
        waveformIdleJob?.cancel()
        rmsJob?.cancel()
        detectionJob?.cancel()
    }

    private fun sliderToMultiplier(slider: Float): Float {
        val normalized = (slider - 1f) / 9f
        return 20f - (normalized * 16f)
    }
}
