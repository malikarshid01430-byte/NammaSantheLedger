package com.example.nammasantheledger

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.nammasantheledger.data.AppDatabase
import com.example.nammasantheledger.data.Transaction
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class TransactionAdapter(context: Context, private val items: List<Transaction>) :
    ArrayAdapter<Transaction>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_transaction, parent, false)
        val item = items[position]
        view.findViewById<TextView>(R.id.avatarText).text =
            item.customerName.first().uppercaseChar().toString()
        view.findViewById<TextView>(R.id.customerNameText).text = item.customerName
        view.findViewById<TextView>(R.id.phoneNumberText).text =
            if (item.phoneNumber.isEmpty()) "No phone" else item.phoneNumber
        view.findViewById<TextView>(R.id.timestampText).text = item.timestamp
        view.findViewById<TextView>(R.id.amountText).text =
            "₹${String.format("%.2f", item.amount)}"
        return view
    }
}

class MainActivity : AppCompatActivity() {

    private val transactions = ArrayList<Transaction>()
    private var adapter: TransactionAdapter? = null
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var db: AppDatabase
    private lateinit var totalTransactionsTv: TextView
    private lateinit var totalAmountTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getDatabase(this)

        val customerNameInput = findViewById<EditText>(R.id.customerName)
        val phoneInput        = findViewById<EditText>(R.id.phoneNumber)
        val amountInput       = findViewById<EditText>(R.id.amount)
        val addButton         = findViewById<Button>(R.id.addButton)
        val historyList       = findViewById<ListView>(R.id.historyList)
        totalTransactionsTv   = findViewById(R.id.totalTransactions)
        totalAmountTv         = findViewById(R.id.totalAmount)
        val dateText          = findViewById<TextView>(R.id.dateText)
        val clearAll          = findViewById<TextView>(R.id.clearAll)

        // Load profile shop name into header
        val profilePrefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        val shopNameTv   = findViewById<TextView>(R.id.shopNameHeader)
        shopNameTv.text  = profilePrefs.getString("shopName", "Namma Santhe Ledger") ?: "Namma Santhe Ledger"

        dateText.text = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date())

        loadTransactions()
        adapter = TransactionAdapter(this, transactions)
        historyList.adapter = adapter

        // ── Add Transaction ──────────────────────────────────────────────────
        addButton.setOnClickListener {
            val name   = customerNameInput.text.toString().trim()
            val phone  = phoneInput.text.toString().trim()
            val amtStr = amountInput.text.toString().trim()

            if (name.isEmpty() || amtStr.isEmpty()) {
                Toast.makeText(this, "Name and amount are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amtStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val timestamp   = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            val transaction = Transaction(customerName = name, phoneNumber = phone, amount = amount, timestamp = timestamp)

            executor.execute {
                db.transactionDao().insert(transaction)
                val all = db.transactionDao().getAll()
                runOnUiThread {
                    transactions.clear()
                    transactions.addAll(all)
                    adapter!!.notifyDataSetChanged()
                    updateSummary()
                    customerNameInput.setText("")
                    phoneInput.setText("")
                    amountInput.setText("")
                    Toast.makeText(this, "✅ Transaction saved!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ── Long Press to Delete ─────────────────────────────────────────────
        historyList.setOnItemLongClickListener { _, _, position, _ ->
            val t = transactions[position]
            AlertDialog.Builder(this)
                .setTitle("🗑️ Delete Transaction")
                .setMessage("Delete ${t.customerName}'s entry of ₹${String.format("%.2f", t.amount)}?")
                .setPositiveButton("Delete") { _, _ ->
                    executor.execute {
                        db.transactionDao().delete(t)
                        val all = db.transactionDao().getAll()
                        runOnUiThread {
                            transactions.clear()
                            transactions.addAll(all)
                            adapter!!.notifyDataSetChanged()
                            updateSummary()
                            Toast.makeText(this, "Deleted!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        // ── Clear All ────────────────────────────────────────────────────────
        clearAll.setOnClickListener {
            if (transactions.isEmpty()) {
                Toast.makeText(this, "Nothing to clear", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("⚠️ Clear All")
                .setMessage("Delete all ${transactions.size} transactions?")
                .setPositiveButton("Clear All") { _, _ ->
                    executor.execute {
                        db.transactionDao().deleteAll()
                        runOnUiThread {
                            transactions.clear()
                            adapter!!.notifyDataSetChanged()
                            updateSummary()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh shop name if profile was updated
        val profilePrefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        findViewById<TextView>(R.id.shopNameHeader).text =
            profilePrefs.getString("shopName", "Namma Santhe Ledger")
    }

    // ── Options Menu (3-dot) ─────────────────────────────────────────────────
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_logout -> {
                AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Logout") { _, _ ->
                        getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("isLoggedIn", false).apply()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadTransactions() {
        executor.execute {
            val all = db.transactionDao().getAll()
            runOnUiThread {
                transactions.clear()
                transactions.addAll(all)
                adapter?.notifyDataSetChanged()
                updateSummary()
            }
        }
    }

    private fun updateSummary() {
        totalTransactionsTv.text = transactions.size.toString()
        totalAmountTv.text = "₹${String.format("%.2f", transactions.sumOf { it.amount })}"
    }
}