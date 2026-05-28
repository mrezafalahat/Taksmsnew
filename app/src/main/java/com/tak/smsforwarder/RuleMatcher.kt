package com.takpack.smsforwarder

import android.content.Context
import org.json.JSONObject

object RuleMatcher {
    private fun normalize(value: String?): String {
        return (value ?: "")
            .replace(" ", "")
            .replace("-", "")
            .replace("+98", "0")
            .lowercase()
            .trim()
    }

    fun match(context: Context, sender: String, body: String): JSONObject? {
        val all = "$sender $body".lowercase()
        val normalizedSender = normalize(sender)
        val rules = DataStore.getRulesArray(context)

        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            if (!rule.optBoolean("enabled", true)) continue

            var senderOk = false
            val senders = rule.optJSONArray("senders")
            if (senders == null || senders.length() == 0) {
                senderOk = true
            } else {
                for (j in 0 until senders.length()) {
                    val item = senders.optJSONObject(j) ?: continue
                    val number = normalize(item.optString("number", ""))
                    if (number.isBlank() || normalizedSender.contains(number) || number.contains(normalizedSender)) {
                        senderOk = true
                        break
                    }
                }
            }

            val keywords = rule.optString("keywords", "").trim().lowercase()
            var keywordsOk = keywords.isBlank() || keywords == "همه" || keywords == "all"
            if (!keywordsOk) {
                val parts = keywords.split(",", "،", "\n", " ").map { it.trim() }.filter { it.isNotBlank() }
                keywordsOk = parts.all { all.contains(it) }
            }

            if (senderOk && keywordsOk) return rule
        }

        return null
    }

    fun senderNote(rule: JSONObject?, sender: String): String {
        if (rule == null) return ""
        val ns = normalize(sender)
        val senders = rule.optJSONArray("senders") ?: return ""
        for (i in 0 until senders.length()) {
            val item = senders.optJSONObject(i) ?: continue
            val n = normalize(item.optString("number", ""))
            if (n.isBlank() || ns.contains(n) || n.contains(ns)) return item.optString("note", "")
        }
        return ""
    }

    fun forwardType(rule: JSONObject): String {
        val hasSms = rule.optString("smsTarget", "").trim().isNotEmpty()
        val hasEmail = rule.optString("emailTarget", "").trim().isNotEmpty()
        return when {
            hasSms && hasEmail -> "SMS + Email"
            hasSms -> "SMS"
            hasEmail -> "Email"
            else -> "History"
        }
    }

    fun forwardTarget(rule: JSONObject): String {
        val sms = rule.optString("smsTarget", "").trim()
        val email = rule.optString("emailTarget", "").trim()
        val parts = mutableListOf<String>()
        if (sms.isNotEmpty()) parts.add(sms)
        if (email.isNotEmpty()) parts.add(email)
        return if (parts.isEmpty()) "History" else parts.joinToString("\n")
    }
}
