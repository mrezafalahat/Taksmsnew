package com.takpack.smsforwarder

import android.content.Context
import org.json.JSONObject

object Forwarder {
    private fun targetValues(rule: JSONObject, arrayKey: String, legacyKey: String): String {
        val arr = rule.optJSONArray(arrayKey)
        val values = mutableListOf<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i)
                val v = if (item != null) item.optString("value", "") else arr.optString(i, "")
                val clean = v.substringAfterLast("|").trim()
                if (clean.isNotBlank()) values.add(clean)
            }
        }
        if (values.isNotEmpty()) return values.joinToString("\n")
        return rule.optString(legacyKey, "").trim()
    }

    fun processIncomingSms(context: Context, sender: String, body: String, time: Long) {
        val matchedRules = RuleMatcher.matchAll(context, sender, body)
        if (matchedRules.isEmpty()) return

        for (rule in matchedRules) {
            processRule(context, sender, body, time, rule)
        }
    }

    private fun processRule(context: Context, sender: String, body: String, time: Long, rule: JSONObject) {
        val smsTarget = targetValues(rule, "smsTargets", "smsTarget")
        val emailTarget = targetValues(rule, "emailTargets", "emailTarget")
        val hasSms = smsTarget.isNotBlank()
        val hasEmail = emailTarget.isNotBlank()
        val type = when {
            hasSms && hasEmail -> "both"
            hasSms -> "sms"
            hasEmail -> "email"
            else -> "history"
        }
        rule.put("forwardType", type)

        val errors = mutableListOf<String>()

        if (hasSms) {
            val result = SmsForwarder.send(context, smsTarget, body)
            if (!result.first) errors.add(result.second)
        }

        if (hasEmail) {
            Thread {
                val result = EmailForwarder.send(
                    context,
                    emailTarget,
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
