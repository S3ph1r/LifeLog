package com.example.lifelog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lifelog.databinding.ListItemAudioSegmentBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioSegmentAdapter : ListAdapter<AudioSegment, AudioSegmentAdapter.AudioSegmentViewHolder>(AudioSegmentDiffCallback()) {

    class AudioSegmentViewHolder(private val binding: ListItemAudioSegmentBinding) : RecyclerView.ViewHolder(binding.root) {

        // Non abbiamo più bisogno del dateFormat qui dentro
        // private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        fun bind(segment: AudioSegment) {
            val file = File(segment.filePath)

            // --- MODIFICA 1: VISUALIZZA IL NOME REALE DEL FILE ---
            // Estraiamo il nome del file dal percorso completo.
            // Il nome del file ora contiene già il timestamp e le coordinate.
            binding.textViewFileName.text = file.name

            // --- MODIFICA 2: VISUALIZZA LA DIMENSIONE IN MB ---
            if (file.exists()) {
                // Calcoliamo la dimensione in byte
                val sizeInBytes = file.length()
                // Convertiamo direttamente i byte in megabyte (1 MB = 1024 * 1024 bytes)
                val sizeInMb = sizeInBytes / (1024.0 * 1024.0)

                // Formattiamo la stringa per mostrare sempre i MB con due cifre decimali
                binding.textViewFileSize.text = String.format(Locale.US, "%.2f MB", sizeInMb)
            } else {
                binding.textViewFileSize.text = "File non trovato"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioSegmentViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemAudioSegmentBinding.inflate(inflater, parent, false)
        return AudioSegmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AudioSegmentViewHolder, position: Int) {
        val segment = getItem(position)
        holder.bind(segment)
    }
}

class AudioSegmentDiffCallback : DiffUtil.ItemCallback<AudioSegment>() {
    override fun areItemsTheSame(oldItem: AudioSegment, newItem: AudioSegment): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: AudioSegment, newItem: AudioSegment): Boolean {
        return oldItem == newItem
    }
}