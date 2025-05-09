package com.japyi0210.simpleenglishdictation.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DictationDao {

    @Insert
    suspend fun insert(record: DictationRecord)

    @Query("SELECT COUNT(*) FROM dictation_records")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM dictation_records WHERE correct = 1")
    suspend fun getCorrectCount(): Int

    @Query("DELETE FROM dictation_records")
    suspend fun clearAll()
}
