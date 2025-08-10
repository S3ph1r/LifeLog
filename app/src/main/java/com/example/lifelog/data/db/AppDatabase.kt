// Percorso: app/src/main/java/com/example/lifelog/data/db/AppDatabase.kt

package com.example.lifelog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * La classe principale del database per l'applicazione, che segue il pattern Singleton.
 *
 * @Database: Annotazione che definisce la configurazione del database.
 * - entities: Elenca tutte le classi Entity che appartengono a questo database.
 * - version: La versione del database. Deve essere incrementata ogni volta che
 *            si modifica lo schema (es. aggiungendo una colonna a una tabella).
 * - exportSchema: Indica se esportare lo schema in un file JSON. Buona pratica.
 */
@Database(
    entities = [AudioFileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Metodo astratto che Room implementerà per fornirci un'istanza del nostro DAO.
     * Attraverso questo metodo accederemo a tutte le operazioni di database.
     */
    abstract fun audioFileDao(): AudioFileDao

    /**
     * Companion object per implementare il pattern Singleton, assicurando che esista
     * una sola istanza del database in tutta l'applicazione, evitando problemi di
     * accesso concorrente e spreco di risorse.
     */
    companion object {
        // La parola chiave 'volatile' garantisce che il valore di INSTANCE sia
        // sempre aggiornato e visibile a tutti i thread.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Metodo per ottenere l'istanza Singleton del database.
         * Se l'istanza non esiste, la crea in un blocco synchronized per
         * garantire la thread-safety.
         */
        fun getInstance(context: Context): AppDatabase {
            // Se l'istanza esiste già, la restituisce.
            // Altrimenti, crea il database.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifelog_app.db" // Nome del file del database sul dispositivo
                )
                    // In un'app di produzione, qui si gestirebbero le migrazioni.
                    // .addMigrations(MIGRATION_1_2, ...)
                    .build()

                INSTANCE = instance
                // Restituisce l'istanza appena creata
                instance
            }
        }
    }
}
