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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.smarttodo.R
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
    val customPrompt by viewModel.customPrompt.collectAsState()
    val silenceTimeout by viewModel.silenceTimeout.collectAsState()

    var showManualInput by remember { mutableStateOf(false) }
    var showAppDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                LargeTopAppBar(
                    title = { 
                        Text(
                            when(selectedTab) {
                                0 -> stringResource(R.string.title_tasks)
                                1 -> stringResource(R.string.title_inbox)
                                else -> stringResource(R.string.title_stream)
                            },
                            style = if (scrollBehavior.state.collapsedFraction > 0.5f) 
                                MaterialTheme.typography.titleLarge 
                            else 
                                MaterialTheme.typography.headlineLarge,
                            fontWeight = if (scrollBehavior.state.collapsedFraction > 0.5f)
                                FontWeight.Medium
                            else
                                FontWeight.ExtraBold
                        ) 
                    },
                    actions = {
                        IconButton(onClick = { showAppDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "App Filters")
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
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
                onSave = { key, url, prompt, timeout -> 
                    viewModel.saveApiConfig(key, url)
                    viewModel.saveCustomPrompt(prompt)
                    viewModel.saveSilenceTimeout(timeout)
                },
                initialApiKey = apiKey,
                initialBaseUrl = apiBaseUrl,
                initialPrompt = customPrompt,
                initialSilenceTimeout = silenceTimeout,
                context = context
            )
        }
}

@Composable
fun MainNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    badgeCount: Int
) {
    val haptic = LocalHapticFeedback.current
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTabSelected(0)
            },
            icon = { Icon(Icons.Default.CheckCircle, null) },
            label = { Text(stringResource(R.string.tab_tasks)) }
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTabSelected(1)
            },
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
            label = { Text(stringResource(R.string.tab_inbox)) }
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTabSelected(2)
            },
            icon = { Icon(Icons.Default.Dns, null) },
            label = { Text(stringResource(R.string.tab_stream)) }
        )
    }
}
