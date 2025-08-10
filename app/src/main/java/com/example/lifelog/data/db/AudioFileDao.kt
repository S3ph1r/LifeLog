// Percorso: app/src/main/java/com/example/lifelog/data/db/AudioFileDao.kt

package com.example.lifelog.data.db

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * DAO (Data Access Object) per l'entità AudioFileEntity.
 *
 * Questa interfaccia definisce tutte le operazioni di database (lettura, scrittura,
 * aggiornamento, eliminazione) per la tabella "audio_files". Room genererà
 * l'implementazione concreta di questa interfaccia.
 */
@Dao
interface AudioFileDao {

    /**
     * Inserisce un nuovo record di file audio nel database.
     * Se un record con lo stesso id esiste già, viene sostituito.
     * La funzione è 'suspend' per essere eseguita in una coroutine.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: AudioFileEntity)

    /**
     * Aggiorna un record esistente. Room usa la chiave primaria (id)
     * per trovare il record da aggiornare.
     */
    @Update
    suspend fun update(file: AudioFileEntity)

    /**
     * Elimina un record dal database usando il suo id.
     */
    @Query("DELETE FROM audio_files WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Recupera una lista di file che hanno uno stato specifico.
     * Sarà usato dal nostro UploaderWorker per trovare i file da caricare.
     * È una funzione 'suspend' perché non abbiamo bisogno di osservarla costantemente.
     */
    @Query("SELECT * FROM audio_files WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getFilesWithStatus(status: AudioFileStatus): List<AudioFileEntity>

    /**
     * Recupera un singolo file dal suo id.
     * Utile quando il worker deve operare su un file specifico.
     */
    @Query("SELECT * FROM audio_files WHERE id = :id")
    suspend fun getFileById(id: Long): AudioFileEntity?

    /**
     *  --- LA FUNZIONE PIÙ IMPORTANTE PER LA UI ---
     * Recupera tutti i file con stato PENDING_UPLOAD come LiveData.
     * LiveData è un contenitore di dati osservabile e lifecycle-aware.
     * Room aggiornerà automaticamente il contenuto di questo LiveData ogni volta
     * che i dati nella tabella 'audio_files' cambiano in un modo che influisce
     * su questa query. Questo aggiornerà la nostra UI in modo reattivo e automatico.
     */
    @Query("SELECT * FROM audio_files WHERE status = 'PENDING_UPLOAD' ORDER BY createdAt DESC")
    fun getPendingFilesForUploadLiveData(): LiveData<List<AudioFileEntity>>
}