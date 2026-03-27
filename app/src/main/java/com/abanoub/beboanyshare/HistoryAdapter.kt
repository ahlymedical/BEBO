package com.abanoub.beboanyshare

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abanoub.beboanyshare.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TransferRecord(
    val id: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val timestamp: Long,
    val isIncoming: Boolean,
    val isSuccess: Boolean
)

class HistoryAdapter(
    private val onItemClick: (TransferRecord) -> Unit
) : ListAdapter<TransferRecord, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(
        private val binding: ItemHistoryBinding,
        private val onItemClick: (TransferRecord) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: TransferRecord) {
            binding.tvFileName.text = record.fileName

            val sizeMb = record.fileSize / (1024.0 * 1024.0)
            val sizeStr = String.format(Locale.getDefault(), "%.2f MB", sizeMb)

            val date = Date(record.timestamp)
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val dateStr = format.format(date)

            val direction = if (record.isIncoming) "Received" else "Sent"
            val status = if (record.isSuccess) "Success" else "Failed"

            binding.tvFileDetails.text = "$sizeStr • $dateStr • $direction • $status"

            binding.root.setOnClickListener {
                onItemClick(record)
            }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<TransferRecord>() {
        override fun areItemsTheSame(oldItem: TransferRecord, newItem: TransferRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TransferRecord, newItem: TransferRecord): Boolean {
            return oldItem == newItem
        }
    }
}
