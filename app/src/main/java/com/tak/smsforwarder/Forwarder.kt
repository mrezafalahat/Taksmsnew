package com.takpack.smsforwarder

import android.content.Context
import org.json.JSONObject

object Forwarder {
    fun processIncomingSms(context: Context, sender: String, body: String, time: Long) {
        val rule = RuleMatcher.match(context, sender, body)

        if (rule == null) {
            DataStore.addMessage(context, sender, body, time, null, "ذخیره شد", "")
            return
        }

        val type = rule.optString("forwardType", "history")
        val message = buildForwardText(sender, body, rule)

        when (type) {
            "sms" -> {
                val target = rule.optString("smsTarget", "")
                val result = SmsForwarder.send(context, target, message)
                DataStore.addMessage(context, sender, body, time, rule, if (result.first) "ارسال SMS موفق" else "خطای SMS", result.second)
            }
            "email" -> {
                val target = rule.optString("emailTarget", "")
                Thread {
                    val result = EmailForwarder.send(context, target, "Forwarded SMS - ${rule.optString("name", "")}", message)
                    DataStore.addMessage(context, sender, body, time, rule, if (result.first) "ارسال Email موفق" else "خطای Email", result.second)
                }.start()
            }
            else -> {
                DataStore.addMessage(context, sender, body, time, rule, "فقط ذخیره شد", "")
            }
        }
    }

    fun buildForwardText(sender: String, body: String, rule: JSONObject): String {
        return """
            SMS Forwarded

            Rule: ${rule.optString("name", "بدون نام")}
            Sender: $sender

            Message:
            $body
        """.trimIndent()
    }
}
