package com.tak.smsforwarder

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

    companion object {
        private const val REQ_SMS_PERMISSION = 1001
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestSmsPermissions()
        requestBatteryIgnore()

        webView = WebView(this)
        setContentView(webView)

        webView.webViewClient = WebViewClient()

        val s: WebSettings = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false

        webView.addJavascriptInterface(SmsBridge(), "Android")
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun requestSmsPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS
            )

            val needPermission = permissions.any {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }

            if (needPermission) {
                requestPermissions(permissions, REQ_SMS_PERMISSION)
            }
        }
    }

    private fun requestBatteryIgnore() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            } catch (_: Exception) {
            }
        }
    }

    inner class SmsBridge {

        @JavascriptInterface
        fun saveRule(json: String): String {
            return try {
                RuleStore.save(this@MainActivity, json)
                """{"ok":true}"""
            } catch (e: Exception) {
                """{"ok":false,"error":"${e.message ?: "save error"}"}"""
            }
        }

        @JavascriptInterface
        fun getRules(): String {
            return try {
                RuleStore.getAll(this@MainActivity)
            } catch (e: Exception) {
                "[]"
            }
        }

        @JavascriptInterface
        fun deleteRule(id: String): String {
            return try {
                RuleStore.delete(this@MainActivity, id)
                """{"ok":true}"""
            } catch (e: Exception) {
                """{"ok":false,"error":"${e.message ?: "delete error"}"}"""
            }
        }

        @JavascriptInterface
        fun sendSms(phoneNumber: String, message: String): String {
            return try {
                Forwarder.send(this@MainActivity, phoneNumber, message)
                """{"ok":true}"""
            } catch (e: Exception) {
                """{"ok":false,"error":"${e.message ?: "send error"}"}"""
            }
        }

        @JavascriptInterface
        fun testRule(json: String): String {
            return try {
                val obj = JSONObject(json)

                val phone = obj.optString("phoneNumber", "")
                val message = obj.optString("message", "")

                if (phone.isNotBlank() && message.isNotBlank()) {
                    Forwarder.send(this@MainActivity, phone, message)
                    """{"ok":true}"""
                } else {
                    """{"ok":false,"error":"phoneNumber or message is empty"}"""
                }

            } catch (e: Exception) {
                """{"ok":false,"error":"${e.message ?: "test error"}"}"""
            }
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
