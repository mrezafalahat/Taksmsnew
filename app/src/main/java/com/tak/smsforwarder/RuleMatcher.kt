package com.takpack.smsforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RuleMatcher {
    private fun normalizePhone(value: String?): String {
        return normalizeText(value)
            .replace(" ", "")
            .replace("-", "")
            .replace("+98", "0")
            .trim()
    }

    private fun normalizeText(value: String?): String {
        return (value ?: "")
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('ة', 'ه')
            .replace('ۀ', 'ه')
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('۰', '0')
            .replace('۱', '1')
            .replace('۲', '2')
            .replace('۳', '3')
            .replace('۴', '4')
            .replace('۵', '5')
            .replace('۶', '6')
            .replace('۷', '7')
            .replace('۸', '8')
            .replace('۹', '9')
            .replace('٠', '0')
            .replace('١', '1')
            .replace('٢', '2')
            .replace('٣', '3')
            .replace('٤', '4')
            .replace('٥', '5')
            .replace('٦', '6')
            .replace('٧', '7')
            .replace('٨', '8')
            .replace('٩', '9')
            .lowercase()
            .trim()
    }

    private fun compact(value: String?): String {
        return normalizeText(value)
            .replace(Regex("\\s+"), "")
            .replace("‌", "")
            .trim()
    }

    private fun terms(raw: String?): List<String> {
        val text = normalizeText(raw)
        if (text.isBlank() || text == "همه" || text == "all") return emptyList()
        return text.split("\n", ",", "،", ";", "؛")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "همه" && it != "all" }
    }

    private fun containsTerm(compactHaystack: String, term: String): Boolean {
        val c = compact(term)
        return c.isBlank() || compactHaystack.contains(c)
    }

    private fun tokenize(value: String?): List<String> {
        return normalizeText(value)
            .split(Regex("[^0-9a-zA-Zآ-ی]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun nearMatch(body: String, target: String, nearWordsRaw: String, distance: Int): Boolean {
        val targetCompact = compact(target)
        val nearWords = terms(nearWordsRaw).map { compact(it) }.filter { it.isNotBlank() }
        if (targetCompact.isBlank() || nearWords.isEmpty()) return true

        val tokens = tokenize(body)
        if (tokens.isEmpty()) return false
        val compactTokens = tokens.map { compact(it) }
        val safeDistance = distance.coerceIn(1, 20)

        val targetIndexes = compactTokens.mapIndexedNotNull { index, token ->
            if (token.contains(targetCompact)) index else null
        }
        if (targetIndexes.isEmpty()) return false

        val nearIndexes = compactTokens.mapIndexedNotNull { index, token ->
            if (nearWords.any { word -> token.contains(word) || word.contains(token) }) index else null
        }
        if (nearIndexes.isEmpty()) return false

        for (ti in targetIndexes) {
            for (ni in nearIndexes) {
                if (kotlin.math.abs(ti - ni) <= safeDistance) return true
            }
        }
        return false
    }

    fun match(context: Context, sender: String, body: String): JSONObject? {
        val searchable = "$sender $body"
        val compactAllText = compact(searchable)
        val normalizedSender = normalizePhone(sender)
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
                    val number = normalizePhone(item.optString("number", ""))
                    if (number.isBlank() || normalizedSender.contains(number) || number.contains(normalizedSender)) {
                        senderOk = true
                        break
                    }
                }
            }
            if (!senderOk) continue

            val allKeywordsRaw = when {
                rule.has("allKeywords") -> rule.optString("allKeywords", "")
                else -> rule.optString("keywords", "")
            }
            val allTerms = terms(allKeywordsRaw)
            val allOk = allTerms.isEmpty() || allTerms.all { containsTerm(compactAllText, it) }
            if (!allOk) continue

            val anyTerms = terms(rule.optString("anyKeywords", ""))
            val anyOk = anyTerms.isEmpty() || anyTerms.any { containsTerm(compactAllText, it) }
            if (!anyOk) continue

            val notTerms = terms(rule.optString("notKeywords", ""))
            val notOk = notTerms.none { containsTerm(compactAllText, it) }
            if (!notOk) continue

            val nearTarget = rule.optString("nearTarget", "").trim()
            val nearKeywords = rule.optString("nearKeywords", "").trim()
            val nearDistance = rule.optInt("nearDistance", 3)
            val nearOk = nearMatch(body, nearTarget, nearKeywords, nearDistance)
            if (!nearOk) continue

            return rule
        }

        return null
    }

    fun senderNote(rule: JSONObject?, sender: String): String {
        if (rule == null) return ""
        val ns = normalizePhone(sender)
        val senders = rule.optJSONArray("senders") ?: return ""
        for (i in 0 until senders.length()) {
            val item = senders.optJSONObject(i) ?: continue
            val n = normalizePhone(item.optString("number", ""))
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
