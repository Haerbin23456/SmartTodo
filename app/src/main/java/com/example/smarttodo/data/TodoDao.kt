package com.example.smarttodo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Transaction
     suspend fun insertTaskAndMarkProcessed(task: SmartTask, messageId: Long) {
         val taskId = insertSmartTask(task)
         markRawMessageProcessed(messageId, taskId)
     }
 
     @Transaction
     suspend fun updateTaskAndMarkProcessed(task: SmartTask, messageId: Long) {
         updateSmartTask(task)
         markRawMessageProcessed(messageId, task.id)
     }

    // --- RawMessage Operations ---
    @Insert
    suspend fun insertRawMessage(message: RawMessage): Long

    @Query("SELECT * FROM raw_messages WHERE isProcessed = 0 ORDER BY timestamp DESC")
    fun getUnprocessedMessages(): Flow<List<RawMessage>>

    @Query("SELECT * FROM raw_messages ORDER BY timestamp DESC LIMIT 100")
    fun getAllRawMessages(): Flow<List<RawMessage>>

    @Query("SELECT * FROM raw_messages WHERE relatedTaskId = :taskId")
    suspend fun getMessagesForTask(taskId: Long): List<RawMessage>

    @Query("SELECT * FROM raw_messages WHERE id = :id")
    suspend fun getRawMessageById(id: Long): RawMessage?

    @Query("UPDATE raw_messages SET isProcessed = 1, relatedTaskId = :taskId WHERE id = :msgId")
    suspend fun markRawMessageProcessed(msgId: Long, taskId: Long?)

    @Query("UPDATE raw_messages SET aiLog = :log WHERE id = :msgId")
    suspend fun updateRawMessageLog(msgId: Long, log: String)

    @Query("UPDATE raw_messages SET status = :status WHERE id = :msgId")
    suspend fun updateRawMessageStatus(msgId: Long, status: String)

    @Query("SELECT * FROM smart_tasks")
    suspend fun getAllTasksSync(): List<SmartTask>

    @Query("SELECT * FROM smart_tasks WHERE id = :id")
    suspend fun getTaskByIdSync(id: Long): SmartTask?

    @Transaction
    fun runInTransaction(block: () -> Unit) {
        block()
    }

    // --- SmartTask Operations ---
    @Insert
    suspend fun insertSmartTask(task: SmartTask): Long

    @Update
    suspend fun updateSmartTask(task: SmartTask)

    @Delete
    suspend fun deleteSmartTask(task: SmartTask)

    @Query("SELECT * FROM smart_tasks WHERE status != 'TRASH' AND isDraft = 0 ORDER BY createdTime DESC")
    fun getActiveTasks(): Flow<List<SmartTask>>

    @Query("SELECT * FROM smart_tasks WHERE status != 'TRASH' AND isDraft = 1 ORDER BY createdTime DESC")
    fun getDraftTasks(): Flow<List<SmartTask>>

    @Query("SELECT * FROM smart_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): SmartTask?
    
    // Helper to fetch task with messages (simplified, manually handling relation is often easier than @Relation for logic)
}
