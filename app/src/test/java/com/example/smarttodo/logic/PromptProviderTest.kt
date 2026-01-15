package com.example.smarttodo.logic

import com.example.smarttodo.data.SmartTask
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptProviderTest {

    @Test
    fun `getSystemPrompt should contain language and current time`() {
        val language = "Chinese"
        val currentTime = "2024-05-20 10:00"
        val tasks = emptyList<SmartTask>()

        val prompt = PromptProvider.getSystemPrompt(tasks, language, currentTime)

        assertTrue(prompt.contains(language))
        assertTrue(prompt.contains(currentTime))
        assertTrue(prompt.contains("Smart Personal Secretary"))
    }

    @Test
    fun `getSystemPrompt should include existing tasks in context`() {
        val tasks = listOf(
            SmartTask(
                id = 1, 
                title = "Task 1", 
                summary = "Summary 1", 
                notes = "Notes 1",
                status = SmartTask.STATUS_PENDING,
                completeness = SmartTask.COMPLETENESS_COMPLETE
            )
        )
        
        val prompt = PromptProvider.getSystemPrompt(tasks, "English", "Now")

        assertTrue(prompt.contains("[ID:1] Task 1"))
        assertTrue(prompt.contains("Notes: \"Notes 1\""))
    }
}
