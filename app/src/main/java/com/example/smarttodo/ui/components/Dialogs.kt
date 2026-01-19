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
import androidx.compose.ui.res.stringResource
import com.example.smarttodo.R
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
        title = { Text(stringResource(R.string.title_ai_log)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.label_raw_content), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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

                Text(stringResource(R.string.label_processing_log), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
                                                    context.getString(R.string.format_extracted_time, time) to false
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
                            } else context.getString(R.string.status_waiting_ai) to false
                        } catch (e: Exception) { (msg.aiLog ?: context.getString(R.string.error_parse_log)) to true }
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
                Text(stringResource(R.string.action_reprocess_log))
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
                    Text(stringResource(R.string.action_open_app))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
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
        title = { Text(stringResource(R.string.title_add_todo)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text(stringResource(R.string.hint_manual_input)) }
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
                    Text(stringResource(R.string.action_paste_clipboard))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text(stringResource(R.string.action_generate_todo))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
