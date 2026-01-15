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

import com.example.smarttodo.util.Constants
import com.example.smarttodo.util.NotificationActionManager

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
        val sharedPrefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val isAllowed = sharedPrefs.getBoolean(packageName, false)

        if (!isAllowed) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        
        // Prioritize bigText, fallback to text
        val fullContent = if (!bigText.isNullOrEmpty()) bigText else text
        
        // 过滤掉通知内容完全为空的情况
        if (fullContent.isNullOrBlank()) return
        
        val displayContent = if (title.isNotEmpty()) "[$title] $fullContent" else fullContent

        val currentTime = System.currentTimeMillis()
        // 增强去重：5秒内完全相同的内容，或者内容包含明显的“新消息”提示语（通常是更新通知）
        if (displayContent == lastContent && (currentTime - lastTime) < 5000) {
            return
        }
        
        if (displayContent.all { it.isDigit() }) return

        lastContent = displayContent
        lastTime = currentTime

        val apiKey = sharedPrefs.getString(Constants.PREF_KEY_API_KEY, "")?.trim() ?: ""
        val baseUrl = sharedPrefs.getString(Constants.PREF_KEY_API_BASE_URL, Constants.DEFAULT_API_BASE_URL) ?: Constants.DEFAULT_API_BASE_URL

        // Store the content intent for later use
        val notificationKey = sbn.key
        sbn.notification.contentIntent?.let {
            NotificationActionManager.storeAction(notificationKey, it)
        }

        // Use TaskProcessor to handle DB & AI
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            TaskProcessor.processContent(
                content = displayContent,
                sourceApp = packageName,
                dao = db.todoDao(),
                apiKey = apiKey,
                baseUrl = baseUrl,
                notificationKey = notificationKey,
                scope = serviceScope
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
    }
}
