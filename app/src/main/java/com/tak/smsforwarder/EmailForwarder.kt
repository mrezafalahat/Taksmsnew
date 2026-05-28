package com.takpack.smsforwarder

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.Base64
import javax.net.ssl.SSLSocketFactory

object EmailForwarder {
    fun send(context: Context, toAddress: String, subject: String, body: String): Pair<Boolean, String> {
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

            val socket: Socket = if (port == 465) {
                SSLSocketFactory.getDefault().createSocket(host, port) as Socket
            } else {
                Socket(host, port)
            }

            socket.soTimeout = 20000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            fun readLine(): String = reader.readLine() ?: ""
            fun cmd(line: String): String {
                writer.write(line + "\r\n")
                writer.flush()
                return readLine()
            }

            readLine()
            cmd("EHLO android")
            cmd("AUTH LOGIN")
            cmd(Base64.getEncoder().encodeToString(username.toByteArray(Charsets.UTF_8)))
            val authResult = cmd(Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8)))
            if (!authResult.startsWith("235")) {
                socket.close()
                return false to "SMTP Authentication failed"
            }

            cmd("MAIL FROM:<$from>")
            val targets = toAddress.split(",", "،", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }
            for (t in targets) cmd("RCPT TO:<$t>")
            cmd("DATA")

            val safeSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.toByteArray(Charsets.UTF_8)) + "?="
            val msg = buildString {
                append("From: <$from>\r\n")
                append("To: ${targets.joinToString(",")}\r\n")
                append("Subject: $safeSubject\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/plain; charset=UTF-8\r\n")
                append("Content-Transfer-Encoding: base64\r\n")
                append("\r\n")
                append(Base64.getMimeEncoder().encodeToString(body.toByteArray(Charsets.UTF_8)))
                append("\r\n.\r\n")
            }

            writer.write(msg)
            writer.flush()
            val dataResult = readLine()
            cmd("QUIT")
            socket.close()

            if (dataResult.startsWith("250")) true to "Email ارسال شد" else false to dataResult
        } catch (e: Exception) {
            false to (e.message ?: "خطای ارسال Email")
        }
    }
}
