package com.xanderscannell.startinggundetector.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xanderscannell.startinggundetector.MainActivity
import com.xanderscannell.startinggundetector.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ListeningService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val detector = AudioDetector()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        detector.onDetected = { event ->
            serviceScope.launch { _detectionFlow.emit(event) }
        }
        detector.onRmsChanged = { rms ->
            _rmsFlow.value = rms
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sensitivity = intent?.getFloatExtra(EXTRA_SENSITIVITY, 8f) ?: 8f
        detector.sensitivityMultiplier = sensitivity
        startForeground(NOTIFICATION_ID, buildNotification())
        serviceScope.launch { detector.run() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        _rmsFlow.value = 0f
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Listening",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active while listening for the starting gun"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Listening for starting gun")
            .setContentText("Tap to return to app")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "listening_service"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_SENSITIVITY = "sensitivity"

        private val _detectionFlow = MutableSharedFlow<DetectionEvent>(extraBufferCapacity = 8)
        val detectionFlow: SharedFlow<DetectionEvent> = _detectionFlow.asSharedFlow()

        private val _rmsFlow = MutableStateFlow(0f)
        val rmsFlow: StateFlow<Float> = _rmsFlow.asStateFlow()

        fun start(context: Context, sensitivityMultiplier: Float) {
            val intent = Intent(context, ListeningService::class.java).apply {
                putExtra(EXTRA_SENSITIVITY, sensitivityMultiplier)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListeningService::class.java))
        }
    }
}
