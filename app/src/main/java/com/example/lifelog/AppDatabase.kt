package com.example.lifelog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// --- MODIFICA 1: Versione incrementata a 2 ---
@Database(entities = [AudioSegment::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun audioSegmentDao(): AudioSegmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // --- MODIFICA 2: Creazione dell'oggetto Migration ---
        /**
         * Migrazione dalla versione 1 alla 2.
         * Aggiunge le colonne 'latitude' e 'longitude' alla tabella 'audio_segments'.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_segments ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE audio_segments ADD COLUMN longitude REAL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifelog_database"
                )
                    // --- MODIFICA 3: Aggiungiamo la migrazione al builder ---
                    .addMigrations(MIGRATION_1_2)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}