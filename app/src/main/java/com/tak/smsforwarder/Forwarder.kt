package com.takpack.smsforwarder

import android.content.Context

object Forwarder {
    fun processIncomingSms(context: Context, sender: String, body: String, time: Long) {
        val matchedRules = RuleMatcher.matchAll(context, sender, body)
        if (matchedRules.isEmpty()) return

        for (rule in matchedRules) {
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
                    val finalErrors = (errors + if (result.first) emptyList() else listOf(result.second)).joinToString(" | ")
                    val status = when {
                        hasSms && errors.isEmpty() && result.first -> "ارسال SMS و Email موفق"
                        hasSms && errors.isEmpty() && !result.first -> "ارسال SMS موفق / خطای Email"
                        hasSms && errors.isNotEmpty() && result.first -> "ارسال Email موفق / خطای SMS"
                        hasSms -> "خطای SMS و Email"
                        result.first -> "ارسال Email موفق"
                        else -> "خطای Email"
                    }
                    DataStore.addMessage(context, sender, body, time, rule, status, finalErrors)
                }.start()
                continue
            }

            val status = when {
                hasSms && errors.isEmpty() -> "ارسال SMS موفق"
                hasSms -> "خطای SMS"
                else -> "فقط ذخیره شد"
            }
            DataStore.addMessage(context, sender, body, time, rule, status, errors.joinToString(" | "))
        }
    }
}
