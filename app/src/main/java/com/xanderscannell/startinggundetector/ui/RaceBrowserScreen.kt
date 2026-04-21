package com.xanderscannell.startinggundetector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xanderscannell.startinggundetector.data.Race
import com.xanderscannell.startinggundetector.utils.TimestampFormatter
import com.xanderscannell.startinggundetector.viewmodel.RaceViewModel

private enum class BrowserLevel { MEETS, EVENTS, RACES }

@Composable
fun RaceBrowserPage(
    raceViewModel: RaceViewModel,
    onOpenRace: (String) -> Unit
) {
    val allRaces by raceViewModel.raceList.collectAsState()
    var level by remember { mutableStateOf(BrowserLevel.MEETS) }
    var selectedMeet by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedEvent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { raceViewModel.loadAllRaces() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (level != BrowserLevel.MEETS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    when (level) {
                        BrowserLevel.RACES -> { level = BrowserLevel.EVENTS; selectedEvent = null }
                        BrowserLevel.EVENTS -> { level = BrowserLevel.MEETS; selectedMeet = null }
                        else -> {}
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = when (level) {
                        BrowserLevel.EVENTS -> selectedMeet?.first ?: ""
                        BrowserLevel.RACES -> selectedEvent ?: ""
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
        }

        when (level) {
            BrowserLevel.MEETS -> MeetList(
                races = allRaces,
                onSelectMeet = { name, date ->
                    selectedMeet = name to date
                    level = BrowserLevel.EVENTS
                }
            )
            BrowserLevel.EVENTS -> {
                val (meetName, meetDate) = selectedMeet ?: return
                EventList(
                    races = allRaces.filter { it.meetName == meetName && it.meetDate == meetDate },
                    onSelectEvent = { eventName ->
                        selectedEvent = eventName
                        level = BrowserLevel.RACES
                    }
                )
            }
            BrowserLevel.RACES -> {
                val (meetName, meetDate) = selectedMeet ?: return
                val eventName = selectedEvent ?: return
                RaceList(
                    races = allRaces.filter {
                        it.meetName == meetName && it.meetDate == meetDate && it.eventName == eventName
                    },
                    onOpenRace = onOpenRace,
                    onDeleteRace = { raceId -> raceViewModel.deleteRace(raceId) }
                )
            }
        }
    }
}

@Composable
private fun MeetList(
    races: List<Race>,
    onSelectMeet: (String, String) -> Unit
) {
    val meets = races
        .groupBy { it.meetName to it.meetDate }
        .map { (key, rs) -> Triple(key.first, key.second, rs.size) }
        .sortedByDescending { it.second }

    if (meets.isEmpty()) {
        EmptyState("No saved races yet")
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(meets, key = { "${it.first}:${it.second}" }) { (name, date, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectMeet(name, date) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = "$count race${if (count != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
        }
    }
}

@Composable
private fun EventList(
    races: List<Race>,
    onSelectEvent: (String) -> Unit
) {
    val events = races
        .groupBy { it.eventName }
        .map { (name, rs) -> name to rs.size }
        .sortedBy { it.first }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(events, key = { it.first }) { (name, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectEvent(name) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "$count race${if (count != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
        }
    }
}

@Composable
private fun RaceList(
    races: List<Race>,
    onOpenRace: (String) -> Unit,
    onDeleteRace: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(races, key = { it.id }) { race ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRace(race.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = TimestampFormatter.format(race.createdAtMillis),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val splitCount = race.finishSplits.size
                    val startCount = race.startTimes.size
                    Text(
                        text = "$splitCount finish${if (splitCount != 1) "ers" else "er"}, $startCount start time${if (startCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = { onDeleteRace(race.id) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete race",
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}
