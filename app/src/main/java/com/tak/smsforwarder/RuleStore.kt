package com.tak.smsforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RuleStore {

    private const val PREF_NAME = "sms_rules"
    private const val KEY_RULES = "rules"

    fun save(context: Context, json: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val rules = JSONArray(prefs.getString(KEY_RULES, "[]"))

        val newRule = JSONObject(json)
        val id = newRule.optString("id").ifBlank {
            System.currentTimeMillis().toString()
        }

        newRule.put("id", id)

        val updated = JSONArray()
        var replaced = false

        for (i in 0 until rules.length()) {
            val item = rules.getJSONObject(i)

            if (item.optString("id") == id) {
                updated.put(newRule)
                replaced = true
            } else {
                updated.put(item)
            }
        }

        if (!replaced) {
            updated.put(newRule)
        }

        prefs.edit()
            .putString(KEY_RULES, updated.toString())
            .apply()
    }

    fun getAll(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_RULES, "[]") ?: "[]"
    }

    fun delete(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val rules = JSONArray(prefs.getString(KEY_RULES, "[]"))
        val updated = JSONArray()

        for (i in 0 until rules.length()) {
            val item = rules.getJSONObject(i)

            if (item.optString("id") != id) {
                updated.put(item)
            }
        }

        prefs.edit()
            .putString(KEY_RULES, updated.toString())
            .apply()
    }
}
