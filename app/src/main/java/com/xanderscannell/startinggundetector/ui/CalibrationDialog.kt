package com.xanderscannell.startinggundetector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xanderscannell.startinggundetector.audio.ListeningService
import com.xanderscannell.startinggundetector.utils.TimestampFormatter
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private data class CalibrationSample(
    val detectedMillis: Long,
    val detectedFormatted: String,
    val realTimeInput: String = ""
)

private val timeInputFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

private fun parseRealTime(input: String, referenceMillis: Long): Long? {
    return try {
        val localTime = LocalTime.parse(input.trim(), timeInputFormatter)
        val zone = ZoneId.systemDefault()
        val refDate = Instant.ofEpochMilli(referenceMillis).atZone(zone).toLocalDate()
        localTime.atDate(refDate).atZone(zone).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationDialog(
    sensitivity: Float,
    onApplyOffset: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var samples by remember { mutableStateOf(listOf<CalibrationSample>()) }

    val startedService = remember { !ListeningService.isRunning }
    DisposableEffect(Unit) {
        if (startedService) {
            val multiplier = 20f - ((sensitivity - 1f) / 9f * 16f)
            ListeningService.start(context, multiplier)
        }
        onDispose {
            if (startedService) ListeningService.stop(context)
        }
    }

    LaunchedEffect(Unit) {
        ListeningService.detectionFlow.collect { event ->
            val formatted = TimestampFormatter.format(event.wallMillis)
            samples = samples + CalibrationSample(
                detectedMillis = event.wallMillis,
                detectedFormatted = formatted
            )
        }
    }

    val offsets = samples.mapNotNull { sample ->
        parseRealTime(sample.realTimeInput, sample.detectedMillis)
            ?.let { realMs -> realMs - sample.detectedMillis }
    }
    val computedOffset: Int? = if (offsets.isNotEmpty()) offsets.average().roundToInt() else null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Latency Calibration", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Make a loud sound, then enter the real time from a reference clock. Repeat for more accuracy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (samples.isEmpty()) {
                Text(
                    "No detections yet — make a loud sound.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("#", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.width(20.dp))
                    Text("Detected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                    Text("Real time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                    Text("Offset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.width(56.dp))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                samples.forEachIndexed { index, sample ->
                    val sampleOffset = parseRealTime(sample.realTimeInput, sample.detectedMillis)
                        ?.let { it - sample.detectedMillis }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            sample.detectedFormatted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sample.realTimeInput,
                            onValueChange = { input ->
                                samples = samples.mapIndexed { i, s ->
                                    if (i == index) s.copy(realTimeInput = input) else s
                                }
                            },
                            placeholder = {
                                Text(
                                    sample.detectedFormatted,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .height(52.dp)
                        )
                        Text(
                            text = sampleOffset?.let { "${if (it >= 0) "+" else ""}${it}ms" } ?: "—",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (sampleOffset != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                            modifier = Modifier.width(56.dp)
                        )
                    }

                    if (index < samples.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                    }
                }
            }

            if (computedOffset != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                val sign = if (computedOffset >= 0) "+" else ""
                Text(
                    "Computed offset: $sign${computedOffset}ms (${offsets.size} sample${if (offsets.size != 1) "s" else ""})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (computedOffset != null) {
                            onApplyOffset(computedOffset)
                            onDismiss()
                        }
                    },
                    enabled = computedOffset != null,
                    modifier = Modifier.weight(1f)
                ) {
                    val sign = if ((computedOffset ?: 0) >= 0) "+" else ""
                    Text(if (computedOffset != null) "Apply $sign${computedOffset}ms" else "Apply")
                }
            }
        }
    }
}
