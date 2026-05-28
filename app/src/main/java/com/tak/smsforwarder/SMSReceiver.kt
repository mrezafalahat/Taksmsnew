package com.tak.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager

class SMSReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val sender = messages.first().displayOriginatingAddress ?: ""
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val rules = RuleStore.findMatchingRules(context, sender, body)
        if (rules.isEmpty()) {
            RuleStore.addHistory(context, false, sender, "", "بدون فیلتر", body, "هیچ فیلتری مطابق نبود")
            return
        }
        for (rule in rules) {
            val finalText = if (rule.suffix.trim().isBlank()) body else "$body ${rule.suffix.trim()}"
            val targets = RuleStore.splitTargets(rule.target)
            for (target in targets) {
                try {
                    val sms = SmsManager.getDefault()
                    sms.sendMultipartTextMessage(target, null, sms.divideMessage(finalText), null, null)
                    RuleStore.addHistory(context, true, sender, target, rule.name, finalText, "Success")
                } catch (e: Exception) {
                    RuleStore.addHistory(context, false, sender, target, rule.name, finalText, e.message ?: "Send failed")
                }
            }
        }
    }
}
