package com.example.lifelog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// MODIFICA: La versione del database è ora 3
@Database(entities = [AudioSegment::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun audioSegmentDao(): AudioSegmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migrazione dalla versione 1 alla 2 (già presente)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_segments ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE audio_segments ADD COLUMN longitude REAL")
            }
        }

        // --- NUOVA MIGRAZIONE ---
        /**
         * Migrazione dalla versione 2 alla 3.
         * Aggiunge la colonna 'isVoiceprint' di tipo INTEGER (booleano in SQLite),
         * non può essere nulla (NOT NULL) e ha un valore di default di 0 (false).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_segments ADD COLUMN isVoiceprint INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifelog_database"
                )
                    // MODIFICA: Aggiungiamo la nuova migrazione alla catena.
                    // Room le eseguirà in ordine se necessario.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}