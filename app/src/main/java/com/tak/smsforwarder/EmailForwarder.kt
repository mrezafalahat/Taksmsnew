package com.takpack.smsforwarder

import android.content.Context
import android.util.Base64
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

object EmailForwarder {

    fun send(context: Context, toAddress: String, subject: String, body: String): Pair<Boolean, String> {
        var socket: Socket? = null

        return try {
            val settings = DataStore.getSettingsObject(context)

            val host = settings.optString("smtpHost", "").trim().lowercase()
            val port = settings.optString("smtpPort", "465").trim().toIntOrNull() ?: 465
            val username = settings.optString("smtpUsername", "").trim()
            val password = settings.optString("smtpPassword", "").replace(" ", "").trim()
            val from = settings.optString("smtpFrom", username).trim().ifBlank { username }

            if (host.isBlank()) return false to "SMTP Host تنظیم نشده"
            if (username.isBlank()) return false to "SMTP Username تنظیم نشده"
            if (password.isBlank()) return false to "SMTP Password تنظیم نشده"
            if (toAddress.isBlank()) return false to "ایمیل مقصد خالی است"

            socket = if (port == 465) {
                SSLSocketFactory.getDefault().createSocket(host, port) as Socket
            } else {
                Socket(host, port)
            }

            socket.soTimeout = 30000

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            fun readResponse(): String {
                val lines = mutableListOf<String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    lines.add(line)
                    if (line.length >= 4 && line[3] == ' ') break
                    if (line.length < 4) break
                }
                return lines.joinToString("\n")
            }

            fun cmd(command: String): String {
                writer.write(command + "\r\n")
                writer.flush()
                return readResponse()
            }

            fun b64(text: String): String =
                Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            val hello = readResponse()
            if (!hello.startsWith("220")) return false to "SMTP اتصال برقرار نشد: $hello"

            val ehlo = cmd("EHLO android")
            if (!ehlo.startsWith("250")) return false to "EHLO خطا: $ehlo"

            val auth = cmd("AUTH LOGIN")
            if (!auth.startsWith("334")) return false to "AUTH شروع نشد: $auth"

            val userResp = cmd(b64(username))
            if (!userResp.startsWith("334")) return false to "Username پذیرفته نشد: $userResp"

            val passResp = cmd(b64(password))
            if (!passResp.startsWith("235")) return false to "رمز یا App Password اشتباه است: $passResp"

            val mailFrom = cmd("MAIL FROM:<$from>")
            if (!mailFrom.startsWith("250")) return false to "MAIL FROM خطا: $mailFrom"

            val targets = toAddress
                .split(",", "،", ";", "\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            for (target in targets) {
                val rcpt = cmd("RCPT TO:<$target>")
                if (!rcpt.startsWith("250") && !rcpt.startsWith("251")) {
                    return false to "ایمیل مقصد پذیرفته نشد: $rcpt"
                }
            }

            val data = cmd("DATA")
            if (!data.startsWith("354")) return false to "DATA خطا: $data"

            val safeSubject = "=?UTF-8?B?${b64(subject)}?="
            val encodedBody = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.DEFAULT)

            val message = buildString {
                append("From: <$from>\r\n")
                append("To: ${targets.joinToString(",")}\r\n")
                append("Subject: $safeSubject\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/plain; charset=UTF-8\r\n")
                append("Content-Transfer-Encoding: base64\r\n")
                append("\r\n")
                append(encodedBody)
                append("\r\n.\r\n")
            }

            writer.write(message)
            writer.flush()

            val finalResp = readResponse()
            cmd("QUIT")

            if (finalResp.startsWith("250")) {
                true to "Email ارسال شد"
            } else {
                false to "ارسال نشد: $finalResp"
            }
        } catch (e: Exception) {
            false to "خطای ارسال Email: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
