package com.ainscel.mymail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ainscel.mymail.security.SecretStore
import com.ainscel.mymail.ui.nav.MailApp
import com.ainscel.mymail.ui.theme.MyMailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val secretStore = SecretStore(this)
        setContent {
            MyMailTheme {
                MailApp(secretStore = secretStore)
            }
        }
    }
}
