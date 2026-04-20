package com.xanderscannell.startinggundetector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xanderscannell.startinggundetector.utils.TimestampFormatter
import com.xanderscannell.startinggundetector.viewmodel.DetectorState
import com.xanderscannell.startinggundetector.viewmodel.UiState
import com.xanderscannell.startinggundetector.viewmodel.WaveformBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppPage { LISTEN, SESSION, CAPTURE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartingGunScreen(
    uiState: UiState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleStar: (Int) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onLatencyOffsetChange: (Int) -> Unit,
    onUsernameChange: (String) -> Unit,
    onCreateSession: () -> Unit,
    onJoinSession: (String) -> Unit,
    onLeaveSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(AppPage.LISTEN) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showSettingsSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var liveClock by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            liveClock = TimestampFormatter.format(System.currentTimeMillis())
            delay(16)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentPage = currentPage,
                uiState = uiState,
                onNavigate = { page ->
                    currentPage = page
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            AppTopBar(
                currentPage = currentPage,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenSettings = { showSettingsSheet = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))

            when (currentPage) {
                AppPage.LISTEN -> ListenPage(
                    uiState = uiState,
                    liveClock = liveClock,
                    onStartListening = onStartListening,
                    onStopListening = onStopListening,
                    onClearHistory = onClearHistory,
                    onToggleStar = onToggleStar,
                    onSensitivityChange = onSensitivityChange
                )
                AppPage.SESSION -> SessionPage(
                    uiState = uiState,
                    onCreateSession = onCreateSession,
                    onJoinSession = onJoinSession,
                    onLeaveSession = onLeaveSession,
                    onUsernameChange = onUsernameChange
                )
                AppPage.CAPTURE -> CapturePage()
            }
        }

        if (showSettingsSheet) {
            SettingsSheet(
                uiState = uiState,
                onSensitivityChange = onSensitivityChange,
                onLatencyOffsetChange = onLatencyOffsetChange,
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}

@Composable
private fun AppTopBar(
    currentPage: AppPage,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open navigation",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        Text(
            text = currentPage.name.lowercase().replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            letterSpacing = 1.sp
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun AppDrawerContent(
    currentPage: AppPage,
    uiState: UiState,
    onNavigate: (AppPage) -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Starting Gun Detector",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = uiState.username.ifBlank { "Detector" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        NavigationDrawerItem(
            label = { Text("Listen") },
            selected = currentPage == AppPage.LISTEN,
            onClick = { onNavigate(AppPage.LISTEN) },
            icon = { Icon(Icons.Default.Mic, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text("Capture") },
            selected = currentPage == AppPage.CAPTURE,
            onClick = { onNavigate(AppPage.CAPTURE) },
            icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text("Session") },
            selected = currentPage == AppPage.SESSION,
            onClick = { onNavigate(AppPage.SESSION) },
            icon = { Icon(Icons.Default.Group, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        Spacer(modifier = Modifier.weight(1f))

        if (uiState.isInSession && uiState.sessionCode != null) {
            HorizontalDivider()
            Text(
                text = "Session: ${uiState.sessionCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    uiState: UiState,
    onSensitivityChange: (Float) -> Unit,
    onLatencyOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
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
            Spacer(modifier = Modifier.height(16.dp))
            LatencyOffsetControl(
                offsetMs = uiState.latencyOffsetMs,
                onOffsetChange = onLatencyOffsetChange
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ListenPage(
    uiState: UiState,
    liveClock: String,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleStar: (Int) -> Unit,
    onSensitivityChange: (Float) -> Unit
) {
    var sensExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.detectionHistory.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.detectionHistory.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            )
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
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
                        if (uiState.isInSession && entry.displayName.isNotEmpty()) {
                            DeviceBadge(
                                label = entry.displayName,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
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
            }
        }

        // Collapsible sensitivity quick strip
        HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { sensExpanded = !sensExpanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sensitivity: ${uiState.sensitivity.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Icon(
                imageVector = if (sensExpanded) Icons.Default.KeyboardArrowUp
                              else Icons.Default.KeyboardArrowDown,
                contentDescription = if (sensExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = sensExpanded) {
            Slider(
                value = uiState.sensitivity,
                onValueChange = onSensitivityChange,
                valueRange = 1f..10f,
                steps = 8,
                enabled = uiState.detectorState == DetectorState.IDLE,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        LoudnessVisualizer(
            bars = uiState.waveformBars,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
        )

        when (uiState.detectorState) {
            DetectorState.IDLE -> Button(
                onClick = onStartListening,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
            ) { Text("START LISTENING") }
            DetectorState.LISTENING -> OutlinedButton(
                onClick = onStopListening,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) { Text("STOP") }
        }
    }
}

@Composable
private fun SessionPage(
    uiState: UiState,
    onCreateSession: () -> Unit,
    onJoinSession: (String) -> Unit,
    onLeaveSession: () -> Unit,
    onUsernameChange: (String) -> Unit
) {
    var joinCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your Name",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text("Shown in sessions") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(16.dp))

        if (!uiState.isInSession) {
            Text(
                text = "Join Session",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isLetterOrDigit() }.uppercase()
                        if (filtered.length <= 4) joinCode = filtered
                    },
                    placeholder = { Text("A3K9") },
                    singleLine = true,
                    enabled = !uiState.sessionLoading,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (joinCode.length == 4) onJoinSession(joinCode) }
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onJoinSession(joinCode) },
                    enabled = joinCode.length == 4 && !uiState.sessionLoading
                ) { Text("Join") }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "— or —",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onCreateSession,
                enabled = !uiState.sessionLoading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create New Session") }

            if (uiState.sessionLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            if (uiState.sessionError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.sessionError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Active Session",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = uiState.sessionCode ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 4.sp
                    )
                }
                TextButton(onClick = onLeaveSession) {
                    Text("Leave", color = MaterialTheme.colorScheme.secondary)
                }
            }

            if (uiState.detectionHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Detections",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                uiState.detectionHistory.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DeviceBadge(label = entry.displayName, isMine = entry.isMine)
                        Text(
                            text = entry.timestamp,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = if (entry.isMine) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CapturePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Capture Mode",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Finish line camera sync coming soon.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Requires an active session.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun DeviceBadge(label: String, isMine: Boolean, modifier: Modifier = Modifier) {
    val bg = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
             else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
    val fg = if (isMine) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.secondary
    Text(
        text = label,
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
                text = "READY",
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
private fun LatencyOffsetControl(offsetMs: Int, onOffsetChange: (Int) -> Unit) {
    val label = when {
        offsetMs > 0 -> "+${offsetMs}ms"
        offsetMs < 0 -> "${offsetMs}ms"
        else -> "0ms"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Latency offset: $label",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onOffsetChange(offsetMs - 10) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease offset")
            }
            FilledTonalIconButton(onClick = { onOffsetChange(offsetMs + 10) }) {
                Icon(Icons.Filled.Add, contentDescription = "Increase offset")
            }
        }
    }
}

@Composable
private fun LoudnessVisualizer(
    bars: List<WaveformBar>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
    val detectionColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.height(48.dp)) {
        val totalSlots = 60
        val slotWidth = size.width / totalSlots
        val gap = 2.dp.toPx()
        val barWidth = (slotWidth - gap).coerceAtLeast(1f)
        val maxHeight = size.height

        bars.forEachIndexed { i, bar ->
            val slotIndex = totalSlots - bars.size + i
            val barHeight = (bar.normalizedRms * maxHeight).coerceAtLeast(2.dp.toPx())
            val x = slotIndex * slotWidth
            val y = maxHeight - barHeight
            drawRect(
                color = if (bar.isDetection) detectionColor else barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
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
