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

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        fun bind(segment: AudioSegment) {
            val dateString = dateFormat.format(Date(segment.timestamp))
            binding.textViewFileName.text = dateString

            val file = File(segment.filePath)
            if (file.exists()) {
                val sizeInKb = file.length() / 1024
                if (sizeInKb < 1024) {
                    binding.textViewFileSize.text = "$sizeInKb KB"
                } else {
                    val sizeInMb = sizeInKb / 1024.0
                    binding.textViewFileSize.text = String.format(Locale.US, "%.2f MB", sizeInMb)
                }
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