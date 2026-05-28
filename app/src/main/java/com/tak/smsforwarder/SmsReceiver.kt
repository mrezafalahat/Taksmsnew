package com.tak.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!DataStore.isEnabled(context)) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        var sender = ""
        val body = StringBuilder()
        var time = System.currentTimeMillis()

        for (msg in messages) {
            sender = msg.displayOriginatingAddress ?: ""
            body.append(msg.messageBody ?: "")
            time = msg.timestampMillis
        }

        Forwarder.processIncomingSms(context, sender, body.toString(), time)
    }
}
