package me.chino.watchseek.presentation

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    onAboutSelected: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    BackHandler(enabled = pagerState.currentPage == 1) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    HorizontalPager(state = pagerState) { page ->
        if (page == 0) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                item { ListHeader { Text("WatchSeek") } }
                item {
                    Chip(
                        onClick = { viewModel.createNewChat(); onChatSelected() },
                        label = { Text("New Chat") },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                item {
                    Chip(
                        onClick = onSettingsSelected,
                        label = { Text("Settings") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                item {
                    Chip(
                        onClick = onAboutSelected,
                        label = { Text("About") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                item {
                    Text(
                        "Swipe Left for History",
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

@Composable
fun HistoryList(viewModel: ChatViewModel, onChatSelected: () -> Unit) {
    val history by viewModel.history.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<Chat?>(null) }
    var chatForMenu by remember { mutableStateOf<Chat?>(null) }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text("History") } }
        if (history.isEmpty()) {
            item {
                Text(
                    "No history yet",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2
                )
            }
        }
        items(history, key = { it.id }) { chat ->
            AppCard(
                onClick = { chatForMenu = chat },
                appName = { Text("Chat") },
                time = {
                    val sdf = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
                    Text(sdf.format(Date(chat.timestamp)))
                },
                title = { Text(chat.title, maxLines = 1) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                if (chat.messagesCount > 0) {
                    Text(
                        text = chat.getMessages(chat.messagesCount - 1).content,
                        maxLines = 1,
                        style = MaterialTheme.typography.caption3,
                        color = Color.Gray
                    )
                }
            }
        }
    }

    Dialog(showDialog = chatForMenu != null, onDismissRequest = { chatForMenu = null }) {
        Alert(
            title = { Text("Options", textAlign = TextAlign.Center) },
            modifier = Modifier.fillMaxWidth(),
            content = {
                item {
                    Chip(
                        onClick = { chatForMenu?.let { viewModel.selectChat(it); onChatSelected() }; chatForMenu = null },
                        label = { Text("View") },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Chip(
                        onClick = { chatToDelete = chatForMenu; showDeleteDialog = true; chatForMenu = null },
                        label = { Text("Delete") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    Dialog(showDialog = showDeleteDialog, onDismissRequest = { showDeleteDialog = false }) {
        Alert(
            title = { Text("Delete Chat") },
            negativeButton = { Button(onClick = { showDeleteDialog = false }) { Text("No") } },
            positiveButton = {
                Button(onClick = { chatToDelete?.let { viewModel.deleteChat(it.id) }; showDeleteDialog = false }) { Text("Yes") }
            },
            content = { Text("Are you sure you want to delete this chat?", textAlign = TextAlign.Center) }
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
                    if (text.isEmpty()) Text("Type here...", color = Color.Gray, fontSize = 14.sp)
                }
            }
        )
    }
}

@Composable
fun AboutScreen() {
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { ListHeader { Text("About") } }
        item {
            Column(modifier = Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("WatchSeek", style = MaterialTheme.typography.title2)
                Text("v1.0.0", style = MaterialTheme.typography.caption2)
                Spacer(modifier = Modifier.height(4.dp))
                Text("A lightweight AI client for your wrist.", textAlign = TextAlign.Center, style = MaterialTheme.typography.caption3)
            }
        }
        item {
            AppCard(onClick = {}, appName = { Text("Developer") }, time = {}, title = { Text("@daisukiKaffuChino", style = MaterialTheme.typography.caption2) }, appImage = { Image(painter = painterResource(id = R.drawable.developer_avatar), contentDescription = "Avatar", modifier = Modifier.size(24.dp).clip(CircleShape), contentScale = ContentScale.Crop) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Feel free to follow me on GitHub!", style = MaterialTheme.typography.caption3)
            }
        }
        item {
            TitleCard(onClick = {}, title = { Text("Open Source") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column {
                    Text("Apache-2.0 licensed", style = MaterialTheme.typography.caption3)
                    Text("github.com/daisukiKaffuChino/WatchSeek", style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.primary, maxLines = 2)
                }
            }
        }
        item { Text("Made with ❤️", style = MaterialTheme.typography.caption2, modifier = Modifier.padding(vertical = 12.dp)) }
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
        val remoteInput = android.app.RemoteInput.Builder("input_value").setLabel(label).build()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        try { launcher.launch(intent) } catch (e: ActivityNotFoundException) { showFallbackInput = true }
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text("Settings") } }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text("API Key", style = MaterialTheme.typography.caption2)
                Button(onClick = { launchInput("API Key", "api_key") }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    val displayKey = if (apiKey.length > 12) "${apiKey.take(6)}...${apiKey.takeLast(6)}" else apiKey.ifEmpty { "Tap to Set Key" }
                    Text(displayKey, maxLines = 1)
                }
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text("Model", style = MaterialTheme.typography.caption2)
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
                label = { Text("Auto-hide Ask button") },
                toggleControl = { Switch(checked = autoHideEnabled) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        item {
            Button(onClick = { if (apiKey.isNotBlank()) { scope.launch { settingsManager.saveSettings(apiKey, selectedModel, baseUrl); onSaved() } } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), enabled = apiKey.isNotBlank()) { Text("Save") }
        }
    }

    TextInputDialog(show = showFallbackInput, title = "Enter Value", onDismiss = { showFallbackInput = false }, onConfirm = { text ->
        when (pendingInputKey) {
            "api_key" -> apiKey = text
            "base_url" -> baseUrl = text
        }
    })
}

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
                } else if (index < prevIndex || (index == prevIndex && offset < prevOffset)) {
                    isButtonVisible = true 
                }
                prevIndex = index
                prevOffset = offset
            }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
            isButtonVisible = true 
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val results = RemoteInput.getResultsFromIntent(result.data!!)
            results?.getCharSequence("chat_input")?.let { viewModel.sendMessage(it.toString()) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            item { ListHeader { Text(chat?.title ?: "Chat") } }
            
            if (!isKeySet) {
                item {
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), backgroundPainter = CardDefaults.cardBackgroundPainter(startBackgroundColor = Color(0xFF422020), endBackgroundColor = Color(0xFF301010))) {
                        Text("API Key not set.\nPlease configure it in Settings.", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else if (messages.isEmpty() && !isLoading) {
                item { Text("No messages yet.\nTap 'Ask' to start!", style = MaterialTheme.typography.body2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
            }

            items(messages) { msg ->
                var showReasoning by remember { mutableStateOf(false) }
                
                Card(onClick = {}, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column {
                        Text(
                            text = if (msg.role == "user") "You" else "AI", 
                            style = MaterialTheme.typography.caption3, 
                            color = if (msg.role == "user") MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Reasoning Section (Collapsible)
                        if (msg.reasoningContent.isNotEmpty()) {
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
                                Text(
                                    text = if (showReasoning) "▼ Thought" else "▶ Thought", 
                                    style = MaterialTheme.typography.caption3, 
                                    color = MaterialTheme.colors.primaryVariant
                                )
                            }
                            if (showReasoning) {
                                MarkdownText(
                                    markdown = msg.reasoningContent,
                                    style = TextStyle(color = Color.Gray, fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Main Content with Markdown Support
                        val displayContent = if (msg.content.isEmpty() && msg.role != "user" && msg.reasoningContent.isNotEmpty()) 
                            "*(Thinking complete)*" else msg.content
                        
                        MarkdownText(
                            markdown = displayContent,
                            style = TextStyle(color = Color.White, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
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
                    Button(onClick = {
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        val remoteInput = android.app.RemoteInput.Builder("chat_input").setLabel("Message").build()
                        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                        try { launcher.launch(intent) } catch (e: ActivityNotFoundException) { showFallbackInput = true }
                    }, modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)) { Text("Ask") }
                }
            }
        }
    }

    if (error != null) {
        Dialog(showDialog = true, onDismissRequest = { viewModel.clearError() }) {
            Alert(
                title = { Text("Error", color = Color.Red) },
                positiveButton = { Button(onClick = { viewModel.clearError() }, colors = ButtonDefaults.primaryButtonColors()) { Text("OK") } },
                negativeButton = { },
                content = { Text(error!!, textAlign = TextAlign.Center, style = MaterialTheme.typography.caption3) }
            )
        }
    }

    TextInputDialog(show = showFallbackInput, title = "New Message", onDismiss = { showFallbackInput = false }, onConfirm = { text -> viewModel.sendMessage(text) })
}
