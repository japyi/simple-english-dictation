package com.japyi0210.simpleenglishdictation

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class ScenarioSelectActivity : AppCompatActivity() {

    data class Scenario(val name: String, val fileKey: String, val category: String, val imageFileName: String)

    private var mInterstitialAd: InterstitialAd? = null

    private lateinit var categorySpinner: Spinner
    private lateinit var orderSpinner: Spinner
    private lateinit var listView: ListView

    private val orderOptions = listOf("순서대로 문장 듣기", "무작위로 문장 듣기")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scenario_select)

        showWeeklyRankingDialog()

        MobileAds.initialize(this) {}
        loadInterstitialAd()

        listView = findViewById(R.id.listViewScenarios)
        categorySpinner = findViewById(R.id.categorySpinner)
        orderSpinner = findViewById(R.id.orderSpinner)

        val allScenarios = mutableListOf(
            Scenario("무작위 문장 듣기", "all", "전체", "all.webp")
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
                        .setTitle("저장 및 종료")
                        .setMessage(
                            """
                            학습기록을 저장하고 종료하시겠습니까?

                            📦 버전: $versionName
                            📧 문의: CREN-J (japyi0210@gmail.com)
                            """.trimIndent()
                        )
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
        val prefs = getSharedPreferences("UsedSentences", Context.MODE_PRIVATE)
        val allScenarioKeys = loadScenarios().map { it.fileKey }

        val itemsWithProgress = filteredScenarios.map { scenario ->
            val totalCount = getTotalSentenceCount(scenario.fileKey)

            val usedCount = if (scenario.fileKey == "all") {
                allScenarioKeys.sumOf { key ->
                    prefs.getStringSet("used_$key", emptySet())?.size ?: 0
                }
            } else {
                prefs.getStringSet("used_${scenario.fileKey}", emptySet())?.size ?: 0
            }

            val progressText = " ($usedCount / $totalCount)"
            Scenario(
                name = scenario.name + progressText,
                fileKey = scenario.fileKey,
                category = scenario.category,
                imageFileName = scenario.imageFileName
            )
        }

        val adapter = ScenarioAdapter(this, itemsWithProgress)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = filteredScenarios[position]
            val userOrderSelection = orderOptions[orderSpinner.selectedItemPosition]
            val internalOrderMode = if (userOrderSelection.contains("순서")) "순서" else "랜덤"

            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("scenario_key", selected.fileKey)
            intent.putExtra("order_mode", internalOrderMode)
            intent.putExtra("image_file_name", selected.imageFileName)
            startActivity(intent)
        }
    }

    private fun loadScenarios(): List<Scenario> {
        return try {
            val inputStream = assets.open("scenarios.txt")
            inputStream.bufferedReader().readLines().mapNotNull {
                val parts = it.split("\t")
                if (parts.size >= 4) {
                    Scenario(
                        name = parts[0].trim(),
                        fileKey = parts[1].trim(),
                        category = parts[2].trim(),
                        imageFileName = parts[3].trim()
                    )
                } else null
            }
        } catch (e: Exception) {
            Toast.makeText(this, "시나리오 목록을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
            emptyList()
        }
    }

    private fun getTotalSentenceCount(fileKey: String): Int {
        return try {
            val assetManager = assets
            val lines = if (fileKey == "all") {
                val files = assetManager.list("")?.filter {
                    it.startsWith("scenario_") && it.endsWith(".txt")
                } ?: emptyList()
                files.flatMap { file ->
                    try {
                        assetManager.open(file).bufferedReader().readLines()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            } else {
                val fileName = "scenario_${fileKey}.txt"
                assetManager.open(fileName).bufferedReader().readLines()
            }

            lines.count { it.contains("\t") }
        } catch (e: Exception) {
            0
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-1872760638277957/9712274803", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    mInterstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
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

    private fun showWeeklyRankingDialog() {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser
        val weekId = getCurrentWeekId()

        val messageView = TextView(this).apply {
            text = "불러오는 중..."
            setPadding(50, 40, 50, 20)
            textSize = 15f
            setLineSpacing(0f, 1.3f)
        }

        val noticeTextView = TextView(this).apply {
            text = ""
            setPadding(50, 20, 50, 40)
            textSize = 15f
            setLineSpacing(0f, 1.2f)
        }

        val scrollView = ScrollView(this).apply {
            val layout = LinearLayout(this@ScenarioSelectActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(messageView)
                addView(noticeTextView)
            }
            addView(layout)
        }

        // ⬇️ 타이틀을 커스텀 View로 대체
        val titleView = TextView(this).apply {
            text = "🏆 실시간 랭킹 TOP 5"
            setPadding(50, 50, 50, 30)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setView(scrollView)
            .setPositiveButton("확인", null)
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.show()

        // 🔹 공지사항
        db.collection("notices").document("weekly_notice").get()
            .addOnSuccessListener { doc ->
                val msg = doc.getString("message") ?: ""
                if (msg.isNotBlank()) {
                    val noticeText = "\n\n$msg"
                    val spannable = android.text.SpannableString(noticeText).apply {
                        setSpan(android.text.style.RelativeSizeSpan(0.85f), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(android.text.style.ForegroundColorSpan(0xFF888888.toInt()), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    noticeTextView.text = spannable
                }
            }
            .addOnFailureListener {
                noticeTextView.text = ""
            }

        // 🔹 주간 랭킹
        db.collection("weekly_rankings")
            .document(weekId)
            .collection("users")
            .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { result ->
                val rankListBuilder = StringBuilder()
                var previousScore: Long? = null
                var currentRank = 0
                var actualIndex = 0

                for (doc in result) {
                    actualIndex++
                    val score = doc.getLong("score") ?: 0
                    if (score != previousScore) {
                        currentRank = actualIndex
                        previousScore = score
                    }
                    val name = maskEmail(doc.getString("name") ?: "익명")
                    rankListBuilder.append("${currentRank}위: $name (${score}문장)\n")
                }

                val rankList = rankListBuilder.toString().ifEmpty { "아직 랭킹 데이터가 없습니다." }

                val afterTextLoad: (String) -> Unit = { footnote ->
                    val fullText = "$rankList\n$footnote"
                    val spannable = android.text.SpannableString(fullText).apply {
                        setSpan(android.text.style.RelativeSizeSpan(0.85f), rankList.length, fullText.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), rankList.length, fullText.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(android.text.style.ForegroundColorSpan(0xFF888888.toInt()), rankList.length, fullText.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, rankList.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    messageView.text = spannable
                }

                if (currentUser != null) {
                    db.collection("weekly_rankings")
                        .document(weekId)
                        .collection("users")
                        .document(currentUser.uid)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val myScore = userDoc.getLong("score") ?: 0
                            val footnote = listOf(
                                "※ 50% 이상 일치한 답변만 순위에 반영됩니다.",
                                "※ 순위는 매주 월요일 자정에 초기화됩니다.",
                                "※ 이번 주에 총 ${myScore}문장을 푸셨습니다!"
                            ).joinToString("\n")
                            afterTextLoad(footnote)
                        }
                } else {
                    val footnote = listOf(
                        "※ 50% 이상 일치한 답변만 순위에 반영됩니다.",
                        "※ 순위는 매주 월요일 자정에 초기화됩니다.",
                        "※ 로그인하면 이번 주 기록을 확인할 수 있습니다."
                    ).joinToString("\n")
                    afterTextLoad(footnote)
                }
            }
            .addOnFailureListener {
                messageView.text = "랭킹 정보를 불러오지 못했습니다."
            }
    }

    private fun getCurrentWeekId(): String {
        val cal = Calendar.getInstance()
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        return String.format("%04d-W%02d", year, week)
    }

    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return email
        val id = parts[0]
        val domain = parts[1]
        val prefix = if (id.length <= 3) id else id.substring(0, 3)
        return "$prefix*****@$domain"
    }
}
