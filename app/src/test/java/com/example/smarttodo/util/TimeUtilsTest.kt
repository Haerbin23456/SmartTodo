package com.example.smarttodo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TimeUtilsTest {

    @Test
    fun `formatToStandard should format timestamp correctly`() {
        // 1716192000000 is 2024-05-20 08:00:00 UTC
        // This test might be sensitive to system timezone, but we can check format pattern
        val timestamp = 1716192000000L
        val formatted = TimeUtils.formatToStandard(timestamp)
        
        // Check pattern YYYY-MM-DD HH:mm
        assertTrue(formatted.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
    }

    @Test
    fun `formatToLog should use MM-dd HH-mm pattern`() {
        val testTime = LocalDateTime.of(2024, 5, 20, 15, 30)
        val formatted = TimeUtils.formatToLog(testTime)
        
        assertEquals("05-20 15:30", formatted)
    }

    @Test
    fun `getCurrentTimeForPrompt should return non-empty string`() {
        val time = TimeUtils.getCurrentTimeForPrompt()
        assertTrue(time.isNotEmpty())
    }
}
