package me.chino.watchseek.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import me.chino.watchseek.data.ChatHistoryManager
import me.chino.watchseek.data.SettingsManager
import me.chino.watchseek.presentation.theme.WatchSeekTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        val settingsManager = SettingsManager(applicationContext)
        val chatHistoryManager = ChatHistoryManager(applicationContext)

        setContent {
            WatchSeekTheme {
                val navController = rememberSwipeDismissableNavController()
                val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(settingsManager, chatHistoryManager))

                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        MainScreen(
                            viewModel = chatViewModel,
                            onChatSelected = { navController.navigate("chat") },
                            onSettingsSelected = { navController.navigate("settings") },
                            onAboutSelected = { navController.navigate("about") }
                        )
                    }
                    composable("chat") {
                        ChatScreen(viewModel = chatViewModel)
                    }
                    composable("settings") {
                        SettingsScreen(
                            settingsManager = settingsManager,
                            onSaved = { navController.popBackStack() }
                        )
                    }
                    composable("about") {
                        AboutScreen()
                    }
                }
            }
        }
    }
}
