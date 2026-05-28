package com.tak.smsforwarder

import android.content.Context
import android.util.Log

object EmailForwarder {

    private const val TAG = "EmailForwarder"

    fun forward(
        context: Context,
        toEmail: String,
        subject: String,
        body: String
    ) {
        try {
            val safeSubject = subject.ifBlank { "SMS Forwarded" }
            val safeBody = body.ifBlank { "Empty SMS" }

            Log.d(TAG, "Forward email to: $toEmail")
            Log.d(TAG, "Subject: $safeSubject")
            Log.d(TAG, "Body: $safeBody")

            // فعلاً فقط برای اینکه Build درست شود.
            // ارسال واقعی ایمیل بعداً اینجا وصل می‌شود.

        } catch (e: Exception) {
            Log.e(TAG, "Email forwarding failed", e)
        }
    }

    fun forwardEmail(
        toEmail: String,
        sender: String,
        message: String,
        subject: String = "SMS Forwarded"
    ) {
        val safeSubject = subject.ifBlank { "SMS Forwarded" }

        try {
            Log.d(TAG, "Forward email to: $toEmail")
            Log.d(TAG, "Subject: $safeSubject")
            Log.d(TAG, "Sender: $sender")
            Log.d(TAG, "Message: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Email forward failed", e)
        }
    }
}
