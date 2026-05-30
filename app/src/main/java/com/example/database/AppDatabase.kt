package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [PresetMessage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kayflood_database"
                )
                .addCallback(DatabaseCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Prepopulate database in background
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = database.presetDao()
                        // Add some default advanced & funny spam templates
                        dao.insert(PresetMessage(title = "KayFlood Selamlama", text = "KayFlood Sistemi Aktif! 🚀", isSystemDefault = true))
                        dao.insert(PresetMessage(title = "Hızlı Gamer", text = "GG WP! Kolaydı 😎", isSystemDefault = true))
                        dao.insert(PresetMessage(title = "Hızlı Destek", text = "Yoldayım, kuleyi savun! 🛡️⚡", isSystemDefault = true))
                        dao.insert(PresetMessage(title = "Komik Taciz", text = "E hani oynamayı biliyordun? 😜", isSystemDefault = true))
                        dao.insert(PresetMessage(title = "Yazıyor...", text = "Yazıyor... 💬 (Şaka şaka, KayFlood attı!)", isSystemDefault = true))
                        dao.insert(PresetMessage(title = "Emoji Yağmuru", text = "🔥⚡💥🚀🔥⚡💥🚀🔥", isSystemDefault = true))
                    }
                }
            }
        }
    }
}
