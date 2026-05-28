package com.tak.smsforwarder

import android.content.Context
import android.util.Base64
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

object EmailForwarder {

    fun send(
        context: Context,
        toAddress: String,
        subject: String,
        body: String
    ): Pair<Boolean, String> {
        return try {
            val settings = DataStore.getSettingsObject(context)
            val host = settings.optString("smtpHost", "").trim()
            val port = settings.optString("smtpPort", "465").trim().toIntOrNull() ?: 465
            val username = settings.optString("smtpUsername", "").trim()
            val password = settings.optString("smtpPassword", "").trim()
            val from = settings.optString("smtpFrom", username).trim().ifBlank { username }

            if (host.isBlank()) return false to "SMTP Host تنظیم نشده"
            if (username.isBlank()) return false to "SMTP Username تنظیم نشده"
            if (password.isBlank()) return false to "SMTP Password تنظیم نشده"
            if (toAddress.isBlank()) return false to "ایمیل مقصد خالی است"

            val targets = toAddress
                .split(Regex("[\\r\\n,;]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (targets.isEmpty()) return false to "ایمیل مقصد خالی است"

            val socket: Socket = if (port == 465) {
                SSLSocketFactory.getDefault().createSocket(host, port) as Socket
            } else {
                Socket(host, port)
            }

            socket.soTimeout = 20000

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            fun readResponse(): String {
                val first = reader.readLine() ?: ""
                val code = if (first.length >= 3) first.substring(0, 3) else ""
                if (first.length >= 4 && first[3] == '-') {
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("$code ")) break
                    }
                }
                return first
            }

            fun cmd(line: String): String {
                writer.write(line + "\r\n")
                writer.flush()
                return readResponse()
            }

            readResponse()
            cmd("EHLO android")
            cmd("AUTH LOGIN")
            cmd(username.toBase64())
            val authResult = cmd(password.toBase64())

            if (!authResult.startsWith("235")) {
                socket.close()
                return false to "SMTP Authentication failed"
            }

            cmd("MAIL FROM:<$from>")
            for (target in targets) {
                cmd("RCPT TO:<$target>")
            }
            cmd("DATA")

            val safeSubject = "=?UTF-8?B?${subject.toBase64()}?="
            val safeBody = body.toBase64Mime()

            val message = buildString {
                append("From: <$from>\r\n")
                append("To: ${targets.joinToString(",")}\r\n")
                append("Subject: $safeSubject\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/plain; charset=UTF-8\r\n")
                append("Content-Transfer-Encoding: base64\r\n")
                append("\r\n")
                append(safeBody)
                append("\r\n.\r\n")
            }

            writer.write(message)
            writer.flush()

            val dataResult = readResponse()
            cmd("QUIT")
            socket.close()

            if (dataResult.startsWith("250")) {
                true to "Email ارسال شد"
            } else {
                false to dataResult.ifBlank { "خطای ارسال Email" }
            }
        } catch (e: Exception) {
            false to (e.message ?: "خطای ارسال Email")
        }
    }

    private fun String.toBase64(): String {
        return Base64.encodeToString(toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun String.toBase64Mime(): String {
        return Base64.encodeToString(toByteArray(Charsets.UTF_8), Base64.DEFAULT).trim()
    }
}
