package com.ainscel.mymail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ainscel.mymail.security.SecretStore
import com.ainscel.mymail.ui.home.HomeScreen
import com.ainscel.mymail.ui.settings.SettingsScreen
import com.ainscel.mymail.ui.theme.MyMailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val secretStore = SecretStore(this)
        setContent {
            var showSettings by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = showSettings) { showSettings = false }
            MyMailTheme {
                Scaffold { innerPadding ->
                    if (showSettings) {
                        SettingsScreen(
                            store = secretStore,
                            onBack = { showSettings = false },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        HomeScreen(
                            onOpenSettings = { showSettings = true },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}
