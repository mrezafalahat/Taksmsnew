package com.tak.smsforwarder

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

class SmsBridge(private val activity: Activity) {

    private val ctx: Context = activity.applicationContext

    @SuppressLint("JavascriptInterface")
    @JavascriptInterface
    fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }
}
