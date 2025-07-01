package com.japyi0210.simpleenglishdictation

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError

class ScenarioSelectActivity : AppCompatActivity() {

    data class Scenario(val name: String, val fileKey: String, val category: String)

    private var mInterstitialAd: InterstitialAd? = null

    private lateinit var categorySpinner: Spinner
    private lateinit var orderSpinner: Spinner
    private lateinit var listView: ListView

    private val orderOptions = listOf("순서대로 문장 듣기", "무작위로 문장 듣기")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scenario_select)

        MobileAds.initialize(this) {}
        loadInterstitialAd()

        listView = findViewById(R.id.listViewScenarios)
        categorySpinner = findViewById(R.id.categorySpinner)
        orderSpinner = findViewById(R.id.orderSpinner)

        val allScenarios = mutableListOf(
            Scenario("무작위 시나리오 듣기 (Random Play)", "all", "전체")
        ) + loadScenarios()

        val categories = allScenarios.map { it.category }.distinct().sorted()
        val categoryOptions = listOf("전체") + categories.filter { it != "전체" }

        val categoryAdapter = ArrayAdapter(this, R.layout.font_list_item, categoryOptions)
        categoryAdapter.setDropDownViewResource(R.layout.font_list_item)
        categorySpinner.adapter = categoryAdapter

        val orderAdapter = ArrayAdapter(this, R.layout.font_list_item, orderOptions)
        orderAdapter.setDropDownViewResource(R.layout.font_list_item)
        orderSpinner.adapter = orderAdapter

        updateList(allScenarios)

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View, position: Int, id: Long) {
                val selectedCategory = categoryOptions[position]
                val filtered = if (selectedCategory == "전체") {
                    allScenarios
                } else {
                    allScenarios.filter { it.category == selectedCategory }
                }
                updateList(filtered)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.selectedItemId = R.id.nav_menu

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_menu -> true
                R.id.nav_dictation -> {
                    Toast.makeText(this, "시나리오를 선택하세요.", Toast.LENGTH_SHORT).show()
                    false
                }
                R.id.ai_review -> {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) startActivity(Intent(this, StartActivity::class.java))
                    else startActivity(Intent(this, AiReviewActivity::class.java))
                    true
                }
                R.id.nav_account -> {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) {
                        val intent = Intent(this, StartActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                    } else {
                        startActivity(Intent(this, StatisticsChartActivity::class.java))
                    }
                    true
                }
                R.id.nav_exit -> {
                    val versionName = try {
                        packageManager.getPackageInfo(packageName, 0).versionName
                    } catch (e: Exception) {
                        "알 수 없음"
                    }

                    AlertDialog.Builder(this)
                        .setTitle("앱 종료")
                        .setMessage("""
                            정말 종료하시겠습니까?

                            📦 버전: $versionName
                            📧 문의: CREN-J (japyi0210@gmail.com)
                        """.trimIndent())
                        .setPositiveButton("예") { _, _ -> showAdOrExit() }
                        .setNegativeButton("아니오", null)
                        .show()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateList(filteredScenarios: List<Scenario>) {
        val adapter = ScenarioAdapter(this, filteredScenarios)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = filteredScenarios[position]
            val order = orderOptions[orderSpinner.selectedItemPosition]

            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("scenario_key", selected.fileKey)
            intent.putExtra("order_mode", order)
            startActivity(intent)
        }
    }

    private fun loadScenarios(): List<Scenario> {
        return try {
            val inputStream = assets.open("scenarios.txt")
            inputStream.bufferedReader().readLines().mapNotNull {
                val parts = it.split("\t")
                if (parts.size >= 3) Scenario(parts[0].trim(), parts[1].trim(), parts[2].trim()) else null
            }
        } catch (e: Exception) {
            Toast.makeText(this, "시나리오 목록을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
            emptyList()
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-1872760638277957/9712274803", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    mInterstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: com.google.android.gms.ads.LoadAdError) {
                    mInterstitialAd = null
                }
            })
    }

    private fun showAdOrExit() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    FirebaseAuth.getInstance().signOut()
                    finishAffinity()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    FirebaseAuth.getInstance().signOut()
                    finishAffinity()
                }
            }
            mInterstitialAd?.show(this)
        } else {
            FirebaseAuth.getInstance().signOut()
            finishAffinity()
        }
    }
}
