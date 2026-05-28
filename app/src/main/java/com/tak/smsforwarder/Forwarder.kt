package com.tak.smsforwarder

import android.content.Context

object Forwarder {
    fun processIncomingSms(context: Context, sender: String, body: String, time: Long) {
        val rule = RuleMatcher.match(context, sender, body)

        // پیام‌های بدون فیلتر نباید در History / پیام‌ها لاگ شوند.
        if (rule == null) return

        val smsTarget = rule.optString("smsTarget", "").trim()
        val emailTarget = rule.optString("emailTarget", "").trim()

        var okCount = 0
        var errorCount = 0
        val results = mutableListOf<String>()
        val errors = mutableListOf<String>()

        if (smsTarget.isNotEmpty()) {
            val result = SmsForwarder.send(context, smsTarget, body)
            if (result.first) okCount++ else errorCount++
            if (result.first) results.add(result.second) else errors.add(result.second)
        }

        if (emailTarget.isNotEmpty()) {
            Thread {
                val emailResult = EmailForwarder.send(context, emailTarget, "Forwarded SMS", body)
                val status = if (emailResult.first) {
                    if (smsTarget.isNotEmpty() && errorCount > 0) "ارسال ناقص" else "ارسال موفق"
                } else {
                    if (smsTarget.isNotEmpty() && okCount > 0) "ارسال ناقص" else "خطای ارسال"
                }
                DataStore.addMessage(
                    context,
                    sender,
                    body,
                    time,
                    rule,
                    status,
                    (results + if (emailResult.first) emailResult.second else emptyList()).joinToString(" | "),
                    (errors + if (!emailResult.first) emailResult.second else emptyList()).joinToString(" | ")
                )
            }.start()
            return
        }

        val status = when {
            smsTarget.isEmpty() && emailTarget.isEmpty() -> "ذخیره شد"
            errorCount > 0 && okCount == 0 -> "خطای ارسال"
            errorCount > 0 && okCount > 0 -> "ارسال ناقص"
            else -> "ارسال موفق"
        }

        DataStore.addMessage(context, sender, body, time, rule, status, results.joinToString(" | "), errors.joinToString(" | "))
    }
}
