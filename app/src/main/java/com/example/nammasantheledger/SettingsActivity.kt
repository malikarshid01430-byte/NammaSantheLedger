package com.example.nammasantheledger

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.title = "⚙️ Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)

        val dailyReminderToggle  = findViewById<Switch>(R.id.dailyReminderSwitch)
        val reminderTimeBtn      = findViewById<Button>(R.id.reminderTimeBtn)
        val weeklyReminderToggle = findViewById<Switch>(R.id.weeklyReminderSwitch)
        val changePinBtn         = findViewById<Button>(R.id.changePinBtn)

        // Load saved states
        dailyReminderToggle.isChecked  = prefs.getBoolean("dailyEnabled", false)
        weeklyReminderToggle.isChecked = prefs.getBoolean("weeklyEnabled", false)

        val savedHour   = prefs.getInt("hour", 20)
        val savedMinute = prefs.getInt("minute", 0)
        reminderTimeBtn.text = formatTime(savedHour, savedMinute)

        // ── Daily Reminder Toggle ────────────────────────────────────────────
        dailyReminderToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dailyEnabled", isChecked).apply()
            if (isChecked) {
                setDailyReminder(prefs.getInt("hour", 20), prefs.getInt("minute", 0))
                Toast.makeText(this, "🔔 Daily reminder ON", Toast.LENGTH_SHORT).show()
            } else {
                cancelDailyReminder()
                Toast.makeText(this, "Daily reminder OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // ── Pick Reminder Time ───────────────────────────────────────────────
        reminderTimeBtn.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                prefs.edit().putInt("hour", hour).putInt("minute", minute).apply()
                reminderTimeBtn.text = formatTime(hour, minute)
                if (dailyReminderToggle.isChecked) setDailyReminder(hour, minute)
                Toast.makeText(this, "⏰ Time set to ${formatTime(hour, minute)}", Toast.LENGTH_SHORT).show()
            }, savedHour, savedMinute, false).show()
        }

        // ── Weekly Customer SMS Reminder ─────────────────────────────────────
        weeklyReminderToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("weeklyEnabled", isChecked).apply()
            if (isChecked) {
                scheduleWeeklyReminder()
                Toast.makeText(this, "📲 Weekly SMS reminders ON", Toast.LENGTH_LONG).show()
            } else {
                cancelWeeklyReminder()
                Toast.makeText(this, "Weekly SMS reminders OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // ── Change PIN ───────────────────────────────────────────────────────
        changePinBtn.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Enter new 4-digit PIN"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle("🔑 Change PIN")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newPin = input.text.toString().trim()
                    if (newPin.length != 4) {
                        Toast.makeText(this, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                    } else {
                        getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                            .edit().putString("pin", newPin).apply()
                        Toast.makeText(this, "✅ PIN changed!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val amPm    = if (hour < 12) "AM" else "PM"
        val display = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        return "${String.format("%02d:%02d", display, minute)} $amPm"
    }

    private fun setDailyReminder(hour: Int, minute: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent       = Intent(this, ReminderReceiver::class.java)
        val pending      = PendingIntent.getBroadcast(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending
        )
    }

    private fun cancelDailyReminder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent       = Intent(this, ReminderReceiver::class.java)
        val pending      = PendingIntent.getBroadcast(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(pending)
    }

    private fun scheduleWeeklyReminder() {
        val request = PeriodicWorkRequestBuilder<WeeklyReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weekly_customer_reminder",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancelWeeklyReminder() {
        WorkManager.getInstance(this).cancelUniqueWork("weekly_customer_reminder")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}