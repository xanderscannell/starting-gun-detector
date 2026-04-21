package com.xanderscannell.startinggundetector.session

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.xanderscannell.startinggundetector.device.DeviceIdProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class FirestoreDetection(
    val timestamp: String,
    val deviceId: String,
    val displayName: String,
    val clientTimestamp: Long
)

data class SessionMember(
    val deviceId: String,
    val displayName: String,
    val isListening: Boolean = false,
    val isMine: Boolean = false
)

class SessionRepository(private val deviceId: String) {

    private val db = FirebaseFirestore.getInstance()
    private val codeChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private fun generateCode(): String = (1..4).map { codeChars.random() }.joinToString("")

    suspend fun createSession(): String {
        var code: String
        var attempts = 0
        do {
            code = generateCode()
            val exists = db.collection("sessions").document(code).get().await().exists()
            if (!exists) break
            attempts++
        } while (attempts < 5)

        db.collection("sessions").document(code).set(
            mapOf(
                "createdBy" to deviceId,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return code
    }

    suspend fun joinSession(code: String): Boolean {
        return db.collection("sessions").document(code.uppercase()).get().await().exists()
    }

    suspend fun writeDetection(sessionCode: String, timestamp: String, displayName: String) {
        db.collection("sessions").document(sessionCode)
            .collection("detections")
            .add(
                mapOf(
                    "timestamp" to timestamp,
                    "deviceId" to deviceId,
                    "displayName" to displayName,
                    "clientTimestamp" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun writeMember(sessionCode: String, displayName: String) {
        db.collection("sessions").document(sessionCode)
            .collection("members").document(deviceId)
            .set(
                mapOf(
                    "displayName" to displayName,
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun updateListeningStatus(sessionCode: String, isListening: Boolean) {
        db.collection("sessions").document(sessionCode)
            .collection("members").document(deviceId)
            .set(
                mapOf("listening" to isListening),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
    }

    fun streamMembers(sessionCode: String): Flow<List<SessionMember>> = callbackFlow {
        val listener = db.collection("sessions").document(sessionCode)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val members = snapshot.documents.mapNotNull { doc ->
                    val displayName = doc.getString("displayName") ?: return@mapNotNull null
                    val isListening = doc.getBoolean("listening") ?: false
                    SessionMember(doc.id, displayName, isListening)
                }
                trySend(members)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Measures the offset between this device's clock and the Firestore server clock.
     * Uses the NTP midpoint formula: serverOffset = serverTime − midpoint(t1, t2)
     * where t1/t2 are client times before/after the write round-trip.
     * Returns serverOffset in milliseconds — add this to System.currentTimeMillis() to get
     * a server-relative timestamp.
     */
    suspend fun measureServerOffset(): Long {
        val docRef = db.collection("calibrations").document(deviceId)
        val t1 = System.currentTimeMillis()
        docRef.set(mapOf(
            "clientTimestamp" to t1,
            "serverTimestamp" to FieldValue.serverTimestamp()
        )).await()
        val t2 = System.currentTimeMillis()
        val serverTimestamp = docRef.get().await()
            .getTimestamp("serverTimestamp")?.toDate()?.time ?: return 0L
        return serverTimestamp - (t1 + t2) / 2
    }

    fun streamDetections(sessionCode: String): Flow<List<FirestoreDetection>> = callbackFlow {
        val listener = db.collection("sessions").document(sessionCode)
            .collection("detections")
            .orderBy("clientTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val detections = snapshot.documents.mapNotNull { doc ->
                    val timestamp = doc.getString("timestamp") ?: return@mapNotNull null
                    val docDeviceId = doc.getString("deviceId") ?: return@mapNotNull null
                    val displayName = doc.getString("displayName")
                        ?: DeviceIdProvider.shortId(docDeviceId)
                    val clientTimestamp = doc.getLong("clientTimestamp") ?: 0L
                    FirestoreDetection(timestamp, docDeviceId, displayName, clientTimestamp)
                }
                trySend(detections)
            }
        awaitClose { listener.remove() }
    }
}
