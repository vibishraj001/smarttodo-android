package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.SettingsDataStore
import com.example.navigation.SmartTodoApp
import com.example.navigation.WidgetDeepLink
import com.example.ui.theme.SmartTodoTheme
import com.example.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val pendingWidgetDeepLink = mutableStateOf<WidgetDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWidgetIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        SettingsViewModel(SettingsDataStore(app))
                    }
                }
            )
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            SmartTodoTheme(themeMode = settings.themeMode) {
                SmartTodoApp(
                    settingsViewModel = settingsViewModel,
                    pendingWidgetDeepLink = pendingWidgetDeepLink.value,
                    onConsumeWidgetDeepLink = { pendingWidgetDeepLink.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_WIDGET_ACTION) ?: return
        when (action) {
            WIDGET_ACTION_ADD_TASK -> {
                pendingWidgetDeepLink.value = WidgetDeepLink.AddTask(System.nanoTime())
            }
            WIDGET_ACTION_EDIT_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_WIDGET_TASK_ID)
                if (!taskId.isNullOrBlank()) {
                    pendingWidgetDeepLink.value = WidgetDeepLink.EditTask(taskId, System.nanoTime())
                }
            }
        }
        intent.removeExtra(EXTRA_WIDGET_ACTION)
        intent.removeExtra(EXTRA_WIDGET_TASK_ID)
    }

    companion object {
        const val EXTRA_WIDGET_ACTION = "com.example.EXTRA_WIDGET_ACTION"
        const val EXTRA_WIDGET_TASK_ID = "com.example.EXTRA_WIDGET_TASK_ID"
        const val WIDGET_ACTION_ADD_TASK = "add_task"
        const val WIDGET_ACTION_EDIT_TASK = "edit_task"
    }
}
