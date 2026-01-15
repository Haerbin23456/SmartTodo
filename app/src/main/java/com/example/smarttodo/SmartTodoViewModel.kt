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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class SmartTodoViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.todoDao()
    private val sharedPrefs = application.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    // Derived processing state from database stream
    val messageStream = dao.getAllRawMessages()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isProcessing = messageStream.map { messages ->
        messages.any { it.status == RawMessage.STATUS_PROCESSING || it.status == RawMessage.STATUS_PENDING }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    // Config State
    private val _apiKey = MutableStateFlow(sharedPrefs.getString(Constants.PREF_KEY_API_KEY, "") ?: "")
    val apiKey = _apiKey.asStateFlow()
    
    private val _apiBaseUrl = MutableStateFlow(sharedPrefs.getString(Constants.PREF_KEY_API_BASE_URL, Constants.DEFAULT_API_BASE_URL) ?: Constants.DEFAULT_API_BASE_URL)
    val apiBaseUrl = _apiBaseUrl.asStateFlow()

    private val _customPrompt = MutableStateFlow(sharedPrefs.getString(Constants.PREF_KEY_CUSTOM_PROMPT, null))
    val customPrompt = _customPrompt.asStateFlow()

    private val _silenceTimeout = MutableStateFlow(sharedPrefs.getInt(Constants.PREF_KEY_SILENCE_TIMEOUT, Constants.DEFAULT_SILENCE_TIMEOUT_SEC))
    val silenceTimeout = _silenceTimeout.asStateFlow()

    private val processingJobs = ConcurrentHashMap<Long, Job>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.resetStuckMessages()
        }
    }

    val activeTasks = dao.getActiveTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val draftTasks = dao.getDraftTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    fun saveApiConfig(key: String, url: String) {
        sharedPrefs.edit()
            .putString(Constants.PREF_KEY_API_KEY, key)
            .putString(Constants.PREF_KEY_API_BASE_URL, url)
            .apply()
        _apiKey.value = key
        _apiBaseUrl.value = url
    }

    fun saveCustomPrompt(prompt: String?) {
        sharedPrefs.edit()
            .putString(Constants.PREF_KEY_CUSTOM_PROMPT, prompt)
            .apply()
        _customPrompt.value = prompt
    }

    fun saveSilenceTimeout(seconds: Int) {
        sharedPrefs.edit()
            .putInt(Constants.PREF_KEY_SILENCE_TIMEOUT, seconds)
            .apply()
        _silenceTimeout.value = seconds
    }

    fun processNewInput(content: String, sourceApp: String, existingRawId: Long? = null) {
        // We need a stable ID to track the job. If existingRawId is null, we'll get it from processContent.
        // But we want to track it immediately. Let's pre-insert if needed.
        viewModelScope.launch(Dispatchers.IO) {
            val rawMsgId = existingRawId ?: dao.insertRawMessage(
                RawMessage(
                    content = content,
                    sourceApp = sourceApp,
                    timestamp = System.currentTimeMillis()
                )
            )

            val job = launch(Dispatchers.IO) {
                try {
                    TaskProcessor.processContent(
                        content = content,
                        sourceApp = sourceApp,
                        dao = dao,
                        existingRawId = rawMsgId,
                        apiKey = _apiKey.value,
                        baseUrl = _apiBaseUrl.value,
                        customPrompt = _customPrompt.value,
                        silenceTimeoutSec = _silenceTimeout.value,
                        notificationKey = null,
                        scope = viewModelScope
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    processingJobs.remove(rawMsgId)
                }
            }
            processingJobs[rawMsgId] = job
        }
    }

    fun cancelProcessing() {
        processingJobs.values.forEach { it.cancel() }
        processingJobs.clear()
        // Database states will be updated via cancelAllMessages or individually
    }

    fun cancelMessage(msgId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.cancelMessage(msgId)
            processingJobs.remove(msgId)?.cancel()
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
