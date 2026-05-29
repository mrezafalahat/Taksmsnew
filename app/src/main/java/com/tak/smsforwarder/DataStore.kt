package com.takpack.smsforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object DataStore {
    private const val PREF = "tak_sms_forwarder_kotlin_final_fixed_v31"
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

    private fun inferForwardType(rule: JSONObject): String {
        val hasSms = rule.optString("smsTarget", "").trim().isNotBlank()
        val hasEmail = rule.optString("emailTarget", "").trim().isNotBlank()
        return when {
            hasSms && hasEmail -> "both"
            hasSms -> "sms"
            hasEmail -> "email"
            else -> "history"
        }
    }

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
            rule.put("forwardType", inferForwardType(rule))

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

    fun deleteMessage(context: Context, id: String): Boolean {
        return try {
            val old = getMessagesArray(context)
            val next = JSONArray()
            for (i in 0 until old.length()) {
                val item = old.getJSONObject(i)
                if (item.optString("id") != id) next.put(item)
            }
            sp(context).edit().putString(MESSAGES, next.toString()).apply()
            true
        } catch (_: Exception) { false }
    }

    fun addMessage(context: Context, sender: String, body: String, time: Long, rule: JSONObject, status: String, error: String) {
        try {
            val old = getMessagesArray(context)
            val next = JSONArray()
            val msg = JSONObject()
            msg.put("id", System.currentTimeMillis().toString() + "_" + kotlin.random.Random.nextInt(1000, 9999))
            msg.put("sender", sender)
            msg.put("body", body)
            msg.put("time", time)
            msg.put("ruleId", rule.optString("id", ""))
            msg.put("ruleName", rule.optString("name", "بدون نام"))
            msg.put("senderNote", RuleMatcher.senderNote(rule, sender))
            msg.put("forwardType", rule.optString("forwardType", "history"))
            msg.put("forwardTarget", RuleMatcher.forwardTarget(rule))
            msg.put("status", status)
            msg.put("error", error)
            next.put(msg)
            val limit = minOf(old.length(), 199)
            for (i in 0 until limit) next.put(old.getJSONObject(i))
            sp(context).edit().putString(MESSAGES, next.toString()).apply()
        } catch (_: Exception) {}
    }

    fun exportAll(context: Context): String {
        return JSONObject()
            .put("app", "TAK SMS Forwarder")
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("rules", getRulesArray(context))
            .put("messages", getMessagesArray(context))
            .put("settings", getSettingsObject(context))
            .put("enabled", isEnabled(context))
            .toString(2)
    }

    fun importAll(context: Context, raw: String): Boolean {
        return try {
            val obj = JSONObject(raw)
            val rules = obj.optJSONArray("rules") ?: JSONArray()
            val messages = obj.optJSONArray("messages") ?: JSONArray()
            val settings = obj.optJSONObject("settings") ?: JSONObject()
            val enabled = obj.optBoolean("enabled", true)
            sp(context).edit()
                .putString(RULES, rules.toString())
                .putString(MESSAGES, messages.toString())
                .putString(SETTINGS, settings.toString())
                .putBoolean(ENABLED, enabled)
                .apply()
            true
        } catch (_: Exception) { false }
    }
}
