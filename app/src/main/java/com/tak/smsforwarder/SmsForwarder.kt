package com.takpack.smsforwarder

import android.content.Context
import android.telephony.SmsManager

object SmsForwarder {
    fun send(context: Context, target: String, message: String): Pair<Boolean, String> {
        return try {
            val numbers = target.split(",", "،", "\n", ";", "؛")
                .map { it.trim().substringAfterLast("|").trim() }
                .filter { it.isNotBlank() }
            if (numbers.isEmpty()) return false to "شماره مقصد SMS خالی است"

            val smsManager = if (android.os.Build.VERSION.SDK_INT >= 23) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            for (number in numbers) {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            }

            true to "SMS ارسال شد"
        } catch (e: Exception) {
            false to (e.message ?: "خطای ارسال SMS")
        }
    }
}
