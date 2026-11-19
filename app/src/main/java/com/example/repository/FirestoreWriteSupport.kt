package com.example.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.google.android.gms.tasks.Task as GmsTask
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal const val FIRESTORE_BATCH_CHUNK_SIZE = 450
private const val OFFLINE_WRITE_WAIT_MS = 350L
private const val ONLINE_WRITE_WAIT_MS = 4_000L
private const val TAG = "FirestoreWriteSupport"

/**
 * Awaits a Firestore mutation [GmsTask] with offline-persistence awareness.
 *
 * Cloud Firestore's Android SDK applies writes immediately to its local `PersistentCacheSettings`
 * cache (so realtime `snapshots()` listeners update right away), but the returned [GmsTask] does
 * not resolve until the server acknowledges the commit. Awaiting indefinitely while offline would
 * freeze form-submission spinners and risk BroadcastReceiver ANRs in the home screen widget.
 *
 * - Online: awaits up to [ONLINE_WRITE_WAIT_MS] so server rejections (e.g. `PERMISSION_DENIED`)
 *   surface immediately to the caller.
 * - Offline (or stalled network): returns once the write is safely queued in the local persistent
 *   cache, attaching an async failure listener so any deferred server rejection on reconnect is
 *   still logged via [handleFirestoreError].
 */
internal suspend fun awaitFirestoreWrite(
    writeTask: GmsTask<Void>,
    operationType: OperationType,
    docPath: String,
    isOnline: () -> Boolean
) {
    val waitMs = if (isOnline()) ONLINE_WRITE_WAIT_MS else OFFLINE_WRITE_WAIT_MS
    var completedInWindow = false
    val result = withTimeoutOrNull(waitMs) {
        writeTask.await()
        completedInWindow = true
        Unit
    }
    if (!completedInWindow && result == null) {
        Log.d(TAG, "Queued ${operationType.value} on $docPath in local Firestore cache for sync.")
        writeTask.addOnFailureListener { deferredError ->
            handleFirestoreError(deferredError, operationType, docPath)
        }
    }
}

internal fun checkDeviceOnline(context: Context): Boolean {
    return try {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        true
    }
}
