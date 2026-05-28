package com.tak.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import org.json.JSONArray

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (sms in messages) {
            val sender = sms.originatingAddress ?: ""
            val body = sms.messageBody ?: ""

            Log.d("SmsReceiver", "SMS from: $sender / $body")

            handleSms(context, sender, body)
        }
    }

    private fun handleSms(context: Context, sender: String, body: String) {
        try {
            val rulesText = RuleStore.getAll(context)
            val rules = JSONArray(rulesText)

            for (i in 0 until rules.length()) {
                val rule = rules.getJSONObject(i)

                val enabled = rule.optBoolean("enabled", true)
                if (!enabled) continue

                val senderValue = rule.optString("senderValue", "")
                val messageValue = rule.optString("messageValue", "")
                val forwardTo = rule.optString("forwardTo", "")

                val senderMatched =
                    senderValue.isBlank() || sender.contains(senderValue, ignoreCase = true)

                val messageMatched =
                    messageValue.isBlank() || body.contains(messageValue, ignoreCase = true)

                if (senderMatched && messageMatched && forwardTo.isNotBlank()) {
                    val forwardedText = "From: $sender\n\n$body"
                    Forwarder.send(context, forwardTo, forwardedText)
                }
            }

        } catch (e: Exception) {
            Log.e("SmsReceiver", "SMS processing failed", e)
        }
    }
}
