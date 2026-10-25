// ==================== app/src/main/java/com/paperai/MainActivity.kt ====================
package com.paperai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaperAITheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RootNav()
                }
            }
        }
    }
}

@Composable
fun RootNav() {
    val ctx = LocalContext.current
    val vm: AppViewModel = viewModel()

    var openBookId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    when {
        showSettings -> {
            BackHandler { showSettings = false }
            SettingsScreen(vm = vm, onBack = { showSettings = false })
        }
        openBookId != null -> {
            BackHandler { openBookId = null }
            ChatScreen(
                bookId = openBookId!!,
                vm = vm,
                onBack = { openBookId = null }
            )
        }
        else -> {
            LibraryScreen(
                vm = vm,
                onOpenBook = { openBookId = it },
                onOpenSettings = { showSettings = true }
            )
        }
    }
}
