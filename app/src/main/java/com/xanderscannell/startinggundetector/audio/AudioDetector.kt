package com.xanderscannell.startinggundetector.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private const val TAG = "AudioDetector"
private const val SAMPLE_RATE = 44100
private const val COOLDOWN_MS = 2500L
private const val BASELINE_WINDOW_SAMPLES = 44100  // ~1 second of history
private const val ABSOLUTE_MIN_RMS = 500.0         // ignore near-silence
private const val CONFIRMATION_BUFFERS = 3         // buffers to wait for RMS drop confirmation (~5-10ms)
private const val CONFIRMATION_DROP_MULTIPLIER = 3.0 // RMS must drop below baseline × this to confirm

data class DetectionEvent(val elapsedNanos: Long, val wallMillis: Long)

class AudioDetector {

    // Called on detection; runs on Dispatchers.IO
    var onDetected: ((DetectionEvent) -> Unit)? = null

    // Sensitivity multiplier: how many times above baseline triggers detection.
    // Higher value = less sensitive (requires louder sound relative to baseline).
    var sensitivityMultiplier: Float = 8f

    suspend fun run() = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid min buffer size: $minBuffer")
            return@withContext
        }

        val bufferSize = minBuffer * 2
        val record = AudioRecord(
            MediaRecorder.AudioSource.UNPROCESSED,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return@withContext
        }

        val buffer = ShortArray(bufferSize / 2)
        val baseline = ArrayDeque<Double>(BASELINE_WINDOW_SAMPLES)
        var lastDetectionMs = 0L

        // Pending candidate: timestamp captured at spike, waiting for drop confirmation
        var pendingEvent: DetectionEvent? = null
        var confirmationBuffersRead = 0

        record.startRecording()
        Log.d(TAG, "Recording started, buffer=$bufferSize samples=${buffer.size}")

        try {
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val rms = computeRms(buffer, read)

                // Build rolling baseline
                repeat(read) { baseline.addLast(rms) }
                while (baseline.size > BASELINE_WINDOW_SAMPLES) baseline.removeFirst()

                val baselineRms = if (baseline.size > 0) baseline.average() else rms
                val now = System.currentTimeMillis()

                // --- Confirmation window ---
                if (pendingEvent != null) {
                    confirmationBuffersRead++
                    if (rms < baselineRms * CONFIRMATION_DROP_MULTIPLIER) {
                        // RMS dropped back — genuine transient, confirm
                        Log.d(TAG, "Confirmed: rms dropped to $rms after $confirmationBuffersRead buffers")
                        lastDetectionMs = now
                        onDetected?.invoke(pendingEvent!!)
                        pendingEvent = null
                    } else if (confirmationBuffersRead >= CONFIRMATION_BUFFERS) {
                        // RMS stayed elevated — sustained noise (wind etc), reject
                        Log.d(TAG, "Rejected: rms stayed at $rms after $confirmationBuffersRead buffers (wind/noise)")
                        pendingEvent = null
                    }
                    continue
                }

                // --- Candidate detection ---
                val cooldownExpired = (now - lastDetectionMs) > COOLDOWN_MS
                if (cooldownExpired &&
                    rms > ABSOLUTE_MIN_RMS &&
                    rms > baselineRms * sensitivityMultiplier
                ) {
                    // Capture timestamp at the spike now, before confirmation delay
                    val peakIndex = findPeakIndex(buffer, read)
                    // Offset is samples from peak to end of buffer — read() returns when the
                    // last sample is captured, so we subtract the tail, not the head.
                    val bufferOffsetMs = ((read - 1 - peakIndex).toLong() * 1000L) / SAMPLE_RATE
                    val capturedNanos = SystemClock.elapsedRealtimeNanos()
                    val capturedMillis = now - bufferOffsetMs

                    pendingEvent = DetectionEvent(capturedNanos, capturedMillis)
                    confirmationBuffersRead = 0
                    Log.d(TAG, "Candidate: rms=$rms baseline=$baselineRms peak=$peakIndex offset=${bufferOffsetMs}ms")
                }
            }
        } finally {
            record.stop()
            record.release()
            Log.d(TAG, "Recording stopped")
        }
    }

    private fun computeRms(buffer: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val s = buffer[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / count)
    }

    private fun findPeakIndex(buffer: ShortArray, count: Int): Int {
        var maxAbs = 0
        var peakIndex = 0
        for (i in 0 until count) {
            val abs = kotlin.math.abs(buffer[i].toInt())
            if (abs > maxAbs) {
                maxAbs = abs
                peakIndex = i
            }
        }
        return peakIndex
    }
}
