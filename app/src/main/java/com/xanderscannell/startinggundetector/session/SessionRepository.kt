package com.xanderscannell.startinggundetector.session

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class FirestoreDetection(
    val timestamp: String,
    val deviceId: String,
    val clientTimestamp: Long
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

    suspend fun writeDetection(sessionCode: String, timestamp: String) {
        db.collection("sessions").document(sessionCode)
            .collection("detections")
            .add(
                mapOf(
                    "timestamp" to timestamp,
                    "deviceId" to deviceId,
                    "clientTimestamp" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
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
                    val clientTimestamp = doc.getLong("clientTimestamp") ?: 0L
                    FirestoreDetection(timestamp, docDeviceId, clientTimestamp)
                }
                trySend(detections)
            }
        awaitClose { listener.remove() }
    }
}
