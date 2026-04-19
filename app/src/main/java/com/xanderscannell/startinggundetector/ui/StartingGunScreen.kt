package com.xanderscannell.startinggundetector.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xanderscannell.startinggundetector.device.DeviceIdProvider
import com.xanderscannell.startinggundetector.utils.TimestampFormatter
import com.xanderscannell.startinggundetector.viewmodel.DetectorState
import com.xanderscannell.startinggundetector.viewmodel.UiState
import kotlinx.coroutines.delay

@Composable
fun StartingGunScreen(
    uiState: UiState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleStar: (Int) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onShowSessionDialog: () -> Unit,
    onDismissSessionDialog: () -> Unit,
    onCreateSession: () -> Unit,
    onJoinSession: (String) -> Unit,
    onLeaveSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var liveClock by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            liveClock = TimestampFormatter.format(System.currentTimeMillis())
            delay(100)
        }
    }

    if (uiState.showSessionDialog) {
        SessionDialog(
            loading = uiState.sessionLoading,
            error = uiState.sessionError,
            onCreateSession = onCreateSession,
            onJoinSession = onJoinSession,
            onDismiss = onDismissSessionDialog
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top ──────────────────────────────────────────────
        if (uiState.detectionHistory.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
        }

        Text(
            text = "Starting Gun Detector",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Session bar
        SessionBar(
            sessionCode = uiState.sessionCode,
            onJoinCreate = onShowSessionDialog,
            onLeave = onLeaveSession
        )

        Spacer(modifier = Modifier.height(12.dp))

        AutoSizeText(
            text = liveClock,
            maxFontSize = 52.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        StatusLabel(
            state = uiState.detectorState,
            lastDetected = uiState.lastDetectedTimestamp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── History ───────────────────────────────────────────
        if (uiState.detectionHistory.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (!uiState.isInSession) {
                    TextButton(onClick = onClearHistory) {
                        Text("Clear", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))

            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(uiState.detectionHistory) { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        // Device badge — only shown in session mode
                        if (uiState.isInSession && entry.deviceId.isNotEmpty()) {
                            DeviceBadge(
                                shortId = DeviceIdProvider.shortId(entry.deviceId),
                                isMine = entry.isMine,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        Text(
                            text = entry.timestamp,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = { onToggleStar(index) }) {
                            Icon(
                                imageVector = if (entry.starred) Icons.Filled.Star
                                              else Icons.Outlined.StarOutline,
                                contentDescription = if (entry.starred) "Unstar" else "Star",
                                tint = if (entry.starred) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                            )
                        }
                    }
                    if (index < uiState.detectionHistory.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // ── Bottom controls ───────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Red,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = "Sensitivity: ${uiState.sensitivity.toInt()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Slider(
            value = uiState.sensitivity,
            onValueChange = onSensitivityChange,
            valueRange = 1f..10f,
            steps = 8,
            enabled = uiState.detectorState == DetectorState.IDLE,
            modifier = Modifier.fillMaxWidth()
        )

        when (uiState.detectorState) {
            DetectorState.IDLE -> Button(
                onClick = onStartListening,
                modifier = Modifier.fillMaxWidth()
            ) { Text("START LISTENING") }
            DetectorState.LISTENING -> OutlinedButton(
                onClick = onStopListening,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) { Text("STOP") }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Device audio latency may vary. Calibrate for best results.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SessionBar(
    sessionCode: String?,
    onJoinCreate: () -> Unit,
    onLeave: () -> Unit
) {
    if (sessionCode != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Session: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = sessionCode,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onLeave) {
                Text("Leave", color = MaterialTheme.colorScheme.secondary)
            }
        }
    } else {
        TextButton(onClick = onJoinCreate) {
            Text(
                text = "Join / Create Session",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun DeviceBadge(shortId: String, isMine: Boolean, modifier: Modifier = Modifier) {
    val bg = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
             else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
    val fg = if (isMine) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.secondary

    Text(
        text = shortId,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

@Composable
private fun StatusLabel(state: DetectorState, lastDetected: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            DetectorState.IDLE -> Text(
                text = "TAP TO LISTEN",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            DetectorState.LISTENING -> {
                val transition = rememberInfiniteTransition(label = "pulse")
                val alpha by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                Text(
                    text = "LISTENING...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alpha(alpha)
                )
            }
        }
        if (lastDetected.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last: $lastDetected",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun AutoSizeText(
    text: String,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.Default,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified
) {
    var fontSize by remember(maxFontSize) { mutableStateOf(maxFontSize) }
    Text(
        text = text,
        fontSize = fontSize,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.hasVisualOverflow) fontSize = fontSize * 0.9f
        }
    )
}
