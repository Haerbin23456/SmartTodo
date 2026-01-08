package com.example.smarttodo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

data class SubTaskItem(
    val content: String,
    val isDone: Boolean = false
)

@Entity(tableName = "smart_tasks")
data class SmartTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,          // AI Summarized Title
    val summary: String,        // AI Summarized details (History Log)
    val notes: String = "",     // Important details/notes
    val subtasks: List<SubTaskItem> = emptyList(), // Actionable Items
    @ColumnInfo(name = "deadline") val scheduledTime: String? = null, // Renamed in code, kept "deadline" in DB for compatibility
    val status: String,         // PENDING, DONE, TRASH
    val completeness: String,   // COMPLETE, MISSING_INFO
    val createdTime: Long = System.currentTimeMillis(),
    val isDraft: Boolean = true // Default to Draft (Inbox) until confirmed by user
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DONE = "DONE"
        const val STATUS_TRASH = "TRASH"

        const val COMPLETENESS_COMPLETE = "COMPLETE"
        const val COMPLETENESS_MISSING_INFO = "MISSING_INFO"
    }
}
