package com.takpack.smsforwarder

import android.content.Context

object Forwarder {
    fun processIncomingSms(context: Context, sender: String, body: String, time: Long) {
        val rule = RuleMatcher.match(context, sender, body) ?: return

        val hasSms = rule.optString("smsTarget", "").trim().isNotBlank()
        val hasEmail = rule.optString("emailTarget", "").trim().isNotBlank()
        val type = when {
            hasSms && hasEmail -> "both"
            hasSms -> "sms"
            hasEmail -> "email"
            else -> "history"
        }
        rule.put("forwardType", type)

        val errors = mutableListOf<String>()
        val success = mutableListOf<String>()

        if (hasSms) {
            val result = SmsForwarder.send(context, rule.optString("smsTarget", ""), body)
            if (result.first) success.add("SMS") else errors.add(result.second)
        }

        if (hasEmail) {
            Thread {
                val result = EmailForwarder.send(
                    context,
                    rule.optString("emailTarget", ""),
                    "Forwarded SMS - ${rule.optString("name", "")}",
                    body
                )
                val status = when {
                    hasSms && errors.isEmpty() && result.first -> "ارسال SMS و Email موفق"
                    hasSms && result.first -> "ارسال Email موفق"
                    result.first -> "ارسال Email موفق"
                    else -> "خطای Email"
                }
                val finalError = (errors + if (result.first) emptyList() else listOf(result.second)).joinToString(" | ")
                DataStore.addMessage(context, sender, body, time, rule, status, finalError)
            }.start()
            return
        }

        val status = when {
            hasSms && errors.isEmpty() -> "ارسال SMS موفق"
            hasSms -> "خطای SMS"
            else -> "فقط ذخیره شد"
        }
        DataStore.addMessage(context, sender, body, time, rule, status, errors.joinToString(" | "))
    }
}
