package com.ainscel.ainsmail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ainscel.ainsmail.ui.debug.FrameJankOverlay
import com.ainscel.ainsmail.ui.nav.MailApp
import com.ainscel.ainsmail.ui.theme.AinsmailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AinsmailTheme {
                Box(Modifier.fillMaxSize()) {
                    MailApp()
                    FrameJankOverlay(
                        Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 4.dp, end = 8.dp),
                    )
                }
            }
        }
    }
}
