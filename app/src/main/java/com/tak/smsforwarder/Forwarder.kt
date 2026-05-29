package com.takpack.smsforwarder

import android.content.Context
import org.json.JSONObject

object Forwarder {
    fun processIncomingSms(context: Context, sender: String, body: String, time: Long) {
        val matchedRules = RuleMatcher.matchAll(context, sender, body)
        if (matchedRules.isEmpty()) return

        for (rule in matchedRules) {
            processRule(context, sender, body, time, rule)
        }
    }

    private fun processRule(context: Context, sender: String, body: String, time: Long, rule: JSONObject) {
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

        if (hasSms) {
            val result = SmsForwarder.send(context, rule.optString("smsTarget", ""), body)
            if (!result.first) errors.add(result.second)
        }

        if (hasEmail) {
            Thread {
                val result = EmailForwarder.send(
                    context,
                    rule.optString("emailTarget", ""),
                    "Forwarded SMS - ${rule.optString("name", "")}",
                    body
                )
                val allErrors = errors.toMutableList()
                if (!result.first) allErrors.add(result.second)

                val status = when {
                    hasSms && hasEmail && allErrors.isEmpty() -> "ارسال SMS و Email موفق"
                    hasSms && hasEmail -> "خطا در بخشی از ارسال"
                    hasSms && allErrors.isEmpty() -> "ارسال SMS موفق"
                    hasSms -> "خطای SMS"
                    hasEmail && result.first -> "ارسال Email موفق"
                    hasEmail -> "خطای Email"
                    else -> "فقط ذخیره شد"
                }
                DataStore.addMessage(context, sender, body, time, rule, status, allErrors.joinToString(" | "))
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
