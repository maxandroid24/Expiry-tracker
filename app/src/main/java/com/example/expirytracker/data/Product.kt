package com.example.expirytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    /** Manufacturing date as epoch millis at UTC midnight. */
    val manufacturingDate: Long,
    /** Expiry date as epoch millis at UTC midnight. */
    val expiryDate: Long,
    /** Optional absolute path to the product image stored in app files. */
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
