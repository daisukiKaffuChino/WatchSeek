package me.chino.watchseek.presentation

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.input.RemoteInputIntentHelper
import dev.jeziellago.compose.markdowntext.MarkdownText
import me.chino.watchseek.data.Chat
import me.chino.watchseek.data.ChatMessage
import me.chino.watchseek.data.SettingsManager
import me.chino.watchseek.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(
    viewModel: ChatViewModel,
    onChatSelected: () -> Unit,
    onSettingsSelected: () -> Unit,
    onAboutSelected: () -> Unit,
    onStatisticsSelected: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    
    // 增加一个状态用于控制进入动画
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    BackHandler(enabled = pagerState.currentPage == 1) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.95f),
        modifier = Modifier.fillMaxSize()
    ) {
        HorizontalPager(state = pagerState) { page ->
            if (page == 0) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    item { ListHeader { Text(stringResource(R.string.app_name)) } }
                    item {
                        Chip(
                            onClick = { viewModel.createNewChat(); onChatSelected() },
                            label = { Text(stringResource(R.string.new_chat)) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    item {
                        Chip(
                            onClick = onStatisticsSelected,
                            label = { Text(stringResource(R.string.statistics)) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    item {
                        Chip(
                            onClick = onSettingsSelected,
                            label = { Text(stringResource(R.string.settings)) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    item {
                        Chip(
                            onClick = onAboutSelected,
                            label = { Text(stringResource(R.string.about)) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.swipe_left_history),
                            style = MaterialTheme.typography.caption3,
                            modifier = Modifier.padding(top = 8.dp),
                            color = Color.Gray
                        )
                    }
                }
            } else {
                HistoryList(viewModel, onChatSelected)
            }
        }
    }
}

@Composable
fun StatisticsScreen(viewModel: ChatViewModel) {
    val dailyUsage by viewModel.dailyUsage.collectAsState()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    
    val todayTokens = remember(dailyUsage) { dailyUsage.find { it.date == today }?.totalTokens ?: 0 }
    val totalTokens = remember(dailyUsage) { dailyUsage.sumOf { it.totalTokens } }
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { ListHeader { Text(stringResource(R.string.statistics)) } }
        
        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.today_usage), style = MaterialTheme.typography.caption2)
                    Text("$todayTokens", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
                    Text(stringResource(R.string.tokens), style = MaterialTheme.typography.caption3)
                }
            }
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.total_usage), style = MaterialTheme.typography.caption2)
                    Text("$totalTokens", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.secondary)
                    Text(stringResource(R.string.tokens), style = MaterialTheme.typography.caption3)
                }
            }
        }

        if (dailyUsage.isNotEmpty()) {
            item { MarginSpacer(8.dp) }
            items(dailyUsage.sortedByDescending { it.date }.take(30)) { usage ->
                TitleCard(
                    onClick = {},
                    title = { Text(usage.date) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                ) {
                    Text("${usage.totalTokens} ${stringResource(R.string.tokens)}", style = MaterialTheme.typography.caption3)
                }
            }
            item {
                Text(
                    text = stringResource(R.string.usage_history_limit_note),
                    style = MaterialTheme.typography.caption3,
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
fun MarginSpacer(height: androidx.compose.ui.unit.Dp) {
    Spacer(modifier = Modifier.height(height))
}

@Composable
fun HistoryList(viewModel: ChatViewModel, onChatSelected: () -> Unit) {
    val history by viewModel.history.collectAsState()
    val listState = rememberScalingLazyListState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<Chat?>(null) }
    var chatForMenu by remember { mutableStateOf<Chat?>(null) }
    
    val sdf = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        item { ListHeader { Text(stringResource(R.string.history)) } }
        if (history.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_history),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2
                )
            }
        }
        items(history, key = { it.id }) { chat ->
            val timeText = remember(chat.timestamp) { sdf.format(Date(chat.timestamp)) }
            val lastMessage = remember(chat.messagesCount) {
                if (chat.messagesCount > 0) chat.getMessages(chat.messagesCount - 1).content else ""
            }

            AppCard(
                onClick = { chatForMenu = chat },
                appName = { Text(stringResource(R.string.chat)) },
                time = { Text(timeText) },
                title = { Text(chat.title, maxLines = 1) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                if (lastMessage.isNotEmpty()) {
                    Text(
                        text = lastMessage,
                        maxLines = 1,
                        style = MaterialTheme.typography.caption3,
                        color = Color.Gray
                    )
                }
            }
        }
        
        if (history.isNotEmpty()) {
            item {
                Chip(
                    onClick = { showClearAllDialog = true },
                    label = {
                        Text(
                            text = stringResource(R.string.clear_all),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(contentColor = Color.Red),
                )
            }
        }
    }

    Dialog(showDialog = chatForMenu != null, onDismissRequest = { chatForMenu = null }) {
        Alert(
            title = { Text(stringResource(R.string.options), textAlign = TextAlign.Center) },
            modifier = Modifier.fillMaxWidth(),
            content = {
                item {
                    Chip(
                        onClick = { chatForMenu?.let { viewModel.selectChat(it); onChatSelected() }; chatForMenu = null },
                        label = { Text(stringResource(R.string.view)) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Chip(
                        onClick = { chatToDelete = chatForMenu; showDeleteDialog = true; chatForMenu = null },
                        label = { Text(stringResource(R.string.delete)) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    Dialog(showDialog = showDeleteDialog, onDismissRequest = { showDeleteDialog = false }) {
        Alert(
            title = { Text(stringResource(R.string.delete_chat)) },
            negativeButton = { Button(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.no)) } },
            positiveButton = {
                Button(onClick = { chatToDelete?.let { viewModel.deleteChat(it.id) }; showDeleteDialog = false }) { Text(stringResource(R.string.yes)) }
            },
            content = { Text(stringResource(R.string.delete_confirm), textAlign = TextAlign.Center) }
        )
    }

    Dialog(showDialog = showClearAllDialog, onDismissRequest = { showClearAllDialog = false }) {
        Alert(
            title = { Text(stringResource(R.string.clear_all)) },
            negativeButton = { Button(onClick = { showClearAllDialog = false }) { Text(stringResource(R.string.no)) } },
            positiveButton = {
                Button(onClick = { viewModel.clearAllHistory(); showClearAllDialog = false }) { Text(stringResource(R.string.yes)) }
            },
            content = { Text(stringResource(R.string.clear_all_confirm), textAlign = TextAlign.Center) }
        )
    }
}

@Composable
fun TextInputDialog(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(show) { mutableStateOf("") }
    Dialog(showDialog = show, onDismissRequest = onDismiss) {
        Alert(
            title = { Text(title, style = MaterialTheme.typography.caption1) },
            negativeButton = { Button(onClick = onDismiss) { Text("X") } },
            positiveButton = { Button(onClick = { onConfirm(text); onDismiss() }) { Text("✓") } },
            content = {
                Box(modifier = Modifier.fillMaxWidth().background(Color.DarkGray, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    if (text.isEmpty()) Text(stringResource(R.string.type_here), color = Color.Gray, fontSize = 14.sp)
                }
            }
        )
    }
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val versionName = remember {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { ListHeader { Text(stringResource(R.string.about)) } }
        item {
            Column(modifier = Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.title2)
                Text("v$versionName", style = MaterialTheme.typography.caption2)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.about_desc), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption3)
            }
        }
        item {
            AppCard(onClick = {}, appName = { Text(stringResource(R.string.developer)) }, time = {}, title = { Text("@daisukiKaffuChino", style = MaterialTheme.typography.caption2) }, appImage = { Image(painter = painterResource(id = R.drawable.developer_avatar), contentDescription = "Avatar", modifier = Modifier.size(24.dp).clip(CircleShape), contentScale = ContentScale.Crop) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Feel free to follow me on GitHub!", style = MaterialTheme.typography.caption3)
            }
        }
        item {
            TitleCard(onClick = {}, title = { Text(stringResource(R.string.open_source)) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column {
                    Text("Apache-2.0 licensed", style = MaterialTheme.typography.caption3)
                    Text("github.com/daisukiKaffuChino/WatchSeek", style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.primary, maxLines = 2)
                }
            }
        }
        item { Text(stringResource(R.string.made_with), style = MaterialTheme.typography.caption2, modifier = Modifier.padding(vertical = 12.dp)) }
    }
}

@Composable
fun SettingsScreen(settingsManager: SettingsManager, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val savedKey by settingsManager.apiKey.collectAsState(initial = "")
    val savedModel by settingsManager.model.collectAsState(initial = "gpt-3.5-turbo")
    val savedBaseUrl by settingsManager.baseUrl.collectAsState(initial = "https://api.openai.com/")
    val autoHideEnabled by settingsManager.autoHideChatButton.collectAsState(initial = false)

    var apiKey by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }

    var pendingInputKey by remember { mutableStateOf<String?>(null) }
    var showFallbackInput by remember { mutableStateOf(false) }

    LaunchedEffect(savedKey, savedModel, savedBaseUrl) {
        apiKey = savedKey ?: ""
        selectedModel = savedModel
        baseUrl = savedBaseUrl
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val results = RemoteInput.getResultsFromIntent(result.data!!)
            results?.getCharSequence("input_value")?.let { text ->
                when (pendingInputKey) {
                    "api_key" -> apiKey = text.toString()
                    "base_url" -> baseUrl = text.toString()
                }
            }
        }
        pendingInputKey = null
    }

    fun launchInput(label: String, key: String) {
        pendingInputKey = key
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val remoteInput = RemoteInput.Builder("input_value").setLabel(label).build()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        try { launcher.launch(intent) } catch (_: ActivityNotFoundException) { showFallbackInput = true }
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text(stringResource(R.string.settings)) } }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text("API Key", style = MaterialTheme.typography.caption2)
                Button(onClick = { launchInput("API Key", "api_key") }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    val displayKey = if (apiKey.length > 12) "${apiKey.take(6)}...${apiKey.takeLast(6)}" else apiKey.ifEmpty { stringResource(R.string.tap_to_set) }
                    Text(displayKey, maxLines = 1)
                }
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.model), style = MaterialTheme.typography.caption2)
                val models = listOf("gpt-3.5-turbo", "gpt-4o", "deepseek-chat", "deepseek-reasoner")
                models.forEach { model ->
                    ToggleChip(checked = selectedModel == model, onCheckedChange = { selectedModel = model; if (model.startsWith("deepseek")) baseUrl = "https://api.deepseek.com/" else if (model.startsWith("gpt")) baseUrl = "https://api.openai.com/" }, label = { Text(model) }, toggleControl = { RadioButton(selected = selectedModel == model) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
                }
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text("Base URL", style = MaterialTheme.typography.caption2)
                Button(onClick = { launchInput("Base URL", "base_url") }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(baseUrl, style = MaterialTheme.typography.caption3, maxLines = 1) }
            }
        }
        item {
            ToggleChip(
                checked = autoHideEnabled,
                onCheckedChange = { scope.launch { settingsManager.saveAutoHideChatButton(it) } },
                label = { Text(stringResource(R.string.auto_hide_button)) },
                toggleControl = { Switch(checked = autoHideEnabled) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        item {
            Button(onClick = { if (apiKey.isNotBlank()) { scope.launch { settingsManager.saveSettings(apiKey, selectedModel, baseUrl); onSaved() } } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), enabled = apiKey.isNotBlank()) { Text(stringResource(R.string.save)) }
        }
    }

    TextInputDialog(show = showFallbackInput, title = "Enter Value", onDismiss = { showFallbackInput = false }, onConfirm = { text ->
        when (pendingInputKey) {
            "api_key" -> apiKey = text
            "base_url" -> baseUrl = text
        }
    })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val chat by viewModel.currentChat.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val autoHideEnabled by viewModel.settingsManager.autoHideChatButton.collectAsState(initial = false)
    val isKeySet = !apiKey.isNullOrBlank()
    var showFallbackInput by remember { mutableStateOf(false) }
    val messages = chat?.messagesList ?: emptyList()
    val listState = rememberScalingLazyListState()

    var isButtonVisible by remember { mutableStateOf(true) }
    
    // 用于选择并复制文本的 Dialog 状态
    var messageToCopy by remember { mutableStateOf<ChatMessage?>(null) }

    LaunchedEffect(listState, autoHideEnabled) {
        if (!autoHideEnabled) {
            isButtonVisible = true
            return@LaunchedEffect
        }
        var prevIndex = listState.centerItemIndex
        var prevOffset = listState.centerItemScrollOffset
        snapshotFlow { listState.centerItemIndex to listState.centerItemScrollOffset }
            .collect { (index, offset) ->
                if (index > prevIndex || (index == prevIndex && offset > prevOffset)) {
                    isButtonVisible = false 
                } else if (index < prevIndex || (offset < prevOffset)) {
                    isButtonVisible = true 
                }
                prevIndex = index
                prevOffset = offset
            }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val results = RemoteInput.getResultsFromIntent(result.data!!)
            results?.getCharSequence("chat_input")?.let { viewModel.sendMessage(it.toString()) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item { ListHeader { Text(chat?.title ?: stringResource(R.string.chat)) } }
            
            if (!isKeySet) {
                item {
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), backgroundPainter = CardDefaults.cardBackgroundPainter(startBackgroundColor = Color(0xFF422020), endBackgroundColor = Color(0xFF301010))) {
                        Text(stringResource(R.string.api_key_not_set), style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else if (messages.isEmpty() && !isLoading) {
                item { Text(stringResource(R.string.no_messages), style = MaterialTheme.typography.body2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
            }

            items(messages) { msg ->
                var showReasoning by remember { mutableStateOf(false) }
                
                val msgContent = remember(msg.content, msg.reasoningContent) { msg.content }
                val reasoningContent = remember(msg.reasoningContent) { msg.reasoningContent }
                val haptic = LocalHapticFeedback.current
                
                Card(
                    onClick = { }, 
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                onClick = { },
                                onLongClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    messageToCopy = msg 
                                }
                            )
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = if (msg.role == "user") "You" else "AI", 
                                style = MaterialTheme.typography.caption3, 
                                color = if (msg.role == "user") MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (reasoningContent.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable { showReasoning = !showReasoning }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val reasoningPrefix = if (showReasoning) "▼ " else "▶ "
                                    Text(
                                        text = reasoningPrefix + stringResource(R.string.thinking_process), 
                                        style = MaterialTheme.typography.caption3, 
                                        color = MaterialTheme.colors.primaryVariant
                                    )
                                }
                                if (showReasoning) {
                                    MarkdownText(
                                        markdown = reasoningContent,
                                        style = TextStyle(color = Color.Gray, fontSize = 12.sp),
                                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            val displayContent = if (msgContent.isEmpty() && msg.role != "user" && reasoningContent.isNotEmpty()) 
                                "*(" + stringResource(R.string.thinking_complete) + ")*" else msgContent
                            
                            MarkdownText(
                                markdown = displayContent,
                                style = TextStyle(color = Color.White, fontSize = 14.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            if (isLoading) { item { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } }
            item { Spacer(modifier = Modifier.height(48.dp)) }
        }

        if (isKeySet) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 4.dp), contentAlignment = Alignment.BottomCenter) {
                AnimatedVisibility(
                    visible = isButtonVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Button(
                        onClick = {
                            if (isLoading) {
                                viewModel.stopStreaming()
                            } else {
                                val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                                val remoteInput = RemoteInput.Builder("chat_input").setLabel("Type here…").build()
                                RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                                try {
                                    launcher.launch(intent)
                                } catch (_: ActivityNotFoundException) {
                                    showFallbackInput = true
                                }
                            }
                        },
                        modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                    ) {
                        Text(if (isLoading) stringResource(R.string.stop) else stringResource(R.string.ask))
                    }
                }
            }
        }
    }

    // 复制页面的 Dialog
    if (messageToCopy != null) {
        val currentMsgToCopy = messageToCopy // 锁定当前消息，防止 Dialog 内部异步访问时 messageToCopy 已变 null
        Dialog(showDialog = true, onDismissRequest = { messageToCopy = null }) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item { ListHeader { Text(stringResource(R.string.select_to_copy)) } }
                
                currentMsgToCopy?.let { msg ->
                    if (msg.content.isNotEmpty()) {
                        item { Text("Content", style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.primary) }
                        item {
                            SelectionContainer {
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.body2,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                    
                    if (msg.reasoningContent.isNotEmpty()) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        item { Text("Reasoning", style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.secondary) }
                        item {
                            SelectionContainer {
                                Text(
                                    text = msg.reasoningContent,
                                    style = MaterialTheme.typography.body2,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
                
                item {
                    Chip(
                        onClick = { messageToCopy = null },
                        label = { Text(stringResource(R.string.done)) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (error != null) {
        Dialog(showDialog = true, onDismissRequest = { viewModel.clearError() }) {
            Alert(
                title = { Text(stringResource(R.string.error), color = Color.Red) },
                positiveButton = { Button(onClick = { viewModel.clearError() }, colors = ButtonDefaults.primaryButtonColors()) { Text("OK") } },
                negativeButton = { },
                content = { Text(error!!, textAlign = TextAlign.Center, style = MaterialTheme.typography.caption3) }
            )
        }
    }

    TextInputDialog(show = showFallbackInput, title = stringResource(R.string.new_chat), onDismiss = { showFallbackInput = false }, onConfirm = { text -> viewModel.sendMessage(text) })
}
