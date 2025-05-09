package com.japyi0210.simpleenglishdictation.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictation_records")
data class DictationRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sentence: String,
    val userInput: String,
    val similarity: Int,
    val correct: Boolean
)
