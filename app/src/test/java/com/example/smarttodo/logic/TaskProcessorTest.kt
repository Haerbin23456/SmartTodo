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
}
