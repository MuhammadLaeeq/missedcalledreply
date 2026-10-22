package com.dss.missedcall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Missed call ka logic:
 * RINGING -> (OFFHOOK nahi hua) -> IDLE  = missed call
 * RINGING -> OFFHOOK -> IDLE              = call pick hui, kuch nahi bhejna
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Android ye broadcast 2 baar bhejta hai, number wale ko save karo
                if (!number.isNullOrBlank()) Prefs.setRingingNumber(context, number)
                if (Prefs.lastState(context) == "IDLE") Prefs.setAnswered(context, false)
                Prefs.setLastState(context, "RINGING")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (Prefs.lastState(context) == "RINGING") Prefs.setAnswered(context, true)
                Prefs.setLastState(context, "OFFHOOK")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val wasRinging = Prefs.lastState(context) == "RINGING"
                val answered = Prefs.wasAnswered(context)
                val caller = number?.takeIf { it.isNotBlank() } ?: Prefs.ringingNumber(context)

                Prefs.setLastState(context, "IDLE")
                Prefs.setRingingNumber(context, null)

                if (wasRinging && !answered && !caller.isNullOrBlank()) {
                    onMissedCall(context, caller)
                }
            }
        }
    }

    private fun onMissedCall(context: Context, rawNumber: String) {
        if (!Prefs.enabled(context)) return

        val number = normalize(rawNumber)
        if (number == null) {
            Prefs.log(context, "Skipped: number samajh nahi aaya ($rawNumber)")
            return
        }

        if (Prefs.skipContacts(context) && isSavedContact(context, rawNumber)) {
            Prefs.log(context, "Skipped (saved contact): +$number")
            return
        }

        val cooldownMs = Prefs.cooldownHours(context) * 60L * 60L * 1000L
        if (System.currentTimeMillis() - Prefs.lastSentAt(context, number) < cooldownMs) {
            Prefs.log(context, "Skipped (pehle hi message ja chuka): +$number")
            return
        }

        Prefs.log(context, "Missed call: +$number")

        if (Prefs.smsFallback(context)) sendSms(context, rawNumber)

        // WhatsApp wala kaam Accessibility service karegi
        Prefs.enqueue(context, number)
        AutoSendService.instance?.processQueue()
            ?: Prefs.log(context, "Accessibility service band hai! Settings mein on karo.")
    }

    private fun sendSms(context: Context, number: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) return
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sms.divideMessage(Prefs.message(context))
            sms.sendMultipartTextMessage(number, null, parts, null, null)
            Prefs.log(context, "SMS bheja: $number")
        } catch (e: Exception) {
            Prefs.log(context, "SMS fail: ${e.message}")
        }
    }

    private fun isSavedContact(context: Context, number: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return false
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        return try {
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) { false }
    }

    companion object {
        /**
         * WhatsApp ko country code ke saath sirf digits chahiye.
         * Canada/US: 6471234567 -> 16471234567
         */
        fun normalize(raw: String): String? {
            val digits = raw.filter { it.isDigit() }
            return when {
                digits.length == 10 -> "1$digits"
                digits.length == 11 && digits.startsWith("1") -> digits
                digits.length in 11..15 -> digits   // international, pehle se country code
                else -> null                        // private / short numbers
            }
        }
    }
}
