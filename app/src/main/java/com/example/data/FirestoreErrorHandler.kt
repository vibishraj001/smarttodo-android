package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun toUserFriendlyFirestoreMessage(exception: Throwable, fallback: String): String {
    val firestoreException = generateSequence(exception) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()

    return when (firestoreException?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "You do not have permission to perform this action. Please sign in again."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Cloud sync is temporarily unavailable. Your changes will retry when connected."
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "The request timed out. Please check your internet connection."
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            "Daily database quota exceeded. Please try again later."
        FirebaseFirestoreException.Code.NOT_FOUND ->
            "This task could not be found. It may have been deleted."
        else -> {
            if (firestoreException != null) {
                fallback
            } else {
                val msg = exception.message
                if (!msg.isNullOrBlank() && !msg.contains("PERMISSION_DENIED")) {
                    msg
                } else {
                    fallback
                }
            }
        }
    }
}
