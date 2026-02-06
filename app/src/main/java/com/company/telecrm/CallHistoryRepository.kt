package com.company.telecrm

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CallHistoryRepository(private val context: Context) {

    private val sharedPreferences = context.getSharedPreferences("call_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveCallHistory(callHistory: List<CallHistory>) {
        val json = gson.toJson(callHistory)
        sharedPreferences.edit().putString("call_history_list", json).apply()
    }

    fun saveCallHistory(item: CallHistory) {
        synchronized(this) {
            val currentList = getCallHistory().toMutableList()
            val index = currentList.indexOfFirst { it.callId == item.callId }
            if (index != -1) {
                currentList[index] = item
            } else {
                currentList.add(item)
            }
            saveCallHistory(currentList)
        }
    }

    fun getCallHistory(): List<CallHistory> {
        val json = sharedPreferences.getString("call_history_list", null)
        return if (json != null) {
            val type = object : TypeToken<List<CallHistory>>() {}.type
            val list: List<CallHistory> = gson.fromJson(json, type)
            // Ensure no null fields from old data
            list.map { 
                it.copy(
                    callId = it.callId ?: "",
                    recordingPath = it.recordingPath ?: ""
                )
            }
        } else {
            emptyList()
        }
    }
}