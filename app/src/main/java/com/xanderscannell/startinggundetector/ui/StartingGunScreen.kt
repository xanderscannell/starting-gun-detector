package com.xanderscannell.startinggundetector.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xanderscannell.startinggundetector.utils.TimestampFormatter
import com.xanderscannell.startinggundetector.viewmodel.DetectorState
import com.xanderscannell.startinggundetector.viewmodel.UiState
import kotlinx.coroutines.delay

@Composable
fun StartingGunScreen(
    uiState: UiState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onReset: () -> Unit,
    onSensitivityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var liveClock by remember { mutableStateOf("") }

    LaunchedEffect(uiState.detectorState) {
        if (uiState.detectorState != DetectorState.DETECTED) {
            while (true) {
                liveClock = TimestampFormatter.format(System.currentTimeMillis())
                delay(100)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Starting Gun Detector",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (uiState.detectorState != DetectorState.DETECTED) {
                AutoSizeText(
                    text = liveClock,
                    maxFontSize = 56.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StatusLabel(state = uiState.detectorState)

            if (uiState.detectorState == DetectorState.DETECTED) {
                Spacer(modifier = Modifier.height(24.dp))
                AutoSizeText(
                    text = uiState.detectedTimestamp,
                    maxFontSize = 64.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (uiState.detectorState == DetectorState.IDLE) {
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
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            when (uiState.detectorState) {
                DetectorState.IDLE -> {
                    Button(onClick = onStartListening, modifier = Modifier.fillMaxWidth()) {
                        Text("START LISTENING")
                    }
                }
                DetectorState.LISTENING -> {
                    OutlinedButton(
                        onClick = onStopListening,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("STOP")
                    }
                }
                DetectorState.DETECTED -> {
                    Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                        Text("RESET")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Device audio latency may vary. Calibrate for best results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red,
                    textAlign = TextAlign.Center
                )
            }
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
    color: androidx.compose.ui.graphics.Color = Color.Unspecified
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
            if (result.hasVisualOverflow) {
                fontSize = fontSize * 0.9f
            }
        }
    )
}

@Composable
private fun StatusLabel(state: DetectorState) {
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
        DetectorState.DETECTED -> Text(
            text = "DETECTED",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
