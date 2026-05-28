package com.tak.smsforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RuleStore {
    private const val PREF = "tak_sms_rules"
    private const val KEY_RULES = "rules"
    private const val KEY_HISTORY = "history"

    fun loadRules(context: Context): MutableList<Rule> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_RULES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Rule>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(Rule(
                id = o.optLong("id"),
                name = o.optString("name"),
                sender = o.optString("sender"),
                keyword = o.optString("keyword"),
                target = o.optString("target"),
                suffix = o.optString("suffix"),
                enabled = o.optBoolean("enabled", true),
                allowOtp = o.optBoolean("allowOtp", false)
            ))
        }
        return list
    }

    fun saveRules(context: Context, rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("sender", r.sender)
                put("keyword", r.keyword)
                put("target", r.target)
                put("suffix", r.suffix)
                put("enabled", r.enabled)
                put("allowOtp", r.allowOtp)
            })
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_RULES, arr.toString()).apply()
    }

    fun rulesJson(context: Context): String {
        val arr = JSONArray()
        loadRules(context).forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("sender", r.sender)
                put("keyword", r.keyword)
                put("target", r.target)
                put("suffix", r.suffix)
                put("enabled", r.enabled)
                put("allowOtp", r.allowOtp)
            })
        }
        return arr.toString()
    }

    fun addOrUpdateRule(context: Context, rule: Rule) {
        val rules = loadRules(context)
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) rules[index] = rule else rules.add(rule)
        saveRules(context, rules)
    }

    fun deleteRule(context: Context, id: Long) {
        val rules = loadRules(context)
        rules.removeAll { it.id == id }
        saveRules(context, rules)
    }

    fun toggleRule(context: Context, id: Long, enabled: Boolean) {
        val rules = loadRules(context)
        saveRules(context, rules.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun findMatchingRules(context: Context, sender: String, body: String): List<Rule> {
        val senderRaw = sender.trim()
        val senderUpper = senderRaw.uppercase(Locale.US)
        val senderNorm = normalizePhone(senderRaw)
        val bodyNorm = normalizeText(body)
        return loadRules(context).filter { r ->
            if (!r.enabled) return@filter false
            val ruleSenderRaw = r.sender.trim()
            val ruleSenderUpper = ruleSenderRaw.uppercase(Locale.US)
            val ruleSenderNorm = normalizePhone(ruleSenderRaw)
            val senderOk =
                ruleSenderRaw.isBlank() ||
                senderNorm == ruleSenderNorm ||
                senderNorm.contains(ruleSenderNorm) ||
                ruleSenderNorm.contains(senderNorm) ||
                senderNorm.endsWith(ruleSenderNorm) ||
                senderRaw.endsWith(ruleSenderRaw) ||
                senderUpper == ruleSenderUpper ||
                senderUpper.contains(ruleSenderUpper) ||
                ruleSenderUpper.contains(senderUpper)

            val keys = splitKeywords(r.keyword)
            val keywordOk = r.keyword.isBlank() || keys.all { key -> bodyNorm.contains(normalizeText(key), ignoreCase = true) }
            val otpOk = r.allowOtp || !looksLikeOtp(body)
            senderOk && keywordOk && otpOk
        }
    }

    fun normalizeText(text: String): String {
        return text.replace("ي", "ی").replace("ك", "ک").replace("ة", "ه")
            .replace("ۀ", "ه").replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
            .replace("ؤ", "و").replace("‌", " ").replace("\u200c", " ")
            .replace("\u200e", "").replace("\u200f", "").replace("ـ", "").trim()
    }

    fun splitKeywords(text: String): List<String> =
        text.split(",", "،", "\n", ";", " ").map { it.trim() }.filter { it.isNotBlank() }

    fun splitTargets(targets: String): List<String> =
        targets.split(",", "،", "\n", ";").map { it.trim() }.filter { it.isNotBlank() }

    fun normalizePhone(x: String): String {
        var n = x.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            .replace("\u200e", "").replace("\u200f", "")
        if (n.startsWith("+98")) n = "0" + n.substring(3)
        if (n.startsWith("0098")) n = "0" + n.substring(4)
        if (n.startsWith("98") && n.length == 12) n = "0" + n.substring(2)
        if (n.startsWith("9") && n.length == 10) n = "0$n"
        return n
    }

    fun looksLikeOtp(body: String): Boolean {
        val lower = normalizeText(body).lowercase(Locale.US)
        val words = listOf("otp", "pin", "password", "verify", "verification", "code", "رمز", "کد", "تایید", "تأیید", "بانک", "فعالسازی", "فعال سازی")
        return words.any { lower.contains(normalizeText(it).lowercase(Locale.US)) } && Regex("\\b\\d{4,8}\\b").containsMatchIn(body)
    }

    fun addHistory(context: Context, success: Boolean, sender: String, target: String, filterName: String, message: String, result: String) {
        val arr = JSONArray()
        arr.put(JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("time", SimpleDateFormat("MM/dd, HH:mm", Locale.US).format(Date()))
            put("success", success)
            put("sender", sender)
            put("target", target)
            put("filterName", filterName)
            put("message", message)
            put("result", result)
        })
        val old = JSONArray(historyJson(context))
        for (i in 0 until minOf(old.length(), 80)) arr.put(old.getJSONObject(i))
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun historyJson(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_HISTORY, "[]") ?: "[]"

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_HISTORY, "[]").apply()
    }
}
