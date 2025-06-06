package com.japyi0210.simpleenglishdictation

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ScenarioSelectActivity : AppCompatActivity() {

    data class Scenario(val name: String, val fileKey: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scenario_select)

        val listView = findViewById<ListView>(R.id.listViewScenarios)

        val scenarios = mutableListOf(
            Scenario("🎲 랜덤으로 듣기 (Random Play)", "all")
        ) + loadScenarios()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, scenarios.map { it.name })
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedScenario = scenarios[position]
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("scenario_key", selectedScenario.fileKey)
            startActivity(intent)
        }
    }

    private fun loadScenarios(): List<Scenario> {
        return try {
            val inputStream = assets.open("scenarios.txt")
            inputStream.bufferedReader().readLines().mapNotNull {
                val parts = it.split("\t")
                if (parts.size == 2) Scenario(parts[0].trim(), parts[1].trim()) else null
            }
        } catch (e: Exception) {
            Toast.makeText(this, "시나리오 목록을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
            emptyList()
        }
    }
}
