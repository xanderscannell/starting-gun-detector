package com.xanderscannell.startinggundetector.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.xanderscannell.startinggundetector.utils.TimestampFormatter
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun CapturePage(
    isInSession: Boolean,
    serverOffsetMs: Long?,
    gunServerTimestampMillis: Long?,
    onCalibrateServerOffset: () -> Unit
) {
    var permissionGranted by remember { mutableStateOf(false) }
    var reviewMode by remember { mutableStateOf(false) }
    // Lifted so both record and scrubber views can access these
    var savedFile by remember { mutableStateOf<File?>(null) }
    var recordingStartMillis by remember { mutableStateOf<Long?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!permissionGranted) {
        NoCameraPermissionMessage()
        return
    }

    if (reviewMode && savedFile != null && recordingStartMillis != null) {
        VideoScrubber(
            videoFile = savedFile!!,
            recordingStartMillis = recordingStartMillis!!,
            serverOffsetMs = serverOffsetMs,
            gunServerTimestampMillis = gunServerTimestampMillis,
            onBack = { reviewMode = false }
        )
    } else {
        CameraPreviewWithRecording(
            isInSession = isInSession,
            serverOffsetMs = serverOffsetMs,
            savedFile = savedFile,
            onCalibrateServerOffset = onCalibrateServerOffset,
            onRecordingComplete = { file, startMillis ->
                savedFile = file
                recordingStartMillis = startMillis
                onCalibrateServerOffset()
            },
            onOpenScrubber = { reviewMode = true }
        )
    }
}

