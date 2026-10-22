package com.dss.missedcall

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.URLEncoder

/**
 * Ye service:
 * 1. WhatsApp chat caller ke number aur message ke saath kholti hai
 * 2. Send button dhoond kar dabati hai
 * 3. Wapas home screen par aa jati hai
 *
 * Phone lock ho to number queue mein rehta hai aur unlock hote hi bhej diya jata hai.
 */
class AutoSendService : AccessibilityService() {

    companion object {
        @Volatile var instance: AutoSendService? = null
        private const val TIMEOUT_MS = 12_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentNumber: String? = null
    private var sentClicked = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { processQueue() }
    }

    override fun onServiceConnected() {
        instance = this
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        Prefs.log(this, "Service on")
        processQueue()
    }

    override fun onDestroy() {
        instance = null
        try { unregisterReceiver(unlockReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onInterrupt() {}

    /** Queue ka agla number uthao aur WhatsApp kholo */
    fun processQueue() {
        handler.post {
            if (currentNumber != null) return@post            // ek waqt mein ek hi
            val queue = Prefs.queue(this)
            if (queue.isEmpty()) return@post

            val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            if (km.isKeyguardLocked) {
                Prefs.log(this, "Phone lock hai, unlock hone par bhejenge (${queue.size})")
                return@post
            }

            val number = queue.removeAt(0)
            Prefs.setQueue(this, queue)
            openChat(number)
        }
    }

    private fun openChat(number: String) {
        val pkg = if (Prefs.useBusiness(this)) "com.whatsapp.w4b" else "com.whatsapp"
        val text = URLEncoder.encode(Prefs.message(this), "UTF-8")
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://api.whatsapp.com/send?phone=$number&text=$text")
        ).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        try {
            currentNumber = number
            sentClicked = false
            startActivity(intent)
            // Agar 12 second mein send na ho (number WhatsApp par nahi), to chhod do
            handler.postDelayed({ finish(success = false) }, TIMEOUT_MS)
        } catch (e: Exception) {
            Prefs.log(this, "WhatsApp nahi khula ($pkg installed hai?)")
            currentNumber = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val number = currentNumber ?: return
        if (sentClicked) return
        val root = rootInActiveWindow ?: return

        val send = findSendButton(root)
        if (send != null) {
            val clicked = clickNodeOrParent(send)
            if (clicked) {
                sentClicked = true
                Prefs.markSent(this, number)
                Prefs.log(this, "WhatsApp message bheja: +$number")
                handler.removeCallbacksAndMessages(null)
                // Message chala jaye phir wapas home
                handler.postDelayed({ finish(success = true) }, 1500)
            }
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1) View ID se (sabse pakka)
        for (id in listOf("com.whatsapp.w4b:id/send", "com.whatsapp:id/send")) {
            root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.let { return it }
        }
        // 2) "Send" description se (ID badal jaye to backup)
        root.findAccessibilityNodeInfosByText("Send")?.firstOrNull {
            it.contentDescription?.toString().equals("Send", ignoreCase = true)
        }?.let { return it }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(3) {
            if (n?.isClickable == true) return n!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n?.parent
        }
        return false
    }

    private fun finish(success: Boolean) {
        val number = currentNumber ?: return
        if (!success && !sentClicked) {
            Prefs.log(this, "Nahi gaya (shayad WhatsApp par nahi): +$number")
        }
        currentNumber = null
        handler.removeCallbacksAndMessages(null)
        performGlobalAction(GLOBAL_ACTION_HOME)
        // Agla number agar queue mein hai
        handler.postDelayed({ processQueue() }, 2000)
    }
}
