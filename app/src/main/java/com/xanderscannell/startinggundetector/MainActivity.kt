package com.xanderscannell.startinggundetector

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xanderscannell.startinggundetector.device.DeviceIdProvider
import com.xanderscannell.startinggundetector.device.UserPreferences
import com.xanderscannell.startinggundetector.session.SessionRepository
import com.xanderscannell.startinggundetector.ui.StartingGunScreen
import com.xanderscannell.startinggundetector.ui.theme.StartingGunTheme
import com.xanderscannell.startinggundetector.viewmodel.GunShotViewModel
import com.xanderscannell.startinggundetector.viewmodel.GunShotViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StartingGunTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val deviceId = remember { DeviceIdProvider.getDeviceId(context) }
    val sessionRepository = remember { SessionRepository(deviceId) }
    val userPreferences = remember { UserPreferences(context) }
    val vm: GunShotViewModel = viewModel(
        factory = GunShotViewModelFactory(deviceId, sessionRepository, userPreferences)
    )
    val uiState by vm.uiState.collectAsState()

    var permissionGranted by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (!granted) showRationale = true
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Microphone Permission Required") },
            text = { Text("This app needs microphone access to detect the starting gun. Please grant the permission in Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Cancel") }
            }
        )
    }

    if (permissionGranted) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            StartingGunScreen(
                uiState = uiState,
                onStartListening = vm::startListening,
                onStopListening = vm::stopListening,
                onClearHistory = vm::clearHistory,
                onToggleStar = vm::toggleStar,
                onSensitivityChange = vm::setSensitivity,
                onLatencyOffsetChange = vm::setLatencyOffset,
                onUsernameChange = vm::setUsername,
                onShowSessionDialog = vm::showSessionDialog,
                onDismissSessionDialog = vm::dismissSessionDialog,
                onCreateSession = vm::createSession,
                onJoinSession = vm::joinSession,
                onLeaveSession = vm::leaveSession,
                modifier = Modifier.padding(innerPadding)
            )
        }
    } else {
        NoPermissionScreen()
    }
}

@Composable
private fun NoPermissionScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Microphone permission not granted.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Restart the app and accept the permission request.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
