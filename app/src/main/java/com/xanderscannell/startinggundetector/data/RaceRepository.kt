package com.xanderscannell.startinggundetector.data

import kotlinx.serialization.json.Json
import java.io.File

data class MeetSummary(
    val meetName: String,
    val meetDate: String,
    val raceCount: Int
)

data class EventSummary(
    val eventName: String,
    val raceIds: List<String>
)

class RaceRepository(private val racesDir: File) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        racesDir.mkdirs()
    }

    fun listAllRaces(): List<Race> {
        val dirs = racesDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val jsonFile = File(dir, "race.json")
            if (!jsonFile.exists()) return@mapNotNull null
            try {
                json.decodeFromString<Race>(jsonFile.readText())
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.createdAtMillis }
    }

    fun loadRace(raceId: String): Race? {
        val jsonFile = File(racesDir, "$raceId/race.json")
        if (!jsonFile.exists()) return null
        return try {
            json.decodeFromString<Race>(jsonFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun saveRace(race: Race) {
        val raceDir = File(racesDir, race.id)
        raceDir.mkdirs()
        val tmpFile = File(raceDir, "race.json.tmp")
        val targetFile = File(raceDir, "race.json")
        tmpFile.writeText(json.encodeToString(Race.serializer(), race))
        tmpFile.renameTo(targetFile)
    }

    fun moveVideoToRace(raceId: String, sourceFile: File) {
        val raceDir = File(racesDir, raceId)
        raceDir.mkdirs()
        val target = File(raceDir, "video.mp4")
        if (!sourceFile.renameTo(target)) {
            sourceFile.copyTo(target, overwrite = true)
            sourceFile.delete()
        }
    }

    fun getRaceVideoFile(raceId: String): File {
        return File(racesDir, "$raceId/video.mp4")
    }

    fun deleteRace(raceId: String) {
        val raceDir = File(racesDir, raceId)
        raceDir.deleteRecursively()
    }

    fun listMeets(): List<MeetSummary> {
        return listAllRaces()
            .groupBy { it.meetName to it.meetDate }
            .map { (key, races) -> MeetSummary(key.first, key.second, races.size) }
            .sortedByDescending { it.meetDate }
    }

    fun listEventsForMeet(meetName: String, meetDate: String): List<EventSummary> {
        return listAllRaces()
            .filter { it.meetName == meetName && it.meetDate == meetDate }
            .groupBy { it.eventName }
            .map { (name, races) -> EventSummary(name, races.map { it.id }) }
            .sortedBy { it.eventName }
    }

    companion object {
        fun cleanupOrphanedVideos(filesDir: File, maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
            filesDir.listFiles()
                ?.filter { it.name.startsWith("capture_") && it.name.endsWith(".mp4") }
                ?.filter { System.currentTimeMillis() - it.lastModified() > maxAgeMs }
                ?.forEach { it.delete() }
        }
    }
}
