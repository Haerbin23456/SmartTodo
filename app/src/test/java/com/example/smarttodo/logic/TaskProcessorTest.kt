package com.example.smarttodo.logic

import com.example.smarttodo.data.SmartTask
import com.example.smarttodo.data.TodoDao
import com.example.smarttodo.util.Constants
import io.mockk.*
import android.util.Log
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TaskProcessorTest {
    private val dao = mockk<TodoDao>(relaxed = true)
    
    @Before
    fun setup() {
        clearAllMocks()
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.isLoggable(any<String>(), any<Int>()) } returns false

        mockkObject(DeepSeekHelper)
        every { dao.getActiveTasks() } returns flowOf(emptyList())
        every { dao.getDraftTasks() } returns flowOf(emptyList())
    }

    @Test
    fun `processContent should call insertTaskAndMarkProcessed when action is CREATE`() = runTest {
        // Arrange
        val content = "New Task"
        val apiKey = "test-key"
        val aiResult = AIAnalysisResult(
            action = Constants.ACTION_CREATE,
            taskData = SmartTaskData(
                title = "AI Title",
                summary = "AI Summary",
                notes = "AI Notes",
                scheduledTime = null,
                subtasks = emptyList(),
                completeness = SmartTask.COMPLETENESS_COMPLETE
            )
        )
        
        coEvery { DeepSeekHelper.analyzeContent(any(), any(), any(), any(), any(), any()) } returns aiResult
        coEvery { dao.insertRawMessage(any()) } returns 1L

        // Act
        TaskProcessor.processContent(content, "TestApp", dao, apiKey = apiKey, scope = this)

        // Assert
        coVerify { dao.insertTaskAndMarkProcessed(any(), 1L) }
    }

    @Test
    fun `processContent should call updateTaskAndMarkProcessed when action is MERGE`() = runTest {
        // Arrange
        val content = "Update Task"
        val apiKey = "test-key"
        val existingTask = SmartTask(id = 123L, title = "Old", summary = "Old Summary", status = SmartTask.STATUS_PENDING, completeness = SmartTask.COMPLETENESS_COMPLETE)
        
        val aiResult = AIAnalysisResult(
            action = Constants.ACTION_MERGE,
            targetTaskId = 123L,
            taskData = SmartTaskData(
                title = "Old",
                summary = "New Info",
                notes = "Updated Notes",
                scheduledTime = null,
                subtasks = emptyList(),
                completeness = SmartTask.COMPLETENESS_COMPLETE
            )
        )
        
        coEvery { DeepSeekHelper.analyzeContent(any(), any(), any(), any(), any(), any()) } returns aiResult
        coEvery { dao.insertRawMessage(any()) } returns 1L
        coEvery { dao.getTaskById(123L) } returns existingTask

        // Act
        TaskProcessor.processContent(content, "TestApp", dao, apiKey = apiKey, scope = this)

        // Assert
        coVerify { dao.updateTaskAndMarkProcessed(match { it.id == 123L && it.summary.contains("New Info") }, 1L) }
    }

    @Test
    fun `processContent should IGNORE when AI returns ACTION_IGNORE`() = runTest {
        val content = "Ok thanks"
        val aiResult = AIAnalysisResult(action = Constants.ACTION_IGNORE)
        
        coEvery { DeepSeekHelper.analyzeContent(any(), any(), any(), any(), any(), any()) } returns aiResult
        coEvery { dao.insertRawMessage(any()) } returns 1L

        TaskProcessor.processContent(content, "TestApp", dao, apiKey = "key", scope = this)

        coVerify { dao.markRawMessageProcessed(1L, null) }
        coVerify(exactly = 0) { dao.insertTaskAndMarkProcessed(any(), any()) }
    }

    @Test
    fun `processContent should intercept and ignore garbage error content`() = runTest {
        val content = "Some content"
        val aiResult = AIAnalysisResult(
            action = Constants.ACTION_CREATE,
            taskData = SmartTaskData(
                title = "Error Log",
                summary = "Streaming Error: connection abort",
                notes = null,
                scheduledTime = null,
                subtasks = emptyList(),
                completeness = SmartTask.COMPLETENESS_MISSING_INFO
            )
        )
        
        coEvery { DeepSeekHelper.analyzeContent(any(), any(), any(), any(), any(), any()) } returns aiResult
        coEvery { dao.insertRawMessage(any()) } returns 1L

        TaskProcessor.processContent(content, "TestApp", dao, apiKey = "key", scope = this)

        // Should NOT create task, but mark processed with null
        coVerify { dao.markRawMessageProcessed(1L, null) }
        coVerify(exactly = 0) { dao.insertTaskAndMarkProcessed(any(), any()) }
    }

    @Test
    fun `processContent should handle AI timeout or exception gracefully`() = runTest {
        val content = "Normal content"
        coEvery { DeepSeekHelper.analyzeContent(any(), any(), any(), any(), any(), any()) } throws Exception("Network Timeout")
        coEvery { dao.insertRawMessage(any()) } returns 1L

        TaskProcessor.processContent(content, "TestApp", dao, apiKey = "key", scope = this)

        // Verify status updated to FAILED
        coVerify { dao.updateRawMessageStatus(1L, "FAILED") }
    }

    @Test
    fun `processContent should handle malformed JSON from AI gracefully`() = runTest {
        val content = "Normal content"
        // 模拟 AI 输出了一段不完整的、不合法的 JSON
        val malformedJson = "{\"action\": \"CREATE\", \"taskData\": { \"title\": \"Incomplete..." 
        
        // 我们直接模拟 DeepSeekHelper 返回一个因解析失败而生成的 IGNORE 结果
        val aiResult = AIAnalysisResult(
            action = Constants.ACTION_IGNORE,
            rawLog = "JSON Parse Error: Unexpected end of input"
        )
        
        coEvery { DeepSeekHelper.analyzeContent(any(), any(), any(), any(), any(), any()) } returns aiResult
        coEvery { dao.insertRawMessage(any()) } returns 1L

        TaskProcessor.processContent(content, "TestApp", dao, apiKey = "key", scope = this)

        // 验证：虽然 AI 乱码了，但系统只是记录了日志，没有创建任务
        coVerify { dao.markRawMessageProcessed(1L, any()) }
        coVerify(exactly = 0) { dao.insertTaskAndMarkProcessed(any(), any()) }
    }

    @Test
    fun `processContent should handle structural mismatch from AI gracefully`() = runTest {
        val content = "Normal content"
        // 模拟 AI 输出的 JSON 结构完全错误：taskData 应该是一个对象，AI 却给了一个字符串
        val structuralMismatchJson = "{\"action\": \"CREATE\", \"taskData\": \"This should have been an object\"}"
        
        // 这种情况下，parseAnalysisResult 内部的 getJSONObject("taskData") 会抛出异常
        // 最终返回 ACTION_IGNORE
        val aiResult = AIAnalysisResult(
            action = Constants.ACTION_IGNORE,
            rawLog = "JSON Parse Error: Value This should have been an object at taskData of type java.lang.String cannot be converted to JSONObject"
        )
        
        coEvery { DeepSeekHelper.analyzeContent(any(), any(), any(), any(), any(), any()) } returns aiResult
        coEvery { dao.insertRawMessage(any()) } returns 1L

        TaskProcessor.processContent(content, "TestApp", dao, apiKey = "key", scope = this)

        // 验证：结构不对的消息被安全忽略
        coVerify { dao.markRawMessageProcessed(1L, any()) }
        coVerify(exactly = 0) { dao.insertTaskAndMarkProcessed(any(), any()) }
    }

    @Test
    fun `processContent should handle vague time by putting it into notes prominently`() = runTest {
        val content = "Java EE验收，下午去，时间不限"
        
        // 模拟 AI 识别出时间模糊，将其放入 notes 的顶部
        val aiResult = AIAnalysisResult(
            action = Constants.ACTION_CREATE,
            taskData = SmartTaskData(
                title = "Java EE 验收",
                summary = "新增验收任务（时间待定）",
                notes = "**🕒 待定时间:** 下午 (时间不限)\n\n- 准备好演示文档\n- 检查代码运行环境",
                scheduledTime = null, // 因为不规范，设为 null
                subtasks = emptyList(),
                completeness = SmartTask.COMPLETENESS_MISSING_INFO
            )
        )
        
        coEvery { DeepSeekHelper.analyzeContent(any(), any(), any(), any(), any(), any()) } returns aiResult
        coEvery { dao.insertRawMessage(any()) } returns 1L

        TaskProcessor.processContent(content, "TestApp", dao, apiKey = "key", scope = this)

        // 验证：任务被创建，且 notes 中包含了我们要求的醒目标注
        coVerify { dao.insertTaskAndMarkProcessed(match { 
            it.title == "Java EE 验收" && 
            it.notes.contains("🕒 待定时间:") && 
            it.scheduledTime == null 
        }, 1L) }
    }

    @Test
    fun `processContent should ensure notes are clean and concise based on prompt instructions`() = runTest {
        val content = "补充：验收地点在实验楼"
        
        // 模拟现有任务
        val existingTask = SmartTask(
            id = 100,
            title = "Java EE 验收",
            summary = "初始任务",
            notes = "**🕒 待定时间:** 下午\n- 准备文档",
            status = SmartTask.STATUS_PENDING,
            completeness = SmartTask.COMPLETENESS_MISSING_INFO
        )
        
        // 模拟 AI 返回的合并结果，它应该按照 Prompt 要求整理好 notes
        val aiResult = AIAnalysisResult(
            action = Constants.ACTION_MERGE,
            targetTaskId = 100,
            taskData = SmartTaskData(
                title = "Java EE 验收",
                summary = "更新了地点",
                notes = "**🕒 待定时间:** 下午\n**📍 地点:** 实验楼\n- 准备文档", // AI 整理后的版本
                scheduledTime = null,
                subtasks = emptyList(),
                completeness = SmartTask.COMPLETENESS_COMPLETE
            )
        )
        
        coEvery { dao.getActiveTasks() } returns kotlinx.coroutines.flow.flowOf(listOf(existingTask))
        coEvery { dao.getDraftTasks() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { dao.getTaskById(100) } returns existingTask
        coEvery { DeepSeekHelper.analyzeContent(any(), any(), any(), any(), any(), any()) } returns aiResult
        coEvery { dao.insertRawMessage(any()) } returns 1L

        TaskProcessor.processContent(content, "TestApp", dao, apiKey = "key", scope = this)

        // 验证：notes 被完全替换为 AI 整理后的整洁版本
        coVerify { dao.updateTaskAndMarkProcessed(match { 
            it.id == 100L && 
            it.notes.contains("📍 地点") && 
            it.notes.contains("实验楼") &&
            it.notes.contains("🕒 待定时间:")
        }, 1L) }
    }
}
