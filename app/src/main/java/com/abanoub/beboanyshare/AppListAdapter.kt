package com.abanoub.beboanyshare

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abanoub.beboanyshare.databinding.ItemAppBinding
import com.bumptech.glide.Glide
import java.io.File
import java.util.Locale

data class AppItem(
    val info: ApplicationInfo,
    val name: String,
    val size: Long,
    val icon: Drawable?,
    var isSelected: Boolean = false
)

class AppListAdapter(private val onSelectionChanged: () -> Unit) : ListAdapter<AppItem, AppListAdapter.AppViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding, onSelectionChanged)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(
        private val binding: ItemAppBinding,
        private val onSelectionChanged: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(appItem: AppItem) {
            binding.tvAppName.text = appItem.name
            binding.tvAppSize.text = String.format(Locale.getDefault(), "%.2f MB", appItem.size / (1024.0 * 1024.0))
            Glide.with(binding.ivAppIcon.context).load(appItem.icon).into(binding.ivAppIcon)

            binding.cbSelect.setOnCheckedChangeListener(null)
            binding.cbSelect.isChecked = appItem.isSelected

            binding.root.setOnClickListener {
                appItem.isSelected = !appItem.isSelected
                binding.cbSelect.isChecked = appItem.isSelected
                onSelectionChanged()
            }
            binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                appItem.isSelected = isChecked
                onSelectionChanged()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem): Boolean = oldItem.info.packageName == newItem.info.packageName
        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem): Boolean = oldItem == newItem
    }
}
