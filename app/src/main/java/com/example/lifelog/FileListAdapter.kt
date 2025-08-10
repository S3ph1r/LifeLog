package com.example.lifelog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

// L'adapter ora lavora con una lista di oggetti RecordingFile, non più semplici String.
class FileListAdapter : ListAdapter<RecordingFile, FileListAdapter.FileViewHolder>(FileDiffCallback()) {

    /**
     * ViewHolder: Mantiene i riferimenti alle View di un singolo elemento della lista.
     * In questo modo, evitiamo di chiamare findViewById() ripetutamente.
     */
    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Riferimenti ai due TextView nel nostro layout list_item_file.xml
        private val fileNameTextView: TextView = itemView.findViewById(R.id.textViewFileName)
        private val fileSizeTextView: TextView = itemView.findViewById(R.id.textViewFileSize)

        /**
         * Collega i dati di un oggetto RecordingFile alle View corrispondenti.
         */
        fun bind(file: RecordingFile) {
            fileNameTextView.text = file.name
            // Formattiamo la stringa per mostrare la dimensione e l'unità di misura.
            fileSizeTextView.text = "${file.sizeInKb} KB"
        }
    }

    /**
     * Chiamato dalla RecyclerView quando ha bisogno di creare un nuovo ViewHolder
     * (cioè, una nuova riga per la lista).
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_file, parent, false)
        return FileViewHolder(view)
    }

    /**
     * Chiamato dalla RecyclerView per visualizzare i dati nella posizione specificata.
     * Prende l'oggetto corretto dalla lista e lo passa al metodo bind del ViewHolder.
     */
    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

/**
 * DiffUtil: Calcola le differenze tra la vecchia e la nuova lista per animare
 * solo gli elementi che sono cambiati. Questo rende la RecyclerView molto efficiente.
 */
class FileDiffCallback : DiffUtil.ItemCallback<RecordingFile>() {
    // Controlla se due item rappresentano lo stesso oggetto (es. hanno lo stesso ID).
    // Il nome del file è un buon ID univoco nel nostro caso.
    override fun areItemsTheSame(oldItem: RecordingFile, newItem: RecordingFile): Boolean {
        return oldItem.name == newItem.name
    }

    // Controlla se i dati di un item sono cambiati.
    // Poiché RecordingFile è una data class, il confronto `==` controlla
    // automaticamente tutti i campi (nome e dimensione).
    override fun areContentsTheSame(oldItem: RecordingFile, newItem: RecordingFile): Boolean {
        return oldItem == newItem
    }
}