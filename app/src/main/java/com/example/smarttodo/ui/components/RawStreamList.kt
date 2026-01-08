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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.smarttodo.ui.components.SmartOutlinedCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier,
    useLazyColumn: Boolean = true // Default true for infinite lists, set false for nested in ScrollView
) {
    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无信息流", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        }
    } else if (useLazyColumn) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
        ) {
            items(messages, key = { it.id }) { msg ->
                Box(modifier = Modifier.animateItem()) {
                    RawMessageItem(msg, context, onReprocess)
                }
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
        ) {
            messages.forEach { msg ->
                RawMessageItem(msg, context, onReprocess)
            }
        }
    }
}

@Composable
fun RawMessageItem(
    msg: RawMessage,
    context: Context,
    onReprocess: (RawMessage) -> Unit
) {
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val timeStr = dateFormat.format(Date(msg.timestamp))
    var showLogDialog by remember { mutableStateOf(false) }

    SmartOutlinedCard(
            onClick = { showLogDialog = true },
            isError = msg.status == RawMessage.STATUS_FAILED,
            modifier = Modifier.fillMaxWidth()
        ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (msg.status == RawMessage.STATUS_FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        msg.sourceApp.substringAfterLast('.').uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (msg.status == RawMessage.STATUS_FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(msg.sourceApp)
                            launchIntent?.let { context.startActivity(it) }
                        }
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (msg.status == RawMessage.STATUS_PROCESSING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (msg.status == RawMessage.STATUS_FAILED) {
                        Text(
                            "分析失败",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                msg.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (msg.status == RawMessage.STATUS_PROCESSING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            }
            
            if (msg.status == RawMessage.STATUS_FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { onReprocess(msg) },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("重试分析")
                }
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