@Composable
private fun CameraPreviewWithRecording(
    isInSession: Boolean,
    serverOffsetMs: Long?,
    savedFile: File?,
    onCalibrateServerOffset: () -> Unit,
    onRecordingComplete: (File, Long) -> Unit,
    onOpenScrubber: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isRecording by remember { mutableStateOf(false) }
    val activeRecording = remember { mutableStateOf<Recording?>(null) }

    // Clock — updates every 16ms. rememberUpdatedState ensures the loop always reads
    // the latest serverOffsetMs without restarting when calibration completes.
    var clockText by remember { mutableStateOf("") }
    val currentOffset by rememberUpdatedState(serverOffsetMs)
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis() + (currentOffset ?: 0L)
            clockText = TimestampFormatter.format(now)
            delay(16)
        }
    }

    val previewUseCase = remember { Preview.Builder().build() }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
    }
    val videoCaptureUseCase: VideoCapture<Recorder> = remember { VideoCapture.withOutput(recorder) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                previewUseCase,
                                videoCaptureUseCase
                            )
                        } catch (e: Exception) {
                            // Camera binding failed — device may not support back camera
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top overlay: sync status + saved file info
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isInSession) {
                if (serverOffsetMs == null) {
                    OutlinedButton(
                        onClick = onCalibrateServerOffset,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clock not synced — tap to calibrate", fontSize = 12.sp)
                    }
                } else {
                    val sign = if (serverOffsetMs >= 0) "+" else ""
                    Text(
                        text = "Clock synced (${sign}${serverOffsetMs}ms)",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
            savedFile?.let { file ->
                val sizeKb = file.length() / 1024
                Text(
                    text = "Saved: ${file.name} (${sizeKb}KB)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        // Clock overlay — bottom-left, dark pill, always visible
        Text(
            text = clockText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 96.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )

        // Bottom buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (savedFile != null && !isRecording) {
                OutlinedButton(
                    onClick = onOpenScrubber,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("REVIEW")
                }
            }
            if (isRecording) {
                OutlinedButton(
                    onClick = {
                        activeRecording.value?.stop()
                        activeRecording.value = null
                        isRecording = false
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("STOP")
                }
            } else {
                Button(
                    onClick = {
                        onCalibrateServerOffset()
                        val fileName = "capture_${System.currentTimeMillis()}.mp4"
                        val file = File(context.filesDir, fileName)
                        val outputOptions = FileOutputOptions.Builder(file).build()
                        var startMillis = 0L
                        activeRecording.value = videoCaptureUseCase.output
                            .prepareRecording(context, outputOptions)
                            .start(ContextCompat.getMainExecutor(context)) { event ->
                                if (event is VideoRecordEvent.Start) {
                                    startMillis = System.currentTimeMillis()
                                } else if (event is VideoRecordEvent.Finalize) {
                                    onRecordingComplete(file, startMillis)
                                }
                            }
                        isRecording = true
                    }
                ) {
                    Text("RECORD")
                }
            }
        }
    }
}

@Composable
private fun VideoScrubber(
    videoFile: File,
    recordingStartMillis: Long,
    serverOffsetMs: Long?,
    gunServerTimestampMillis: Long?,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    var duration by remember { mutableStateOf(0L) }
    var sliderPosition by remember { mutableStateOf(0L) }
    val currentOffset by rememberUpdatedState(serverOffsetMs)
    var finishers by remember { mutableStateOf(listOf<Long>()) }
    var frameStepMs by remember { mutableStateOf(33L) }

    LaunchedEffect(player) {
        while (duration <= 0L) {
            val d = player.duration
            if (d > 0L) duration = d
            delay(50)
        }
        val format = player.videoFormat
        if (format != null && format.frameRate > 0f) {
            frameStepMs = (1000.0 / format.frameRate).toLong().coerceAtLeast(1L)
        }
    }

    val frameTimestamp = remember(sliderPosition, serverOffsetMs) {
        TimestampFormatter.format(recordingStartMillis + (currentOffset ?: 0L) + sliderPosition)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to camera",
                    tint = Color.White
                )
            }
            Text(
                text = "Review",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Video player — seeks live during drag, showing actual frames at full resolution
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Timestamp display
        Text(
            text = frameTimestamp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )

        // Seek slider — CLOSEST_SYNC during drag for fast keyframe previews,
        // EXACT on release for the precise frame needed for timing.
        Slider(
            value = if (duration > 0L) sliderPosition.toFloat() else 0f,
            onValueChange = { pos ->
                sliderPosition = pos.toLong()
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                player.seekTo(sliderPosition)
            },
            onValueChangeFinished = {
                player.setSeekParameters(SeekParameters.EXACT)
                player.seekTo(sliderPosition)
                sliderPosition = player.currentPosition
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // Frame step buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    player.setSeekParameters(SeekParameters.EXACT)
                    sliderPosition = (sliderPosition - frameStepMs).coerceAtLeast(0L)
                    player.seekTo(sliderPosition)
                    sliderPosition = player.currentPosition
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("◀ Frame")
            }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(
                onClick = {
                    player.setSeekParameters(SeekParameters.EXACT)
                    sliderPosition = (sliderPosition + frameStepMs).coerceAtMost(duration)
                    player.seekTo(sliderPosition)
                    sliderPosition = player.currentPosition
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Frame ▶")
            }
        }

        // Mark Finish button
        Button(
            onClick = {
                val frameMillis = recordingStartMillis + (currentOffset ?: 0L) + sliderPosition
                finishers = finishers + frameMillis
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("MARK FINISH")
        }

        // Finisher results list
        if (finishers.isNotEmpty()) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Finishers",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
                TextButton(onClick = { finishers = finishers.dropLast(1) }) {
                    Text("Undo", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                itemsIndexed(finishers) { index, frameMillis ->
                    val split = gunServerTimestampMillis?.let { formatSplit(frameMillis - it) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ordinal(index + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            text = TimestampFormatter.format(frameMillis),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        if (split != null) {
                            Text(
                                text = split,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (index < finishers.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.White.copy(alpha = 0.06f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

private fun formatSplit(millis: Long): String {
    val abs = if (millis < 0) -millis else millis
    val sign = if (millis < 0) "-" else "+"
    val m = abs / 60000
    val s = (abs % 60000) / 1000
    val ms = abs % 1000
    return if (m > 0) {
        "$sign$m:${s.toString().padStart(2, '0')}.${ms.toString().padStart(3, '0')}"
    } else {
        "$sign$s.${ms.toString().padStart(3, '0')}"
    }
}

@Composable
private fun NoCameraPermissionMessage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera permission required",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Grant camera access to use finish line capture.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}
