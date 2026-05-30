package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preset_messages")
data class PresetMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val text: String,
    val isSystemDefault: Boolean = false
)
