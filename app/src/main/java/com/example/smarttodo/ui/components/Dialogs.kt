package com.example.smarttodo.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import com.example.smarttodo.data.RawMessage
import com.example.smarttodo.util.NotificationActionManager
import org.json.JSONObject

@Composable
fun LogDetailDialog(
    msg: RawMessage,
    context: Context,
    onReprocess: (RawMessage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 处理日志") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("原始内容:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        msg.content,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text("处理日志:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    val (displayText, isError) = remember(msg.aiLog) {
                        try {
                            if (msg.aiLog != null) {
                                val log = msg.aiLog.trim()
                                if (log.startsWith("{")) {
                                    try {
                                        val json = JSONObject(log)
                                        if (json.has("choices")) {
                                            val content = json.getJSONArray("choices")
                                                .getJSONObject(0)
                                                .getJSONObject("message")
                                                .getString("content")
                                            try {
                                                val taskObj = JSONObject(content)
                                                val taskData = taskObj.optJSONObject("taskData")
                                                if (taskData != null && taskData.has("scheduledTime")) {
                                                    val time = taskData.getString("scheduledTime")
                                                    "提取时间: $time" to false
                                                } else {
                                                    JSONObject(content).toString(2) to false
                                                }
                                            } catch (e: Exception) {
                                                content to false
                                            }
                                        } else {
                                            json.toString(2) to false
                                        }
                                    } catch (e: Exception) {
                                        log to false
                                    }
                                } else {
                                    log to (msg.status == RawMessage.STATUS_FAILED)
                                }
                            } else "等待 AI 响应..." to false
                        } catch (e: Exception) { (msg.aiLog ?: "解析错误") to true }
                    }
                    
                    Text(
                        displayText, 
                        style = MaterialTheme.typography.bodySmall, 
                        fontFamily = FontFamily.Monospace,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onReprocess(msg)
                onDismiss()
            }) {
                Text("重新解析")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val fired = msg.notificationKey?.let { 
                        NotificationActionManager.fireAction(it)
                    } ?: false
                    
                    if (!fired) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(msg.sourceApp)
                        launchIntent?.let { context.startActivity(it) }
                    }
                }) {
                    Text("打开应用")
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

@Composable
fun ManualInputDialog(
    onDismiss: () -> Unit, 
    onConfirm: (String) -> Unit,
    context: Context
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加待办") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("粘贴文本或输入...") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        if (clipboard.hasPrimaryClip()) {
                            val clipData = clipboard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                text = clipData.getItemAt(0).text.toString()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("粘贴剪贴板")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text("生成待办")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
