package com.example.workspace

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

sealed interface WorkspaceAuthResult {
    data class Authorized(val accessToken: String) : WorkspaceAuthResult
    data class NeedsConsent(val intentSender: IntentSender) : WorkspaceAuthResult
    data class Error(val message: String) : WorkspaceAuthResult
}

/**
 * Manages Google Workspace OAuth 2.0 scope authorization via Google Identity Services
 * [AuthorizationClient] for Calendar, Gmail, and Google Chat.
 */
object GoogleWorkspaceAuthManager {
    private const val TAG = "WorkspaceAuthManager"

    const val SCOPE_CALENDAR_EVENTS = "https://www.googleapis.com/auth/calendar.events"
    const val SCOPE_GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly"
    const val SCOPE_CHAT_SPACES_READONLY = "https://www.googleapis.com/auth/chat.spaces.readonly"
    const val SCOPE_CHAT_MESSAGES_CREATE = "https://www.googleapis.com/auth/chat.messages.create"

    val ALL_WORKSPACE_SCOPES: List<Scope> = listOf(
        Scope(SCOPE_CALENDAR_EVENTS),
        Scope(SCOPE_GMAIL_READONLY),
        Scope(SCOPE_CHAT_SPACES_READONLY),
        Scope(SCOPE_CHAT_MESSAGES_CREATE)
    )

    suspend fun authorizeWorkspace(
        context: Context,
        scopes: List<Scope> = ALL_WORKSPACE_SCOPES
    ): WorkspaceAuthResult {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(scopes)
                .build()
            val result = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()

            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    WorkspaceAuthResult.NeedsConsent(pendingIntent.intentSender)
                } else {
                    WorkspaceAuthResult.Error("Google authorization prompt could not be opened.")
                }
            } else {
                val token = result.accessToken
                if (!token.isNullOrBlank()) {
                    WorkspaceAuthResult.Authorized(token)
                } else {
                    WorkspaceAuthResult.Error("Authorization succeeded without an access token.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Google Workspace authorization failed", e)
            WorkspaceAuthResult.Error(
                e.localizedMessage ?: "Unable to authorize Google Workspace access."
            )
        }
    }

    fun handleAuthorizationIntentResult(context: Context, data: Intent?): WorkspaceAuthResult {
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (!token.isNullOrBlank()) {
                WorkspaceAuthResult.Authorized(token)
            } else {
                WorkspaceAuthResult.Error("Google Workspace permission was not granted.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse authorization intent result", e)
            WorkspaceAuthResult.Error(
                e.localizedMessage ?: "Google Workspace consent was cancelled or failed."
            )
        }
    }
}
