package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Task
import com.example.workspace.CalendarSyncResult
import com.example.workspace.GoogleWorkspaceClient
import com.example.workspace.WorkspaceApiClient
import com.example.workspace.WorkspaceCalendarEvent
import com.example.workspace.WorkspaceChatSpace
import com.example.workspace.WorkspaceEmailItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val accessToken: String? = null,
    val isConnecting: Boolean = false,
    val isLoadingCalendar: Boolean = false,
    val isLoadingGmail: Boolean = false,
    val isLoadingChat: Boolean = false,
    val isSyncingAction: Boolean = false,
    val calendarEvents: List<WorkspaceCalendarEvent> = emptyList(),
    val emails: List<WorkspaceEmailItem> = emptyList(),
    val chatSpaces: List<WorkspaceChatSpace> = emptyList(),
    val calendarError: String? = null,
    val gmailError: String? = null,
    val chatError: String? = null,
    val authError: String? = null
) {
    val isConnected: Boolean get() = !accessToken.isNullOrBlank()
}

class WorkspaceViewModel(
    private val apiClient: WorkspaceApiClient = GoogleWorkspaceClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    fun setConnecting(connecting: Boolean) {
        _uiState.update { it.copy(isConnecting = connecting, authError = null) }
    }

    fun onAuthError(message: String) {
        _uiState.update { it.copy(isConnecting = false, authError = message) }
    }

    fun onAccessTokenObtained(token: String) {
        _uiState.update {
            it.copy(
                accessToken = token,
                isConnecting = false,
                authError = null
            )
        }
        refreshAll()
    }

    fun disconnect() {
        _uiState.value = WorkspaceUiState()
    }

    fun refreshAll() {
        refreshCalendar()
        refreshGmail()
        refreshChatSpaces()
    }

    fun refreshCalendar() {
        val token = _uiState.value.accessToken ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCalendar = true, calendarError = null) }
            apiClient.fetchUpcomingCalendarEvents(token).fold(
                onSuccess = { events ->
                    _uiState.update {
                        it.copy(isLoadingCalendar = false, calendarEvents = events, calendarError = null)
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingCalendar = false,
                            calendarError = err.message ?: "Unable to load Google Calendar events."
                        )
                    }
                }
            )
        }
    }

    fun refreshGmail() {
        val token = _uiState.value.accessToken ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGmail = true, gmailError = null) }
            apiClient.fetchRecentGmailMessages(token).fold(
                onSuccess = { messages ->
                    _uiState.update {
                        it.copy(isLoadingGmail = false, emails = messages, gmailError = null)
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingGmail = false,
                            gmailError = err.message ?: "Unable to load Gmail messages."
                        )
                    }
                }
            )
        }
    }

    fun refreshChatSpaces() {
        val token = _uiState.value.accessToken ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChat = true, chatError = null) }
            apiClient.fetchChatSpaces(token).fold(
                onSuccess = { spaces ->
                    _uiState.update {
                        it.copy(isLoadingChat = false, chatSpaces = spaces, chatError = null)
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingChat = false,
                            chatError = err.message ?: "Unable to load Google Chat spaces."
                        )
                    }
                }
            )
        }
    }

    fun syncTaskToCalendar(
        task: Task,
        onResult: (Result<CalendarSyncResult>) -> Unit
    ) {
        val token = _uiState.value.accessToken
        if (token.isNullOrBlank()) {
            onResult(Result.failure(IllegalStateException("Connect Google Workspace first.")))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingAction = true) }
            val result = apiClient.syncTaskToCalendar(token, task)
            _uiState.update { it.copy(isSyncingAction = false) }
            if (result.isSuccess) {
                refreshCalendar()
            }
            onResult(result)
        }
    }

    fun postTaskToChatSpace(
        space: WorkspaceChatSpace,
        task: Task,
        onResult: (Result<String>) -> Unit
    ) {
        val token = _uiState.value.accessToken
        if (token.isNullOrBlank()) {
            onResult(Result.failure(IllegalStateException("Connect Google Workspace first.")))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingAction = true) }
            val result = apiClient.postTaskToChatSpace(token, space.name, task)
            _uiState.update { it.copy(isSyncingAction = false) }
            onResult(result)
        }
    }
}
