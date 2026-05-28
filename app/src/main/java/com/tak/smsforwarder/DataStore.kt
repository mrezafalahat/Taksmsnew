package com.takpack.smsforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object DataStore {
    private const val PREF = "tak_sms_forwarder_kotlin_sms_email_v3"
    private const val RULES = "rules"
    private const val MESSAGES = "messages"
    private const val SETTINGS = "settings"
    private const val ENABLED = "enabled"

    private fun sp(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun setEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context): Boolean = sp(context).getBoolean(ENABLED, true)

    fun getRulesArray(context: Context): JSONArray {
        return try { JSONArray(sp(context).getString(RULES, "[]")) } catch (_: Exception) { JSONArray() }
    }

    fun getRules(context: Context): String = getRulesArray(context).toString()

    fun saveRule(context: Context, rawRule: String): Boolean {
        return try {
            val rule = JSONObject(rawRule)
            var id = rule.optString("id", "")
            if (id.isBlank()) {
                id = System.currentTimeMillis().toString()
                rule.put("id", id)
            }
            if (!rule.has("enabled")) rule.put("enabled", true)
            if (!rule.has("senders")) rule.put("senders", JSONArray())
            if (!rule.has("forwardType")) rule.put("forwardType", "history")

            val old = getRulesArray(context)
            val next = JSONArray()
            var found = false
            for (i in 0 until old.length()) {
                val item = old.getJSONObject(i)
                if (item.optString("id") == id) {
                    next.put(rule)
                    found = true
                } else {
                    next.put(item)
                }
            }
            if (!found) next.put(rule)
            sp(context).edit().putString(RULES, next.toString()).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteRule(context: Context, id: String): Boolean {
        return try {
            val old = getRulesArray(context)
            val next = JSONArray()
            for (i in 0 until old.length()) {
                val item = old.getJSONObject(i)
                if (item.optString("id") != id) next.put(item)
            }
            sp(context).edit().putString(RULES, next.toString()).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun toggleRule(context: Context, id: String, enabled: Boolean): Boolean {
        return try {
            val arr = getRulesArray(context)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (item.optString("id") == id) item.put("enabled", enabled)
            }
            sp(context).edit().putString(RULES, arr.toString()).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getSettingsObject(context: Context): JSONObject {
        return try {
            JSONObject(sp(context).getString(SETTINGS, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun getSettings(context: Context): String = getSettingsObject(context).toString()

    fun saveSettings(context: Context, raw: String): Boolean {
        return try {
            val obj = JSONObject(raw)
            sp(context).edit().putString(SETTINGS, obj.toString()).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getMessagesArray(context: Context): JSONArray {
        return try { JSONArray(sp(context).getString(MESSAGES, "[]")) } catch (_: Exception) { JSONArray() }
    }

    fun getMessages(context: Context): String = getMessagesArray(context).toString()

    fun clearMessages(context: Context) {
        sp(context).edit().putString(MESSAGES, "[]").apply()
    }

    fun addMessage(context: Context, sender: String, body: String, time: Long, rule: JSONObject?, status: String, error: String) {
        try {
            val old = getMessagesArray(context)
            val next = JSONArray()
            val msg = JSONObject()
            msg.put("id", System.currentTimeMillis().toString())
            msg.put("sender", sender)
            msg.put("body", body)
            msg.put("time", time)
            msg.put("ruleId", rule?.optString("id", "") ?: "")
            msg.put("ruleName", rule?.optString("name", "بدون فیلتر") ?: "بدون فیلتر")
            msg.put("senderNote", RuleMatcher.senderNote(rule, sender))
            msg.put("forwardType", rule?.optString("forwardType", "history") ?: "history")
            msg.put("forwardTarget", RuleMatcher.forwardTarget(rule))
            msg.put("status", status)
            msg.put("error", error)
            next.put(msg)
            val limit = minOf(old.length(), 199)
            for (i in 0 until limit) next.put(old.getJSONObject(i))
            sp(context).edit().putString(MESSAGES, next.toString()).apply()
        } catch (_: Exception) {}
    }
}
