package com.example.smarttodo.logic

import com.example.smarttodo.data.SmartTask
import com.example.smarttodo.data.TodoDao
import com.example.smarttodo.util.Constants
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TaskProcessorTest {
    private val dao = mockk<TodoDao>(relaxed = true)
    
    @Before
    fun setup() {
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
}
