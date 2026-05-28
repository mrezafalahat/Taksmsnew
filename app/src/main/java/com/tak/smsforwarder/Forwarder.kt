package com.tak.smsforwarder

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

object Forwarder {

    private const val TAG = "Forwarder"

    fun send(
        context: Context,
        phoneNumber: String,
        message: String
    ) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)

            smsManager.sendMultipartTextMessage(
                phoneNumber,
                null,
                parts,
                null,
                null
            )

            Log.d(TAG, "SMS sent to: $phoneNumber")

        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed", e)
        }
    }
}
