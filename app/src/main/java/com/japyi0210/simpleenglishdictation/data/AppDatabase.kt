package com.japyi0210.simpleenglishdictation.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DictationRecord::class], version = 3) // ← 꼭 올려야 함
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictationDao(): DictationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dictation_db"
                )
                    .fallbackToDestructiveMigration() // 🚨 이 줄 추가
                    .build().also { INSTANCE = it }
            }
        }
    }
}
