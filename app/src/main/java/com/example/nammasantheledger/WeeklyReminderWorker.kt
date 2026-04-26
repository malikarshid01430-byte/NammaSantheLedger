package com.example.nammasantheledger

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.nammasantheledger.data.AppDatabase

class WeeklyReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val allTransactions = db.transactionDao().getAll()

            // Group transactions by customer name
            val customerGroups = allTransactions.groupBy { it.customerName }

            // Get shop name for the message
            val prefs    = applicationContext.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
            val shopName = prefs.getString("shopName", "Namma Santhe") ?: "Namma Santhe"

            customerGroups.forEach { (name, txList) ->
                val phone = txList.firstOrNull { it.phoneNumber.isNotEmpty() }?.phoneNumber
                val total = txList.sumOf { it.amount }

                if (!phone.isNullOrEmpty()) {
                    val message = "Dear $name, your total credit amount at $shopName " +
                            "is ₹${String.format("%.2f", total)}. " +
                            "Kindly clear your dues. Thank you!"
                    sendSms(phone, message)
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun sendSms(phone: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applicationContext.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, message, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}