package com.example.smarttodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.TypeConverter
import androidx.room.TypeConverters
import org.json.JSONArray
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromSubTaskList(value: List<SubTaskItem>): String {
        val jsonArray = JSONArray()
        value.forEach { item ->
            val jsonObject = JSONObject()
            jsonObject.put("content", item.content)
            jsonObject.put("isDone", item.isDone)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toSubTaskList(value: String): List<SubTaskItem> {
        val list = mutableListOf<SubTaskItem>()
        try {
            val jsonArray = JSONArray(value)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                list.add(
                    SubTaskItem(
                        content = jsonObject.getString("content"),
                        isDone = jsonObject.optBoolean("isDone", false)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}

@Database(entities = [RawMessage::class, SmartTask::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_todo_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
