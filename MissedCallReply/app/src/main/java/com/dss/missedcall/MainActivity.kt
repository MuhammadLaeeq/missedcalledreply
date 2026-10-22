package com.dss.missedcall

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var swEnabled: Switch
    private lateinit var etMessage: EditText
    private lateinit var rbBusiness: RadioButton
    private lateinit var rbNormal: RadioButton
    private lateinit var swContacts: Switch
    private lateinit var swSms: Switch
    private lateinit var etCooldown: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swEnabled = findViewById(R.id.swEnabled)
        etMessage = findViewById(R.id.etMessage)
        rbBusiness = findViewById(R.id.rbBusiness)
        rbNormal = findViewById(R.id.rbNormal)
        swContacts = findViewById(R.id.swContacts)
        swSms = findViewById(R.id.swSms)
        etCooldown = findViewById(R.id.etCooldown)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)

        swEnabled.isChecked = Prefs.enabled(this)
        etMessage.setText(Prefs.message(this))
        rbBusiness.isChecked = Prefs.useBusiness(this)
        rbNormal.isChecked = !Prefs.useBusiness(this)
        swContacts.isChecked = Prefs.skipContacts(this)
        swSms.isChecked = Prefs.smsFallback(this)
        etCooldown.setText(Prefs.cooldownHours(this).toString())

        findViewById<Button>(R.id.btnPermissions).setOnClickListener { askPermissions() }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { askBattery() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { testSend() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val service = AutoSendService.instance != null
        val perms = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        ).all { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val battery = pm.isIgnoringBatteryOptimizations(packageName)

        tvStatus.text = buildString {
            append(if (perms) "✅ Call permissions\n" else "❌ Call permissions (Step 1)\n")
            append(if (service) "✅ Accessibility service on\n" else "❌ Accessibility service off (Step 2)\n")
            append(if (battery) "✅ Battery optimization off" else "⚠️ Battery optimization on (Step 3)")
        }
        tvLog.text = Prefs.readLog(this).ifBlank { "Abhi koi activity nahi." }
    }

    private fun askPermissions() {
        val list = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS
        )
        ActivityCompat.requestPermissions(this, list.toTypedArray(), 1)
    }

    private fun askBattery() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun save() {
        val msg = etMessage.text.toString().trim()
        if (msg.isEmpty()) {
            Toast.makeText(this, "Message khali nahi ho sakta", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.save(
            this,
            enabled = swEnabled.isChecked,
            message = msg,
            business = rbBusiness.isChecked,
            skipContacts = swContacts.isChecked,
            sms = swSms.isChecked,
            cooldown = etCooldown.text.toString().toIntOrNull() ?: 12
        )
        Toast.makeText(this, "Save ho gaya", Toast.LENGTH_SHORT).show()
    }

    /** Apne hi kisi doosre number par test message bhejne ke liye */
    private fun testSend() {
        save()
        val input = EditText(this).apply { hint = "e.g. 6471234567" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Test number (WhatsApp wala)")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val n = CallReceiver.normalize(input.text.toString())
                if (n == null) {
                    Toast.makeText(this, "Number theek nahi", Toast.LENGTH_SHORT).show()
                } else if (AutoSendService.instance == null) {
                    Toast.makeText(this, "Pehle Accessibility on karo", Toast.LENGTH_LONG).show()
                } else {
                    Prefs.enqueue(this, n)
                    AutoSendService.instance?.processQueue()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
