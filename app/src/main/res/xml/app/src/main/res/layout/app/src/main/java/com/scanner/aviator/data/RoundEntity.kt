package com.scanner.aviator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rounds")
data class RoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val multiplier: Double,
    val timestamp: Long = System.currentTimeMillis()
)
