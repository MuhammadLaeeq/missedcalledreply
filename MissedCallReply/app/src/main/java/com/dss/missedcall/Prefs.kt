package com.dss.missedcall

import android.content.Context

/** App ki saari settings aur pending queue yahan save hoti hai. */
object Prefs {
    private const val FILE = "mcr_prefs"

    const val DEFAULT_MESSAGE =
        "Hi! Thank you for calling Diamond Star Driving School. " +
        "We are probably on a lesson right now and will call you back as soon as possible.\n\n" +
        "Check our packages and prices here:\nhttps://diamondstardriving.ca/"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun message(c: Context) = sp(c).getString("message", DEFAULT_MESSAGE) ?: DEFAULT_MESSAGE
    fun useBusiness(c: Context) = sp(c).getBoolean("business", true)
    fun skipContacts(c: Context) = sp(c).getBoolean("skip_contacts", true)
    fun smsFallback(c: Context) = sp(c).getBoolean("sms_fallback", false)
    /** Ek hi number ko itne ghante tak dobara message nahi jayega */
    fun cooldownHours(c: Context) = sp(c).getInt("cooldown", 12)

    fun save(
        c: Context, enabled: Boolean, message: String, business: Boolean,
        skipContacts: Boolean, sms: Boolean, cooldown: Int
    ) {
        sp(c).edit()
            .putBoolean("enabled", enabled)
            .putString("message", message)
            .putBoolean("business", business)
            .putBoolean("skip_contacts", skipContacts)
            .putBoolean("sms_fallback", sms)
            .putInt("cooldown", cooldown)
            .apply()
    }

    // ---- call state tracking (receiver ke beech yaad rakhne ke liye) ----
    fun lastState(c: Context) = sp(c).getString("last_state", "IDLE") ?: "IDLE"
    fun setLastState(c: Context, s: String) = sp(c).edit().putString("last_state", s).apply()
    fun wasAnswered(c: Context) = sp(c).getBoolean("answered", false)
    fun setAnswered(c: Context, v: Boolean) = sp(c).edit().putBoolean("answered", v).apply()
    fun ringingNumber(c: Context) = sp(c).getString("ring_num", null)
    fun setRingingNumber(c: Context, n: String?) = sp(c).edit().putString("ring_num", n).apply()

    // ---- cooldown ----
    fun lastSentAt(c: Context, number: String) = sp(c).getLong("sent_$number", 0L)
    fun markSent(c: Context, number: String) =
        sp(c).edit().putLong("sent_$number", System.currentTimeMillis()).apply()

    // ---- queue: jab phone lock ho to number yahan ruk jata hai ----
    fun queue(c: Context): MutableList<String> =
        (sp(c).getString("queue", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    fun setQueue(c: Context, list: List<String>) =
        sp(c).edit().putString("queue", list.distinct().joinToString(",")).apply()

    fun enqueue(c: Context, number: String) {
        val q = queue(c)
        if (!q.contains(number)) q.add(number)
        setQueue(c, q)
    }

    // ---- simple log jo app ki screen par dikhta hai ----
    fun log(c: Context, line: String) {
        val time = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US)
            .format(java.util.Date())
        val old = sp(c).getString("log", "") ?: ""
        val updated = ("$time  $line\n$old").lines().take(30).joinToString("\n")
        sp(c).edit().putString("log", updated).apply()
    }
    fun readLog(c: Context) = sp(c).getString("log", "") ?: ""
}
