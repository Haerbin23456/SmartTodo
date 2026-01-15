package com.example.smarttodo.logic

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val COMMON_APPS = setOf(
    "com.tencent.mm",       // 微信
    "com.tencent.mobileqq", // QQ
    "com.alibaba.android.rimet", // 钉钉
    "com.ss.android.lark",  // 飞书
    "com.eg.android.AlipayGphone", // 支付宝
    "com.whatsapp",
    "com.tencent.wework"    // 企业微信
)

data class AppInfo(
    val name: String,
    val packageName: String,
    val isSelected: Boolean,
    val isSystem: Boolean, // 是否是系统应用
    val icon: android.graphics.drawable.Drawable? = null
)

fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val sharedPrefs = context.getSharedPreferences("SmartTodoPrefs", Context.MODE_PRIVATE)
    val packages = pm.getInstalledPackages(0)

    // 检查是否是第一次运行
    val isFirstRun = !sharedPrefs.contains("HAS_INIT_DEFAULTS")
    
    val result = packages.mapNotNull { p ->
        p.applicationInfo?.let { appInfo ->
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val packageName = p.packageName

            // 默认勾选逻辑：如果是第一次运行且在常用列表里，则勾选
            val isDefaultCommon = isFirstRun && COMMON_APPS.contains(packageName)
            val isSelected = sharedPrefs.getBoolean(packageName, isDefaultCommon)

            AppInfo(
                name = appInfo.loadLabel(pm).toString(),
                packageName = packageName,
                isSelected = isSelected,
                isSystem = isSystem,
                icon = null // 为了性能，图标可以按需加载
            )
        }
    }.sortedByDescending { it.isSelected }

    // 统一处理第一次运行的默认值持久化，减少循环内的 edit 开启次数
    if (isFirstRun) {
        sharedPrefs.edit {
            putBoolean("HAS_INIT_DEFAULTS", true)
            result.filter { it.isSelected }.forEach {
                putBoolean(it.packageName, true)
            }
        }
    }

    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String?, Int) -> Unit,
    initialApiKey: String,
    initialBaseUrl: String,
    initialPrompt: String?,
    initialSilenceTimeout: Int,
    context: Context
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var prompt by remember { mutableStateOf(initialPrompt ?: PromptProvider.getDefaultPrompt()) }
    var silenceTimeout by remember { mutableIntStateOf(initialSilenceTimeout) }
    var showPassword by remember { mutableStateOf(false) }
    var showPromptEditor by remember { mutableStateOf(false) }

    if (showPromptEditor) {
        AlertDialog(
            onDismissRequest = { showPromptEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            title = { 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("编辑 AI 系统提示词")
                    TextButton(onClick = { prompt = PromptProvider.getDefaultPrompt() }) {
                        Text("恢复默认")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        "可用变量: \$currentTime, \$language, \$contextJson",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPromptEditor = false }) {
                    Text("确定")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // API Key Input
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("DeepSeek API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    }
                )

                // Base URL Input
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://api.deepseek.com/chat/completions") }
                )

                // Silence Timeout Input
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("输出停滞自动断开", style = MaterialTheme.typography.labelLarge)
                        Text("${silenceTimeout}秒", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = silenceTimeout.toFloat(),
                        onValueChange = { silenceTimeout = it.toInt() },
                        valueRange = 5f..60f,
                        steps = 11 // 5, 10, 15, ..., 60
                    )
                    Text(
                        "当 AI 超过该时间没有新字输出时，将自动停止并保存当前结果。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                HorizontalDivider()

                // Prompt Editor Link
                TextButton(
                    onClick = { showPromptEditor = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("自定义 AI 系统提示词 (Prompt)")
                }

                HorizontalDivider()

                // System Settings Link
                TextButton(
                    onClick = {
                        context.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("打开系统通知权限设置")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalPrompt = if (prompt == PromptProvider.getDefaultPrompt()) null else prompt
                onSave(apiKey, baseUrl, finalPrompt, silenceTimeout)
                onDismiss()
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagementDialog(
    onDismiss: () -> Unit,
    context: Context
) {
    // 1. 原始数据状态
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 异步加载应用列表
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = getInstalledApps(context)
            allApps = apps
            isLoading = false
        }
    }

    // 2. 搜索和筛选状态
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("全部") } // 全部 / 已选 / 未选
    var typeMode by remember { mutableStateOf("全部") }   // 全部 / 第三方 / 系统

    // 3. 计算过滤后的列表 (这是 2026 年流畅 UI 的核心)
    val filteredApps = remember(allApps, searchQuery, filterMode, typeMode) {
        allApps.filter { app ->
            val matchSearch = app.name.contains(searchQuery, ignoreCase = true) ||
                             app.packageName.contains(searchQuery, ignoreCase = true)
            val matchFilter = when (filterMode) {
                "已选" -> app.isSelected
                "未选" -> !app.isSelected
                else -> true
            }
            val matchType = when (typeMode) {
                "第三方" -> !app.isSystem
                "系统" -> app.isSystem
                else -> true
            }
            matchSearch && matchFilter && matchType
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize().padding(vertical = 40.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false), // 全屏感
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        title = {
            Column {
                Text("应用监听管理", style = MaterialTheme.typography.headlineSmall)
                if (!isLoading) {
                    // 搜索栏
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索应用名或包名...") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                }
            }
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column {
                    // 第一行：按状态筛选 (已选/未选)
                Text(
                    "状态筛选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("全部", "已选", "未选").forEach { tag ->
                        FilterChip(
                            selected = filterMode == tag,
                            onClick = { filterMode = tag },
                            label = { Text(tag) }
                        )
                    }
                }

                // 第二行：按类型筛选 (第三方/系统)
                Text(
                    "类型筛选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("全部", "第三方", "系统").forEach { tag ->
                        FilterChip(
                            selected = typeMode == tag,
                            onClick = { typeMode = tag },
                            label = { Text(tag) }
                        )
                    }
                }

                // 全选/反选 + 统计信息
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "显示 ${filteredApps.size} 个应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row {
                        TextButton(onClick = {
                            allApps = allApps.map { app ->
                                // 逻辑：只全选当前筛选结果中的内容
                                val isSelected = if (filteredApps.any { it.packageName == app.packageName }) true else app.isSelected
                                if (isSelected) {
                                    context.getSharedPreferences("SmartTodoPrefs", Context.MODE_PRIVATE)
                                        .edit { putBoolean(app.packageName, true) }
                                }
                                app.copy(isSelected = isSelected)
                            }
                        }) { Text("全选当前") }

                        TextButton(onClick = {
                            allApps = allApps.map { app ->
                                val isSelected = if (filteredApps.any { it.packageName == app.packageName }) false else app.isSelected
                                context.getSharedPreferences("SmartTodoPrefs", Context.MODE_PRIVATE)
                                    .edit { putBoolean(app.packageName, isSelected) }
                                app.copy(isSelected = isSelected)
                            }
                        }) { Text("重置当前") }
                    }
                }

                // 应用列表
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppListItem(app) { checked ->
                            allApps = allApps.map {
                                if (it.packageName == app.packageName) it.copy(isSelected = checked) else it
                            }
                            context.getSharedPreferences("SmartTodoPrefs", Context.MODE_PRIVATE)
                                .edit { putBoolean(app.packageName, checked) }
                        }
                    }
                }
                }
            }
        }
    )
}

@Composable
fun AppListItem(app: AppInfo, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        // 图标加载 (2026 优雅处理)
        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
            // 这里建议引入 Coil 库或使用我们之前的 DrawablePainter
            // 暂时用文字代替图标演示
            Text(app.name.take(1), modifier = Modifier.align(Alignment.Center))
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(app.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }

        Switch(checked = app.isSelected, onCheckedChange = onCheckedChange)
    }
}
