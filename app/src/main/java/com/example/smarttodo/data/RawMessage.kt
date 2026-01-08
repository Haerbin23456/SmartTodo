package com.example.smarttodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_messages")
data class RawMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val sourceApp: String, // e.g. "com.tencent.mm"
    val timestamp: Long,
    val isProcessed: Boolean = false,
    val status: String = STATUS_PENDING, // PENDING, PROCESSING, SUCCESS, FAILED
    val relatedTaskId: Long? = null,
    val aiLog: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
    }
}
