package com.example.lifelog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    /**
     * Inserisce le impostazioni o le aggiorna se esistono già.
     * OnConflictStrategy.REPLACE assicura che la riga con id=1 venga semplicemente sovrascritta.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: Settings)

    /**
     * Recupera le impostazioni correnti.
     * Restituisce un oggetto Settings? (nullable) perché al primo avvio la tabella potrebbe essere vuota.
     */
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettings(): Settings?

    /**
     * Recupera le impostazioni come un Flow, per permettere alla UI di osservare i cambiamenti
     * in tempo reale senza dover interrogare continuamente il database.
     */
    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettingsFlow(): Flow<Settings?>
}