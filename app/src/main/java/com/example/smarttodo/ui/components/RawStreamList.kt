package com.example.smarttodo.ui.components

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.smarttodo.ui.components.SmartOutlinedCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smarttodo.data.RawMessage
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RawStreamList(
    messages: List<RawMessage>,
    context: Context,
    onReprocess: (RawMessage) -> Unit,
    onCancel: (Long) -> Unit,
    onCancelAll: () -> Unit,
    modifier: Modifier = Modifier,
    useLazyColumn: Boolean = true
) {
    Column(modifier = modifier) {
        // Header with Global Progress and Cancel All
        val processingMessages = messages.filter { it.status == RawMessage.STATUS_PROCESSING || it.status == RawMessage.STATUS_PENDING }
        val totalCount = processingMessages.size
        
        if (totalCount > 0) {
            // Find which one is actually processing (the one with the earliest timestamp usually)
            // Or just show total count since they are serial
            val finishedInSession = messages.count { it.status == RawMessage.STATUS_SUCCESS || it.status == RawMessage.STATUS_FAILED }
            // Note: Since we don't have a reliable "total batch" size from the past, 
            // let's show (Total - Remaining / Total) or just (Remaining)
            // User wants (x/n). Let's use a simpler logic: 
            // If we have 5 pending, and we just started, it's (1/5).
            // Actually, we can just show "Remaining X tasks" or similar, 
            // but (x/n) usually means "Completed / Total".
            
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "AI 正在分析 ($totalCount 条处理中)...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = onCancelAll,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.StopCircle, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("停止全部", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无信息流", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
        } else if (useLazyColumn) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Box(modifier = Modifier.animateItem()) {
                        RawMessageItem(msg, context, onReprocess, onCancel)
                    }
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                messages.forEach { msg ->
                    RawMessageItem(msg, context, onReprocess, onCancel)
                }
            }
        }
    }
}

@Composable
fun RawMessageItem(
    msg: RawMessage,
    context: Context,
    onReprocess: (RawMessage) -> Unit,
    onCancel: (Long) -> Unit
) {
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val timeStr = dateFormat.format(Date(msg.timestamp))
    var showLogDialog by remember { mutableStateOf(false) }

    // Parse result summary from aiLog if possible
    val resultSummary = remember(msg.aiLog, msg.status) {
        if (msg.status == RawMessage.STATUS_SUCCESS && msg.aiLog != null) {
            try {
                val json = JSONObject(msg.aiLog)
                val action = json.optString("action", "")
                val taskData = json.optJSONObject("taskData")
                val title = taskData?.optString("title", "")
                when (action) {
                    "CREATE" -> "✨ 新建: $title"
                    "MERGE" -> "🔄 合并: $title"
                    "IGNORE" -> "🔇 已忽略"
                    else -> null
                }
            } catch (e: Exception) { null }
        } else null
    }

    SmartOutlinedCard(
        onClick = { showLogDialog = true },
        isError = msg.status == RawMessage.STATUS_FAILED,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: App Info & Status/Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    val appIcon = if (msg.status == RawMessage.STATUS_FAILED) Icons.Default.History else Icons.Default.Notifications
                    Icon(
                        appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = when(msg.status) {
                            RawMessage.STATUS_FAILED -> MaterialTheme.colorScheme.error
                            RawMessage.STATUS_SUCCESS -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        msg.sourceApp.substringAfterLast('.').uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (msg.status == RawMessage.STATUS_PROCESSING || msg.status == RawMessage.STATUS_PENDING) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { onCancel(msg.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close, 
                                contentDescription = "Cancel",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Content
            Text(
                msg.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Result Summary or Progress
            if (msg.status == RawMessage.STATUS_PROCESSING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round
                )
            } else if (resultSummary != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = SmartTodoCardDefaults.InnerShape
                ) {
                    Text(
                        resultSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (msg.status == RawMessage.STATUS_FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "分析失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { onReprocess(msg) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("重试", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (msg.status == RawMessage.STATUS_CANCELLED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "已取消",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showLogDialog) {
        LogDetailDialog(
            msg = msg,
            context = context,
            onReprocess = onReprocess,
            onDismiss = { showLogDialog = false }
        )
    }
}
