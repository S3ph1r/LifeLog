package com.example.lifelog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. Aggiungi la classe "Settings" alla lista delle entità del database.
// 2. Aumenta la versione del database a "2". Questo è OBBLIGATORIO quando cambi la struttura.
@Database(entities = [AudioSegment::class, Settings::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // 3. Dichiara la funzione astratta per ottenere il nuovo DAO.
    abstract fun settingsDao(): SettingsDao
    abstract fun audioSegmentDao(): AudioSegmentDao // La tua funzione esistente

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
                    // Poiché abbiamo aumentato la versione, dobbiamo dire a Room come gestire
                    // l'aggiornamento. "fallbackToDestructiveMigration" è la via più semplice:
                    // se trova un database vecchio, lo cancella e lo ricrea. Va bene per lo sviluppo
                    // e per la nostra situazione attuale.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}