package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM preset_messages ORDER BY id DESC")
    fun getAllPresets(): Flow<List<PresetMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: PresetMessage)

    @Update
    suspend fun update(preset: PresetMessage)

    @Delete
    suspend fun delete(preset: PresetMessage)

    @Query("DELETE FROM preset_messages WHERE isSystemDefault = 0")
    suspend fun deleteAllUserPresets()
}
