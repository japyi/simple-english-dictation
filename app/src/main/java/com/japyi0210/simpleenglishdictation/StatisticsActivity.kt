package com.japyi0210.simpleenglishdictation

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.japyi0210.simpleenglishdictation.data.AppDatabase
import kotlinx.coroutines.launch

class StatisticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        val totalView = findViewById<TextView>(R.id.text_total)
        val correctView = findViewById<TextView>(R.id.text_correct)
        val rateView = findViewById<TextView>(R.id.text_rate)

        val dao = AppDatabase.getDatabase(this).dictationDao()

        lifecycleScope.launch {
            val total = dao.getTotalCount()
            val correct = dao.getCorrectCount()
            val rate = if (total == 0) 0 else (correct * 100) / total

            totalView.text = "총 문제 수: $total"
            correctView.text = "정답 개수: $correct"
            rateView.text = "정답률: $rate%"
        }
    }
}
