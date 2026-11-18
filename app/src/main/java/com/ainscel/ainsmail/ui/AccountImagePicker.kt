package com.ainscel.ainsmail.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** 登录与设置换头像共用的相册选择入口。 */
@Composable
internal fun rememberAccountImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) currentOnPicked(uri)
    }
    return remember(launcher) {
        {
            launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }
    }
}
