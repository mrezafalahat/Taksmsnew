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
    private val backupCreateCode = 3001
    private val backupOpenCode = 3002

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

    private fun notifyWeb(message: String) {
        runOnUiThread {
            val safe = JSONObject.quote(message)
            webView.evaluateJavascript("window.App && App.nativeToast && App.nativeToast($safe); window.App && App.refresh && App.refresh();", null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data ?: return
        when (requestCode) {
            backupCreateCode -> {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(DataStore.exportAll(this).toByteArray(Charsets.UTF_8))
                    }
                    notifyWeb("بکاپ ذخیره شد")
                } catch (e: Exception) { notifyWeb(e.message ?: "خطای ذخیره بکاپ") }
            }
            backupOpenCode -> {
                try {
                    val raw = contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: ""
                    val ok = DataStore.importAll(this, raw)
                    notifyWeb(if (ok) "بکاپ بازیابی شد" else "فایل بکاپ معتبر نیست")
                } catch (e: Exception) { notifyWeb(e.message ?: "خطای بازیابی بکاپ") }
            }
        }
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
        @JavascriptInterface fun deleteMessage(id: String): Boolean = DataStore.deleteMessage(this@MainActivity, id)
        @JavascriptInterface fun deleteMessageAt(index: Int): Boolean = DataStore.deleteMessageAt(this@MainActivity, index)

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
        fun connectGmail() {
            notifyWeb("اتصال واقعی Gmail با OAuth نیاز به Client ID و تنظیمات Google Cloud دارد")
        }

        @JavascriptInterface
        fun connectOutlook() {
            notifyWeb("اتصال واقعی Outlook با OAuth نیاز به Client ID و تنظیمات Microsoft Azure دارد")
        }

        @JavascriptInterface
        fun backupAll() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "tak_sms_backup.json")
                }
                startActivityForResult(intent, backupCreateCode)
            }
        }

        @JavascriptInterface
        fun restoreAll() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                startActivityForResult(intent, backupOpenCode)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.postDelayed({
            webView.evaluateJavascript("window.App && App.refresh && App.refresh();", null)
        }, 300)
    }
}
