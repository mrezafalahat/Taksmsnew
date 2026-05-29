package com.takpack.smsforwarder

import android.content.Context
import org.json.JSONObject

object RuleMatcher {
    private fun normalizeNumber(value: String?): String {
        return (value ?: "")
            .replace(" ", "")
            .replace("-", "")
            .replace("+98", "0")
            .lowercase()
            .trim()
    }

    private fun normalizeText(value: String?): String {
        return (value ?: "")
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('ى', 'ی')
            .replace('ۀ', 'ه')
            .replace('ة', 'ه')
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3").replace("۴", "4")
            .replace("۵", "5").replace("۶", "6").replace("۷", "7").replace("۸", "8").replace("۹", "9")
            .replace("٠", "0").replace("١", "1").replace("٢", "2").replace("٣", "3").replace("٤", "4")
            .replace("٥", "5").replace("٦", "6").replace("٧", "7").replace("٨", "8").replace("٩", "9")
            .lowercase()
            .trim()
    }

    private fun compact(value: String?): String {
        return normalizeText(value)
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")
    }

    private fun tokens(value: String?): List<String> {
        val normalized = normalizeText(value)
        return Regex("[\\p{L}]+|\\d+")
            .findAll(normalized)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun parseList(value: String?): List<String> {
        val raw = (value ?: "").trim()
        if (raw.isBlank() || raw == "همه" || raw.lowercase() == "all") return emptyList()
        return raw
            .split('\n', ',', '،', ';', '؛')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "همه" && it.lowercase() != "all" }
    }

    private fun orderedTokensMatch(messageTokens: List<String>, phraseTokens: List<String>): Boolean {
        if (phraseTokens.isEmpty()) return true
        var pos = 0
        for (token in messageTokens) {
            if (token == phraseTokens[pos]) {
                pos++
                if (pos == phraseTokens.size) return true
            }
        }
        return false
    }

    private fun smartPhraseMatch(message: String, phrase: String): Boolean {
        val phraseCompact = compact(phrase)
        if (phraseCompact.isBlank()) return true

        val messageCompact = compact(message)
        if (messageCompact.contains(phraseCompact)) return true

        val phraseTokens = tokens(phrase)
        if (phraseTokens.isEmpty()) return true

        return orderedTokensMatch(tokens(message), phraseTokens)
    }

    private fun legacyKeywordsOk(message: String, keywords: String): Boolean {
        val list = parseList(keywords)
        if (list.isEmpty()) return true
        return list.all { smartPhraseMatch(message, it) }
    }

    private fun smartAnyOk(message: String, smartPhrases: String): Boolean {
        val list = parseList(smartPhrases)
        if (list.isEmpty()) return true
        return list.any { smartPhraseMatch(message, it) }
    }

    fun match(context: Context, sender: String, body: String): JSONObject? {
        val message = "$sender $body"
        val normalizedSender = normalizeNumber(sender)
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
                    val number = normalizeNumber(item.optString("number", ""))
                    if (number.isBlank() || normalizedSender.contains(number) || number.contains(normalizedSender)) {
                        senderOk = true
                        break
                    }
                }
            }

            val keywordsOk = legacyKeywordsOk(message, rule.optString("keywords", ""))
            val smartOk = smartAnyOk(message, rule.optString("smartPhrases", ""))

            if (senderOk && keywordsOk && smartOk) return rule
        }

        return null
    }

    fun senderNote(rule: JSONObject?, sender: String): String {
        if (rule == null) return ""
        val ns = normalizeNumber(sender)
        val senders = rule.optJSONArray("senders") ?: return ""
        for (i in 0 until senders.length()) {
            val item = senders.optJSONObject(i) ?: continue
            val n = normalizeNumber(item.optString("number", ""))
            if (n.isBlank() || ns.contains(n) || n.contains(ns)) return item.optString("note", "")
        }
        return ""
    }

    fun forwardTarget(rule: JSONObject?): String {
        if (rule == null) return ""
        val sms = rule.optString("smsTarget", "").trim()
        val email = rule.optString("emailTarget", "").trim()
        val parts = mutableListOf<String>()
        if (sms.isNotBlank()) parts.add("SMS: $sms")
        if (email.isNotBlank()) parts.add("Email: $email")
        return if (parts.isEmpty()) "History" else parts.joinToString(" | ")
    }
}
