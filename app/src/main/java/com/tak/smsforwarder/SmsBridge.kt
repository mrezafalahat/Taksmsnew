package com.tak.smsforwarder

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class SmsBridge(private val activity: MainActivity) {
    private val ctx: Context = activity.applicationContext
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun getRules(): String = RuleStore.rulesJson(ctx)

    @JavascriptInterface
    fun getHistory(): String = RuleStore.historyJson(ctx)

    @JavascriptInterface
    fun saveRule(idText: String, name: String, sender: String, keyword: String, target: String, suffix: String, enabled: Boolean, allowOtp: Boolean): String {
        val id = idText.toLongOrNull() ?: System.currentTimeMillis()
        val finalName = if (name.isBlank()) { if (sender.isBlank()) "Filter" else sender } else name
        val rule = Rule(id, finalName, sender, keyword, target, suffix, enabled, allowOtp)
        RuleStore.addOrUpdateRule(ctx, rule)
        return "OK"
    }

    @JavascriptInterface
    fun deleteRule(idText: String): String {
        idText.toLongOrNull()?.let { RuleStore.deleteRule(ctx, it) }
        return "OK"
    }

    @JavascriptInterface
    fun toggleRule(idText: String, enabled: Boolean): String {
        idText.toLongOrNull()?.let { RuleStore.toggleRule(ctx, it, enabled) }
        return "OK"
    }

    @JavascriptInterface
    fun clearHistory(): String {
        RuleStore.clearHistory(ctx)
        return "OK"
    }

    @JavascriptInterface
    fun requestBattery(): String {
        mainHandler.post { activity.requestBatteryFromBridge() }
        return "OK"
    }
}
