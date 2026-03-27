package com.swiftshare

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.swiftshare.databinding.FragmentHistoryBinding
import java.io.File

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HistoryAdapter
    private lateinit var historyManager: HistoryManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyManager = HistoryManager(requireContext())
        setupRecyclerView()
        loadHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter { record ->
            openFile(record.filePath)
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun loadHistory() {
        val records = historyManager.getRecords()
        adapter.submitList(records)
    }

    private fun openFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, getMimeType(file.absolutePath))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return if (extension != null) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"
        } else {
            "*/*"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
