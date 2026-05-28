package com.takpack.smsforwarder

import android.content.Context

object Forwarder {
    fun processIncomingSms(context: Context, sender: String, body: String, time: Long) {
        val rule = RuleMatcher.match(context, sender, body)

        // پیام‌های بدون فیلتر نباید در History / پیام‌ها لاگ شوند.
        if (rule == null) return

        val smsTarget = rule.optString("smsTarget", "").trim()
        val emailTarget = rule.optString("emailTarget", "").trim()

        val results = mutableListOf<String>()
        val errors = mutableListOf<String>()

        if (smsTarget.isNotEmpty()) {
            val smsResult = SmsForwarder.send(context, smsTarget, body)

            if (smsResult.first) {
                results.add(smsResult.second)
            } else {
                errors.add(smsResult.second)
            }
        }

        if (emailTarget.isNotEmpty()) {
            Thread {
                val emailResult = EmailForwarder.send(
                    context,
                    emailTarget,
                    "Forwarded SMS",
                    body
                )

                val finalResults = mutableListOf<String>()
                finalResults.addAll(results)

                val finalErrors = mutableListOf<String>()
                finalErrors.addAll(errors)

                if (emailResult.first) {
                    finalResults.add(emailResult.second)
                } else {
                    finalErrors.add(emailResult.second)
                }

                val status = when {
                    finalErrors.isNotEmpty() && finalResults.isEmpty() -> "خطای ارسال"
                    finalErrors.isNotEmpty() && finalResults.isNotEmpty() -> "ارسال ناقص"
                    finalResults.isNotEmpty() -> "ارسال موفق"
                    else -> "ذخیره شد"
                }

                DataStore.addMessage(
                    context = context,
                    sender = sender,
                    body = body,
                    time = time,
                    rule = rule,
                    status = status,
                    result = finalResults.joinToString(" | "),
                    error = finalErrors.joinToString(" | ")
                )
            }.start()

            return
        }

        val status = when {
            smsTarget.isEmpty() && emailTarget.isEmpty() -> "ذخیره شد"
            errors.isNotEmpty() && results.isEmpty() -> "خطای ارسال"
            errors.isNotEmpty() && results.isNotEmpty() -> "ارسال ناقص"
            results.isNotEmpty() -> "ارسال موفق"
            else -> "ذخیره شد"
        }

        DataStore.addMessage(
            context = context,
            sender = sender,
            body = body,
            time = time,
            rule = rule,
            status = status,
            result = results.joinToString(" | "),
            error = errors.joinToString(" | ")
        )
    }
}
