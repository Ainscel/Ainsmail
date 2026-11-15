package com.ainscel.ainsmail.security

import android.util.Log

/**
 * 登录诊断日志。只记步骤与原因，不写密钥、授权码、刷新令牌或访问令牌原文。
 */
internal object LoginLog {
    private const val TAG = "AinsmailLogin"

    fun w(step: String, detail: String) {
        Log.w(TAG, "$step: $detail")
    }

    fun e(step: String, detail: String, error: Throwable? = null) {
        if (error == null) {
            Log.e(TAG, "$step: $detail")
        } else {
            Log.e(TAG, "$step: $detail", error)
        }
    }
}
