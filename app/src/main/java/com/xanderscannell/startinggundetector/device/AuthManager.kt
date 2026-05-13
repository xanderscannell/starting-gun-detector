package com.xanderscannell.startinggundetector.device

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

object AuthManager {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val signInMutex = Mutex()

    fun startEagerSignIn(scope: CoroutineScope) {
        scope.launch { runCatching { requireUid() } }
    }

    suspend fun requireUid(): String {
        auth.currentUser?.uid?.let { return it }
        return signInMutex.withLock {
            auth.currentUser?.uid?.let { return@withLock it }
            val result = auth.signInAnonymously().await()
            result.user?.uid ?: error("Anonymous sign-in returned null user")
        }
    }

    fun currentUidOrNull(): String? = auth.currentUser?.uid
}
