package com.example.smarttodo.ui

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.example.smarttodo.ui.components.LogDetailDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smarttodo.R
import com.example.smarttodo.SmartTodoViewModel
import com.example.smarttodo.data.RawMessage
import com.example.smarttodo.data.SmartTask
import com.example.smarttodo.data.SubTaskItem
import com.example.smarttodo.ui.components.LogDetailDialog
import com.example.smarttodo.ui.components.SmartTodoCardDefaults
import com.example.smarttodo.ui.components.SmartOutlinedCard
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: Long,
    viewModel: SmartTodoViewModel,
    onNavigateUp: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var task by remember { mutableStateOf<SmartTask?>(null) }
    var evidence by remember { mutableStateOf<List<RawMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Editable State
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var scheduledTime by remember { mutableStateOf("") }
    val subtasks = remember { mutableStateListOf<SubTaskItem>() }
    var isEditingNotes by remember { mutableStateOf(false) }
    var isNotesExpanded by remember { mutableStateOf(false) }

    // Date/Time Picker State
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    val context = LocalContext.current

    LaunchedEffect(taskId) {
        val loadedTask = viewModel.getTaskById(taskId)
        if (loadedTask != null) {
            task = loadedTask
            title = loadedTask.title
            summary = loadedTask.summary
            notes = loadedTask.notes
            scheduledTime = loadedTask.scheduledTime ?: ""
            subtasks.clear()
            subtasks.addAll(loadedTask.subtasks)
            evidence = viewModel.getTaskEvidence(taskId)
        }
        isLoading = false
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    val selectedDate = datePickerState.selectedDateMillis
                    if (selectedDate != null) {
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = selectedDate
                        calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        calendar.set(Calendar.MINUTE, timePickerState.minute)
                        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        scheduledTime = formatter.format(calendar.time)
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text(stringResource(R.string.hint_title)) },
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        task?.let { t ->
                            val intent = Intent(Intent.ACTION_INSERT)
                                .setData(CalendarContract.Events.CONTENT_URI)
                                .putExtra(CalendarContract.Events.TITLE, title)
                                .putExtra(CalendarContract.Events.DESCRIPTION, summary)
                            if (scheduledTime.isNotBlank()) {
                                try {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                    sdf.parse(scheduledTime)?.let { date ->
                                        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, date.time)
                                        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, date.time + 3600000)
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                            context.startActivity(intent)
                        }
                    }) { Icon(Icons.Default.Event, "Calendar") }
                    IconButton(onClick = {
                        task?.let { t ->
                            viewModel.deleteTask(t)
                            onNavigateUp()
                        }
                    }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
                    TextButton(
                        onClick = {
                            task?.let { t ->
                                viewModel.updateTask(t.copy(
                                    title = title,
                                    summary = summary,
                                    notes = notes,
                                    scheduledTime = scheduledTime.ifBlank { null },
                                    subtasks = subtasks.toList(),
                                    completeness = if (scheduledTime.isNotBlank()) SmartTask.COMPLETENESS_COMPLETE else SmartTask.COMPLETENESS_MISSING_INFO
                                ))
                                onNavigateUp()
                            }
                        }
                    ) { Text(stringResource(R.string.action_save)) }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
        } else if (task == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("任务不存在") }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Notes & Time ---
                item {
                    SectionHeader(stringResource(R.string.label_notes), Icons.Default.Info)
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmartTodoCardDefaults.CardShape,
                        colors = SmartTodoCardDefaults.outlinedCardColors(),
                        border = SmartTodoCardDefaults.cardBorder()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.label_notes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { isEditingNotes = !isEditingNotes }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        if (isEditingNotes) Icons.Default.Visibility else Icons.Default.Edit,
                                        contentDescription = if (isEditingNotes) stringResource(R.string.action_preview) else stringResource(R.string.action_edit),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (isEditingNotes) {
                                TextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    placeholder = { Text(stringResource(R.string.hint_notes)) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { 
                                        isEditingNotes = false
                                        focusManager.clearFocus()
                                    }),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    shape = SmartTodoCardDefaults.InnerShape
                                )
                            } else {
                                Surface(
                                    onClick = { 
                                        if (notes.length > 200) {
                                            isNotesExpanded = !isNotesExpanded
                                        } else {
                                            isEditingNotes = true
                                        }
                                    },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = SmartTodoCardDefaults.InnerShape,
                                    modifier = Modifier.fillMaxWidth().animateContentSize()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (notes.isBlank()) {
                                            Text(stringResource(R.string.hint_notes), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                        } else {
                                            MarkdownText(
                                                markdown = if (isNotesExpanded || notes.length <= 200) notes else notes.take(200) + "...",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                            if (notes.length > 200) {
                                                Text(
                                                    if (isNotesExpanded) stringResource(R.string.notes_collapse) else stringResource(R.string.notes_expand),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 8.dp).align(Alignment.End)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(stringResource(R.string.label_reminder_time), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                onClick = { showDatePicker = true },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = SmartTodoCardDefaults.InnerShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(scheduledTime.ifBlank { "未设置" }, Modifier.weight(1f))
                                    if (scheduledTime.isNotBlank()) {
                                        IconButton(onClick = { scheduledTime = "" }, Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Clear, null, Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Checklist ---
                item {
                    SectionHeader(stringResource(R.string.label_checklist), Icons.Default.Checklist)
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmartTodoCardDefaults.CardShape,
                        colors = SmartTodoCardDefaults.outlinedCardColors(),
                        border = SmartTodoCardDefaults.cardBorder()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            subtasks.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.isDone,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            subtasks[index] = item.copy(isDone = checked)
                                        }
                                    )
                                    TextField(
                                        value = item.content,
                                        onValueChange = { subtasks[index] = item.copy(content = it) },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text(stringResource(R.string.hint_step), style = MaterialTheme.typography.bodyMedium) },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                                            color = if (item.isDone) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                        ),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )
                                    IconButton(onClick = { subtasks.removeAt(index) }) {
                                        Icon(Icons.Default.Close, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                            TextButton(
                                onClick = { subtasks.add(SubTaskItem("")) },
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_add))
                            }
                        }
                    }
                }

                // --- AI Summary ---
                item {
                    SectionHeader(stringResource(R.string.label_ai_summary), Icons.Default.AutoAwesome)
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmartTodoCardDefaults.CardShape,
                        colors = SmartTodoCardDefaults.outlinedCardColors(),
                        border = SmartTodoCardDefaults.cardBorder()
                    ) {
                        MarkdownText(
                            markdown = summary,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                // --- Evidence ---
                if (evidence.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.label_evidence), Icons.Default.Source) }
                    items(evidence) { msg ->
                        Box(modifier = Modifier.animateItem()) {
                            var showDialog by remember { mutableStateOf(false) }
                            SmartOutlinedCard(onClick = { showDialog = true }) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Notifications, null, Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(6.dp))
                                            Text(msg.sourceApp.substringAfterLast('.').uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                        val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                                        Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(msg.content, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (showDialog) {
                                LogDetailDialog(
                                    msg = msg,
                                    context = context,
                                    onReprocess = { viewModel.processNewInput(it.content, it.sourceApp, it.id) },
                                    onDismiss = { showDialog = false }
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
