package com.tak.smsforwarder

import android.util.Log

object EmailForwarder {

    private const val TAG = "EmailForwarder"

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

            // فعلاً برای جلوگیری از خطای Build
            // منطق واقعی ارسال ایمیل بعداً اینجا وصل می‌شود.

        } catch (e: Exception) {
            Log.e(TAG, "Email forward failed", e)
        }
    }
}
