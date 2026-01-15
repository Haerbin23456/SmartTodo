package com.example.smarttodo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttodo.data.AppDatabase
import com.example.smarttodo.data.RawMessage
import com.example.smarttodo.data.SmartTask
import com.example.smarttodo.logic.TaskProcessor
import com.example.smarttodo.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SmartTodoViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.todoDao()
    private val processingMutex = Mutex() // Mutex to serialize processing
    private val sharedPrefs = application.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()
    
    // Config State
    private val _apiKey = MutableStateFlow(sharedPrefs.getString(Constants.PREF_KEY_API_KEY, "") ?: "")
    val apiKey = _apiKey.asStateFlow()
    
    private val _apiBaseUrl = MutableStateFlow(sharedPrefs.getString(Constants.PREF_KEY_API_BASE_URL, Constants.DEFAULT_API_BASE_URL) ?: Constants.DEFAULT_API_BASE_URL)
    val apiBaseUrl = _apiBaseUrl.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.resetStuckMessages()
        }
    }

    private var processingJob: Job? = null

    val activeTasks = dao.getActiveTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val draftTasks = dao.getDraftTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Stream shows all messages (history), not just unprocessed
    val messageStream = dao.getAllRawMessages()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    fun saveApiConfig(key: String, url: String) {
        sharedPrefs.edit()
            .putString(Constants.PREF_KEY_API_KEY, key)
            .putString(Constants.PREF_KEY_API_BASE_URL, url)
            .apply()
        _apiKey.value = key
        _apiBaseUrl.value = url
    }

    fun processNewInput(content: String, sourceApp: String, existingRawId: Long? = null) {
        processingJob = viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                TaskProcessor.processContent(
                    content = content,
                    sourceApp = sourceApp,
                    dao = dao,
                    existingRawId = existingRawId,
                    apiKey = _apiKey.value,
                    baseUrl = _apiBaseUrl.value,
                    scope = viewModelScope
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
                processingJob = null
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        _isProcessing.value = false
        processingJob = null
    }

    fun cancelMessage(msgId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.cancelMessage(msgId)
        }
    }

    fun cancelAllMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.cancelAllMessages()
            cancelProcessing()
        }
    }

    fun confirmTask(task: SmartTask) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateSmartTask(task.copy(isDraft = false))
        }
    }

    suspend fun getTaskById(id: Long): SmartTask? {
        return dao.getTaskById(id)
    }

    suspend fun getTaskEvidence(taskId: Long): List<RawMessage> {
        return dao.getMessagesForTask(taskId)
    }

    fun updateTask(task: SmartTask) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateSmartTask(task)
        }
    }

    fun deleteTask(task: SmartTask) {
        viewModelScope.launch(Dispatchers.IO) {
            // Soft delete or hard delete?
            // User said "trash", but schema has status TRASH.
            // Let's mark as TRASH.
            dao.updateSmartTask(task.copy(status = SmartTask.STATUS_TRASH))
        }
    }

    fun toggleTaskCompletion(task: SmartTask) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = if (task.status == SmartTask.STATUS_DONE) SmartTask.STATUS_PENDING else SmartTask.STATUS_DONE
            dao.updateSmartTask(task.copy(status = newStatus))
        }
    }
}
