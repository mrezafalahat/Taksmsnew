package com.takpack.smsforwarder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")

        requestPermissionsOnStart()
    }

    private fun requestPermissionsOnStart() {
        if (Build.VERSION.SDK_INT >= 23) {
            val perms = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECEIVE_SMS)
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_SMS)
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1201)
        }
    }

    private fun hasPermission(name: String): Boolean {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
    }

    private fun batteryOk(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    inner class AndroidBridge {
        @JavascriptInterface fun hasSmsPermission(): Boolean = hasPermission(Manifest.permission.RECEIVE_SMS) && hasPermission(Manifest.permission.READ_SMS)
        @JavascriptInterface fun hasSendSmsPermission(): Boolean = hasPermission(Manifest.permission.SEND_SMS)
        @JavascriptInterface fun isBatteryUnrestricted(): Boolean = batteryOk()

        @JavascriptInterface
        fun openBatterySettings() {
            runOnUiThread {
                try {
                    if (Build.VERSION.SDK_INT >= 23) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } else {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {}
                }
            }
        }

        @JavascriptInterface fun setEnabled(enabled: Boolean) = DataStore.setEnabled(this@MainActivity, enabled)
        @JavascriptInterface fun isEnabled(): Boolean = DataStore.isEnabled(this@MainActivity)

        @JavascriptInterface fun getRules(): String = DataStore.getRules(this@MainActivity)
        @JavascriptInterface fun saveRule(ruleJson: String): Boolean = DataStore.saveRule(this@MainActivity, ruleJson)
        @JavascriptInterface fun deleteRule(id: String): Boolean = DataStore.deleteRule(this@MainActivity, id)
        @JavascriptInterface fun toggleRule(id: String, enabled: Boolean): Boolean = DataStore.toggleRule(this@MainActivity, id, enabled)

        @JavascriptInterface fun getMessages(): String = DataStore.getMessages(this@MainActivity)
        @JavascriptInterface fun clearMessages() = DataStore.clearMessages(this@MainActivity)

        @JavascriptInterface fun getSettings(): String = DataStore.getSettings(this@MainActivity)
        @JavascriptInterface fun saveSettings(raw: String): Boolean = DataStore.saveSettings(this@MainActivity, raw)

        @JavascriptInterface
        fun getStatus(): String {
            return try {
                JSONObject()
                    .put("receiveSms", hasSmsPermission())
                    .put("sendSms", hasSendSmsPermission())
                    .put("enabled", isEnabled())
                    .put("battery", isBatteryUnrestricted())
                    .put("rulesCount", DataStore.getRulesArray(this@MainActivity).length())
                    .put("messagesCount", DataStore.getMessagesArray(this@MainActivity).length())
                    .toString()
            } catch (_: Exception) { "{}" }
        }

        @JavascriptInterface
        fun testSmsForward(target: String): String {
            val result = SmsForwarder.send(this@MainActivity, target, "TAK SMS Forwarder test message")
            return JSONObject().put("ok", result.first).put("message", result.second).toString()
        }

        @JavascriptInterface
        fun testEmailForward(target: String): String {
            var response = JSONObject().put("ok", false).put("message", "در حال ارسال تست Email...").toString()
            val lock = Object()
            Thread {
                val result = EmailForwarder.send(this@MainActivity, target, "TAK SMS Forwarder Test", "This is a test email from TAK SMS Forwarder.")
                response = JSONObject().put("ok", result.first).put("message", result.second).toString()
                synchronized(lock) { lock.notify() }
            }.start()
            synchronized(lock) { try { lock.wait(15000) } catch (_: Exception) {} }
            return response
        }

        @JavascriptInterface
        fun addTestIncomingSms() {
            Forwarder.processIncomingSms(this@MainActivity, "0098100099", "کد تایید بانک ملت 12345 است.", System.currentTimeMillis())
        }
    }

    override fun onResume() {
        super.onResume()
        webView.postDelayed({
            webView.evaluateJavascript("window.App && App.refresh && App.refresh();", null)
        }, 300)
    }
}
