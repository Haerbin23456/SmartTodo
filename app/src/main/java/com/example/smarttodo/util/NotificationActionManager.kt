package com.example.smarttodo.util

import android.app.PendingIntent
import android.util.Log

object NotificationActionManager {
    private const val TAG = "NotificationActionManager"
    private val intentMap = mutableMapOf<String, PendingIntent>()
    
    // We only keep a limited number of intents to avoid memory issues
    private const val MAX_INTENTS = 50
    private val keyOrder = mutableListOf<String>()

    fun storeAction(key: String, pendingIntent: PendingIntent) {
        synchronized(this) {
            if (intentMap.containsKey(key)) {
                keyOrder.remove(key)
            } else if (keyOrder.size >= MAX_INTENTS) {
                val oldestKey = keyOrder.removeAt(0)
                intentMap.remove(oldestKey)
            }
            intentMap[key] = pendingIntent
            keyOrder.add(key)
            Log.d(TAG, "Stored action for key: $key. Total stored: ${intentMap.size}")
        }
    }

    fun fireAction(key: String): Boolean {
        synchronized(this) {
            val pendingIntent = intentMap[key]
            return if (pendingIntent != null) {
                try {
                    pendingIntent.send()
                    Log.d(TAG, "Successfully fired action for key: $key")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fire action for key: $key", e)
                    false
                }
            } else {
                Log.w(TAG, "No action found for key: $key")
                false
            }
        }
    }
}
