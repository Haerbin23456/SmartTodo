package com.example.smarttodo.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smarttodo.SmartTodoViewModel
import com.example.smarttodo.logic.AppManagementDialog
import com.example.smarttodo.logic.SettingsDialog
import com.example.smarttodo.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: SmartTodoViewModel,
    onTaskClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    // UI State
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Tasks, 1 = Inbox, 2 = Stream
    val activeTasks by viewModel.activeTasks.collectAsState()
    val draftTasks by viewModel.draftTasks.collectAsState()
    val rawMessages by viewModel.messageStream.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsState()

    var showManualInput by remember { mutableStateOf(false) }
    var showAppDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                LargeTopAppBar(
                    title = { 
                        Text(when(selectedTab) {
                            0 -> "我的待办"
                            1 -> "收件箱"
                            else -> "信息流"
                        }) 
                    },
                    actions = {
                        IconButton(onClick = { showAppDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "App Filters")
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                AnimatedVisibility(
                    visible = isProcessing,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ProcessingIndicator(
                        onCancel = { viewModel.cancelProcessing() }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showManualInput = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        },
        bottomBar = {
            MainNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                badgeCount = draftTasks.size
            )
        }
    ) { padding ->
        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(300),
            modifier = Modifier.padding(padding)
        ) { tab ->
            when(tab) {
                0 -> TaskList(
                    tasks = activeTasks,
                    onTaskClick = onTaskClick,
                    onToggleComplete = { task -> viewModel.toggleTaskCompletion(task) },
                    onDelete = { task -> viewModel.deleteTask(task) }
                )
                1 -> InboxList(
                    tasks = draftTasks,
                    onTaskClick = onTaskClick,
                    onConfirm = { task -> viewModel.confirmTask(task) },
                    onDelete = { task -> viewModel.deleteTask(task) }
                )
                else -> RawStreamList(
                    messages = rawMessages,
                    context = context,
                    onReprocess = { msg -> viewModel.processNewInput(msg.content, msg.sourceApp, msg.id) },
                    onCancel = { id -> viewModel.cancelMessage(id) },
                    onCancelAll = { viewModel.cancelAllMessages() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Dialogs
    if (showManualInput) {
        ManualInputDialog(
            onDismiss = { showManualInput = false },
            onConfirm = { text ->
                viewModel.processNewInput(text, "Manual Input")
                showManualInput = false
            },
            context = context
        )
    }

    if (showAppDialog) {
        AppManagementDialog(
            onDismiss = { showAppDialog = false },
            context = context
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            onSave = { key, url -> viewModel.saveApiConfig(key, url) },
            initialApiKey = apiKey,
            initialBaseUrl = apiBaseUrl,
            context = context
        )
    }
}

@Composable
fun ProcessingIndicator(onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "AI 正在分析中...",
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("停止", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun MainNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    badgeCount: Int
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.CheckCircle, null) },
            label = { Text("待办") }
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { 
                BadgedBox(
                    badge = {
                        if (badgeCount > 0) {
                            Badge { Text("$badgeCount") }
                        }
                    }
                ) {
                    Icon(Icons.Default.Inbox, null)
                }
            },
            label = { Text("收件箱") }
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.Dns, null) },
            label = { Text("信息流") }
        )
    }
}
