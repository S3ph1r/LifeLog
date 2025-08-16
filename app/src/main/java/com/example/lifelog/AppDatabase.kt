package com.example.lifelog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. Rimuovi "Settings::class", aggiungi "User::class"
// 2. La versione rimane 2 (o incrementala a 3 se preferisci)
@Database(entities = [AudioSegment::class, User::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun audioSegmentDao(): AudioSegmentDao
    abstract fun userDao(): UserDao // 3. Aggiungi il nuovo UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifelog_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}