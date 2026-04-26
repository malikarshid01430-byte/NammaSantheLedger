package com.example.nammasantheledger.data

import androidx.room.*

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY id DESC")
    fun getAll(): List<Transaction>

    // Get unique customers who have phone numbers (for weekly reminders)
    @Query("SELECT * FROM transactions WHERE phoneNumber != '' GROUP BY customerName")
    fun getCustomersWithPhone(): List<Transaction>

    @Insert
    fun insert(transaction: Transaction)

    @Delete
    fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions")
    fun deleteAll()
}