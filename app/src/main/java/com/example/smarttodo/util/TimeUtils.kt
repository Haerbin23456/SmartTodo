package com.example.smarttodo.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val standardFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val logFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())
    private val promptFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEEE)", Locale.getDefault())

    fun formatToStandard(timestamp: Long): String {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            .format(standardFormatter)
    }

    fun formatToLog(dateTime: LocalDateTime = LocalDateTime.now()): String {
        return dateTime.format(logFormatter)
    }

    fun getCurrentTimeForPrompt(): String {
        return LocalDateTime.now().format(promptFormatter)
    }

    fun now(): Long = System.currentTimeMillis()
}
