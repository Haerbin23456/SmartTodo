package com.example.smarttodo

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.smarttodo.data.AppDatabase
import com.example.smarttodo.logic.TaskProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyNotificationService : NotificationListenerService() {

    private var lastContent = ""
    private var lastTime = 0L
    
    // Service lifecycle scope
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing) return

        val packageName = sbn.packageName
        val sharedPrefs = getSharedPreferences("SmartTodoPrefs", MODE_PRIVATE)
        val isAllowed = sharedPrefs.getBoolean(packageName, false)

        if (!isAllowed) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        
        // Prioritize bigText, fallback to text
        val fullContent = if (!bigText.isNullOrEmpty()) bigText else text
        val displayContent = if (title.isNotEmpty()) "[$title] $fullContent" else fullContent

        if (displayContent.isBlank()) return

        val currentTime = System.currentTimeMillis()
        if (displayContent == lastContent && (currentTime - lastTime) < 2000) {
            return
        }

        lastContent = displayContent
        lastTime = currentTime

        val apiKey = sharedPrefs.getString("api_key", "") ?: ""
        val baseUrl = sharedPrefs.getString("api_base_url", "https://api.deepseek.com/chat/completions") ?: "https://api.deepseek.com/chat/completions"

        // Use TaskProcessor to handle DB & AI
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            TaskProcessor.processContent(
                content = displayContent,
                sourceApp = packageName,
                dao = db.todoDao(),
                apiKey = apiKey,
                baseUrl = baseUrl,
                scope = serviceScope
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
    }
}
