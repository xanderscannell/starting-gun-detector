package com.xanderscannell.startinggundetector.data

import kotlinx.serialization.Serializable

@Serializable
data class Race(
    val id: String,
    val meetName: String,
    val meetDate: String,
    val eventName: String,
    val createdAtMillis: Long,
    val recordingStartMillis: Long,
    val serverOffsetMs: Long? = null,
    val startTimes: List<StartTime> = emptyList(),
    val officialStartTimeId: String? = null,
    val finishSplits: List<FinishSplit> = emptyList(),
    val videoFileName: String = "video.mp4"
)

@Serializable
data class StartTime(
    val id: String,
    val timestampMillis: Long,
    val source: StartTimeSource,
    val label: String = ""
)

@Serializable
enum class StartTimeSource {
    AUDIO_DETECTION,
    FIRESTORE_SESSION,
    MANUAL_ENTRY
}

@Serializable
data class FinishSplit(
    val place: Int,
    val timestampMillis: Long
)
