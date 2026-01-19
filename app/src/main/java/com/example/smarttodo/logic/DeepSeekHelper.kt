package com.example.smarttodo.logic

import android.util.Log
import com.example.smarttodo.data.SmartTask
import com.example.smarttodo.util.Constants
import com.example.smarttodo.util.TimeUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class AIAnalysisResult(
    val action: String, // CREATE, MERGE, IGNORE
    val targetTaskId: Long? = null,
    val taskData: SmartTaskData? = null,
    val rawLog: String? = null
)

data class SmartTaskData(
    val title: String,
    val summary: String, // Dynamic log/update
    val notes: String?,  // Persistent details
    val scheduledTime: String?,
    val subtasks: List<String> = emptyList(),
    val completeness: String
)

object DeepSeekHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    suspend fun analyzeContent(
        newContent: String,
        existingTasks: List<SmartTask>,
        apiKey: String,
        baseUrl: String = Constants.DEFAULT_API_BASE_URL,
        customPrompt: String? = null,
        silenceTimeoutSec: Int = Constants.DEFAULT_SILENCE_TIMEOUT_SEC,
        language: String = "Chinese",
        onProgress: (String) -> Unit = {}
    ): AIAnalysisResult = kotlinx.coroutines.withTimeout(120000) { // 2 minutes max global timeout
        if (apiKey.isBlank()) {
            return@withTimeout AIAnalysisResult(
                action = Constants.ACTION_IGNORE,
                rawLog = "Error: API Key is missing. Please configure it in Settings."
            )
        }

        val json = JSONObject()
        json.put("model", Constants.DEFAULT_AI_MODEL)
        json.put("stream", true)
        json.put("response_format", JSONObject().put("type", "json_object"))

        val currentDateTime = TimeUtils.getCurrentTimeForPrompt()

        val systemPrompt = PromptProvider.getSystemPrompt(existingTasks, language, currentDateTime, customPrompt)

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        messages.put(JSONObject().put("role", "user").put("content", "New Message: $newContent"))

        json.put("messages", messages)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        var fullContent = ""
        var lastUpdateTime = System.currentTimeMillis()
        val silenceTimeoutMs = silenceTimeoutSec * 1000L

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                val userFriendlyError = when (response.code) {
                    401 -> "API Key 无效或已过期"
                    402 -> "账户余额不足"
                    429 -> "请求过于频繁，请稍后再试"
                    500, 503 -> "AI 服务端繁忙，请稍后重试"
                    else -> "API 错误: ${response.code}"
                }
                return@withTimeout AIAnalysisResult(
                    action = Constants.ACTION_IGNORE,
                    rawLog = "$userFriendlyError\n详细信息: $errorBody"
                )
            }

            val reader = response.body.source().inputStream().bufferedReader()
            reader.forEachLine { line ->
                coroutineContext.ensureActive() // Check for cancellation
                
                // Check for silence timeout
                if (System.currentTimeMillis() - lastUpdateTime > silenceTimeoutMs) {
                    throw IOException("AI 响应超时 ($silenceTimeoutSec 秒无输出)")
                }

                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") return@forEachLine
                    
                    try {
                        val chunk = JSONObject(data)
                        val choices = chunk.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val delta = choices.getJSONObject(0).getJSONObject("delta")
                            if (delta.has("content")) {
                                val content = delta.getString("content")
                                fullContent += content
                                lastUpdateTime = System.currentTimeMillis() // Reset timer on new content
                                onProgress(fullContent)
                            }
                        }
                    } catch (e: Exception) { }
                }
            }
            
            if (fullContent.isBlank()) throw IOException("AI 返回内容为空")
             
             // Pass the actual fullContent as rawLog so it's preserved in DB
             parseAnalysisResult(fullContent, fullContent)
 
         } catch (e: java.net.UnknownHostException) {
             AIAnalysisResult(
                 action = Constants.ACTION_IGNORE,
                 rawLog = "网络不可用，请检查联网状态"
             )
         } catch (e: java.net.SocketTimeoutException) {
             AIAnalysisResult(
                 action = Constants.ACTION_IGNORE,
                 rawLog = "连接超时，请检查网络环境"
             )
         } catch (e: Exception) {
             Log.e("DeepSeek", "Streaming error", e)
             AIAnalysisResult(
                 action = Constants.ACTION_IGNORE,
                 rawLog = "处理出错: ${e.message}\n已获取内容: $fullContent"
             )
         }
    }

    private fun parseAnalysisResult(content: String, rawLog: String): AIAnalysisResult {
        return try {
            val resultJson = JSONObject(content)
            val action = resultJson.optString("action", Constants.ACTION_IGNORE)
            val targetTaskId = if (resultJson.has("targetTaskId") && !resultJson.isNull("targetTaskId")) resultJson.getLong("targetTaskId") else null
            
            val taskDataJson = if (resultJson.has("taskData") && !resultJson.isNull("taskData")) resultJson.getJSONObject("taskData") else null
            val taskData = if (taskDataJson != null) {
                val subtasksJson = taskDataJson.optJSONArray("subtasks")
                val subtasksList = mutableListOf<String>()
                if (subtasksJson != null) {
                    for (i in 0 until subtasksJson.length()) {
                        subtasksList.add(subtasksJson.getString(i))
                    }
                }

                 SmartTaskData(
                    title = taskDataJson.optString("title", "Untitled"),
                    summary = taskDataJson.optString("summary", ""),
                    notes = if (taskDataJson.has("notes") && !taskDataJson.isNull("notes")) taskDataJson.getString("notes") else null,
                    scheduledTime = if (taskDataJson.has("scheduledTime") && !taskDataJson.isNull("scheduledTime")) taskDataJson.getString("scheduledTime") else null,
                    subtasks = subtasksList,
                    completeness = taskDataJson.optString("completeness", SmartTask.COMPLETENESS_MISSING_INFO)
                )
            } else null

            AIAnalysisResult(action, targetTaskId, taskData, rawLog = rawLog)
        } catch (e: Exception) {
            Log.e("DeepSeek", "JSON Parse Error: ${e.message}")
            AIAnalysisResult(
                action = Constants.ACTION_IGNORE,
                rawLog = "JSON Parse Error: ${e.message}\nRaw Content: $content"
            )
        }
    }
}
