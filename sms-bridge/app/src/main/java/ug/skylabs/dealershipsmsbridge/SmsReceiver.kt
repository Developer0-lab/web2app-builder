package ug.skylabs.dealershipsmsbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        Thread {
            try {
                val prefs = context.getSharedPreferences("bridge", Context.MODE_PRIVATE)
                val endpoint = prefs.getString("endpoint", "") ?: ""
                val key = prefs.getString("key", "") ?: ""
                if (endpoint.isBlank() || key.isBlank()) return@Thread
                for (sms in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    val body = sms.messageBody ?: continue
                    val parsed = parse(body, sms.timestampMillis, sms.originatingAddress ?: "") ?: continue
                    if (upload(endpoint, key, parsed)) {
                        val p = context.getSharedPreferences("bridge", Context.MODE_PRIVATE)
                        p.edit().putInt("sent", p.getInt("sent", 0) + 1).apply()
                    } else {
                        val p = context.getSharedPreferences("bridge", Context.MODE_PRIVATE)
                        p.edit().putInt("failed", p.getInt("failed", 0) + 1).apply()
                    }
                }
            } finally { pending.finish() }
        }.start()
    }

    data class Payment(val transactionId: String, val amount: Long, val timestamp: Long, val sender: String, val provider: String)

    private fun parse(body: String, timestamp: Long, sender: String): Payment? {
        val lower = body.lowercase(Locale.US)
        val paymentWords = listOf("transaction", "txn", "received", "payment", "airtel money", "mtn momo", "mobile money", "ugx", "shs")
        if (paymentWords.none { lower.contains(it) }) return null
        val id = Regex("(?i)(?:transaction\\s*(?:id|number|no)?|txn)\\s*[:#-]?\\s*([A-Z0-9-]{6,40})").find(body)?.groupValues?.get(1)
            ?: Regex("(?i)\\b([A-Z0-9]{8,24})\\b").find(body)?.groupValues?.get(1)
            ?: return null
        val amountText = Regex("(?i)(?:UGX|Ugx|Shs\\.?)[ ]*([0-9,]+)").find(body)?.groupValues?.get(1)
            ?: Regex("(?i)([0-9][0-9,]{2,})[ ]*(?:UGX|Ugx|Shs\\.?)").find(body)?.groupValues?.get(1)
            ?: return null
        val amount = amountText.replace(",", "").toLongOrNull() ?: return null
        val provider = when {
            lower.contains("airtel") || sender.lowercase(Locale.US).contains("airtel") -> "airtel"
            lower.contains("mtn") || lower.contains("momo") || sender.lowercase(Locale.US).contains("mtn") -> "mtn"
            else -> "unknown"
        }
        return Payment(id, amount, timestamp, sender, provider)
    }

    private fun upload(endpoint: String, key: String, p: Payment): Boolean {
        return try {
            val json = """{"transaction_id":"${escape(p.transactionId)}","amount_ugx":${p.amount},"sms_timestamp":${p.timestamp},"sender":"${escape(p.sender)}","provider":"${p.provider}"}"""
            val c = URL(endpoint).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-bridge-key", key)
            c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            c.responseCode in 200..299
        } catch (_: Exception) { false }
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
