package com.example.smarttodo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smarttodo.data.SmartTask

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskList(
    tasks: List<SmartTask>,
    onTaskClick: (Long) -> Unit,
    onToggleComplete: (SmartTask) -> Unit,
    onDelete: (SmartTask) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无待办事项", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                Box(modifier = Modifier.animateItem()) {
                    SmartTaskCard(
                        task = task,
                        onClick = { onTaskClick(task.id) },
                        onToggleComplete = { onToggleComplete(task) },
                        onDelete = { onDelete(task) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxList(
    tasks: List<SmartTask>,
    onTaskClick: (Long) -> Unit,
    onConfirm: (SmartTask) -> Unit,
    onDelete: (SmartTask) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("收件箱是空的", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                Text("AI 识别到的新事项会出现在这里", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                Box(modifier = Modifier.animateItem()) {
                    InboxCard(
                        task = task,
                        onClick = { onTaskClick(task.id) },
                        onConfirm = { onConfirm(task) },
                        onDelete = { onDelete(task) }
                    )
                }
            }
        }
    }
}
