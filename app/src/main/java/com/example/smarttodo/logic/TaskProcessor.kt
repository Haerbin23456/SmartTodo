package com.example.smarttodo.logic

import com.example.smarttodo.data.RawMessage
import com.example.smarttodo.data.SmartTask
import com.example.smarttodo.data.SubTaskItem
import com.example.smarttodo.data.TodoDao
import com.example.smarttodo.util.Constants
import com.example.smarttodo.util.TimeUtils
import kotlinx.coroutines.flow.firstOrNull

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object TaskProcessor {
    private val globalMutex = Mutex()

    /**
     * 处理新消息内容的核心逻辑。
     * 
     * 待优化：上下文链式识别 (Context Chaining)
     * 目前系统通过传入最近的 [activeTasks] 和 [draftTasks] 作为 AI 上下文，
     * 已经具备基本的“合并到已有任务”的能力。
     * 
     * 未来改进方向：
     * 1. 短时记忆：记录最近 5-10 分钟内的原始消息序列，即使没有创建任务的消息也作为上下文。
     * 2. 意图补全：当收到类似“下午9点”这种碎片化消息时，AI 应优先检索最近处理的任务进行补充，
     *    而不是直接忽略或创建新任务。
     */
    suspend fun processContent(
        content: String,
        sourceApp: String,
        dao: TodoDao,
        existingRawId: Long? = null,
        apiKey: String,
        baseUrl: String = Constants.DEFAULT_API_BASE_URL,
        customPrompt: String? = null,
        silenceTimeoutSec: Int = Constants.DEFAULT_SILENCE_TIMEOUT_SEC,
        scope: CoroutineScope
    ) {
        val locale = java.util.Locale.getDefault()
        val language = if (locale.language == "zh") "Chinese (Simplified)" else locale.displayLanguage

        val rawMsgId = existingRawId
            ?: dao.insertRawMessage(
                RawMessage(
                    content = content,
                    sourceApp = sourceApp,
                    timestamp = TimeUtils.now()
                )
            )

        // 立即标记为处理中，以便 UI 显示转圈
        dao.updateRawMessageStatus(rawMsgId, RawMessage.STATUS_PROCESSING)

        globalMutex.withLock {
            // 再次检查状态，防止在等待锁的过程中被手动取消了
            val currentMsg = dao.getRawMessageById(rawMsgId)
            if (currentMsg?.status == RawMessage.STATUS_CANCELLED) return@withLock

            try {
                val activeTasks = dao.getActiveTasks().firstOrNull()?.take(10) ?: emptyList()
                val draftTasks = dao.getDraftTasks().firstOrNull()?.take(10) ?: emptyList()
                val allContextTasks = activeTasks + draftTasks

                val result = DeepSeekHelper.analyzeContent(
                    newContent = content,
                    existingTasks = allContextTasks,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    customPrompt = customPrompt,
                    silenceTimeoutSec = silenceTimeoutSec,
                    language = language,
                    onProgress = { currentLog ->
                        scope.launch(Dispatchers.IO) {
                            dao.updateRawMessageLog(rawMsgId, currentLog)
                        }
                    }
                )

                handleAnalysisResult(result, rawMsgId, sourceApp, dao)
                
                if (result.rawLog != null) {
                     dao.updateRawMessageLog(rawMsgId, result.rawLog)
                }
                dao.updateRawMessageStatus(rawMsgId, RawMessage.STATUS_SUCCESS)
                
            } catch (e: Exception) {
                handleError(e, rawMsgId, dao)
            }
        }
    }

    private suspend fun handleAnalysisResult(
        result: AIAnalysisResult,
        rawMsgId: Long,
        sourceApp: String,
        dao: TodoDao
    ) = withContext(Dispatchers.IO) {
        when (result.action) {
            Constants.ACTION_CREATE -> handleCreate(result, rawMsgId, sourceApp, dao)
            Constants.ACTION_MERGE -> handleMerge(result, rawMsgId, dao)
            Constants.ACTION_IGNORE -> dao.markRawMessageProcessed(rawMsgId, null)
        }
    }

    private suspend fun handleCreate(result: AIAnalysisResult, rawMsgId: Long, sourceApp: String, dao: TodoDao) {
        result.taskData?.let { data ->
            // 安全校验：如果内容包含明显的报错信息，拦截创建请求
            val isGarbage = data.title.contains("Error", ignoreCase = true) || 
                           data.summary.contains("Streaming Error", ignoreCase = true) ||
                           data.summary.contains("connection abort", ignoreCase = true)
            
            if (isGarbage) {
                dao.updateRawMessageLog(rawMsgId, "Intercepted garbage content: ${data.summary}")
                dao.markRawMessageProcessed(rawMsgId, null)
                return@let
            }

            val timeStamp = TimeUtils.formatToLog()
            dao.insertTaskAndMarkProcessed(
                SmartTask(
                    title = data.title,
                    summary = "[$timeStamp] Created from: $sourceApp\n${data.summary}",
                    notes = data.notes ?: "",
                    scheduledTime = data.scheduledTime,
                    subtasks = data.subtasks.map { SubTaskItem(content = it) },
                    status = SmartTask.STATUS_PENDING,
                    completeness = data.completeness,
                    isDraft = true
                ),
                rawMsgId
            )
        }
    }

    private suspend fun handleMerge(result: AIAnalysisResult, rawMsgId: Long, dao: TodoDao) {
        val targetId = result.targetTaskId ?: return
        val data = result.taskData ?: return
        val existingTask = dao.getTaskById(targetId) ?: return
        
        val timeStamp = TimeUtils.formatToLog()
        
        // 1. Smart Summary (History) Append
        val newLogEntry = if (data.summary.isNotBlank()) "\n[$timeStamp] ${data.summary}" else ""
        val updatedSummary = if (!existingTask.summary.contains(data.summary)) {
            existingTask.summary + newLogEntry
        } else {
            existingTask.summary
        }
        
        // 2. Notes Overwrite (Source of Truth)
        val updatedNotes = data.notes ?: existingTask.notes

        // 3. Subtasks Append - Avoid Duplicate Subtasks
        val existingSubtaskContents = existingTask.subtasks.map { it.content }
        val newSubItems = data.subtasks
            .filter { it !in existingSubtaskContents }
            .map { SubTaskItem(content = it) }
        val updatedSubtasks = existingTask.subtasks + newSubItems

        dao.updateTaskAndMarkProcessed(
            existingTask.copy(
                title = data.title, 
                summary = updatedSummary,
                notes = updatedNotes,
                scheduledTime = data.scheduledTime ?: existingTask.scheduledTime,
                subtasks = updatedSubtasks,
                completeness = data.completeness
            ),
            rawMsgId
        )
    }

    private suspend fun handleError(e: Exception, rawMsgId: Long, dao: TodoDao) {
        val errorLog = if (e is kotlinx.coroutines.TimeoutCancellationException) {
            "AI Analysis Timeout (30s)"
        } else {
            e.message ?: "Unknown error"
        }
        dao.updateRawMessageLog(rawMsgId, "Error: $errorLog")
        dao.updateRawMessageStatus(rawMsgId, RawMessage.STATUS_FAILED)
    }
}
