package com.swiftshare

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class HistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SwiftShareHistory", Context.MODE_PRIVATE)

    fun addRecord(record: TransferRecord) {
        val records = getRecords().toMutableList()
        records.add(0, record) // Add to top
        saveRecords(records)
    }

    fun getRecords(): List<TransferRecord> {
        val jsonString = prefs.getString("records", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val records = mutableListOf<TransferRecord>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            records.add(
                TransferRecord(
                    id = obj.getString("id"),
                    fileName = obj.getString("fileName"),
                    filePath = obj.getString("filePath"),
                    fileSize = obj.getLong("fileSize"),
                    timestamp = obj.getLong("timestamp"),
                    isIncoming = obj.getBoolean("isIncoming"),
                    isSuccess = obj.getBoolean("isSuccess")
                )
            )
        }
        return records
    }

    private fun saveRecords(records: List<TransferRecord>) {
        val jsonArray = JSONArray()
        for (record in records) {
            val obj = JSONObject()
            obj.put("id", record.id)
            obj.put("fileName", record.fileName)
            obj.put("filePath", record.filePath)
            obj.put("fileSize", record.fileSize)
            obj.put("timestamp", record.timestamp)
            obj.put("isIncoming", record.isIncoming)
            obj.put("isSuccess", record.isSuccess)
            jsonArray.put(obj)
        }
        prefs.edit().putString("records", jsonArray.toString()).apply()
    }
}
