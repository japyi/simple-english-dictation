package com.japyi0210.simpleenglishdictation

import androidx.core.content.res.ResourcesCompat
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.text.SimpleDateFormat
import java.util.*

class StatisticsChartActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var lineChart: LineChart
    private lateinit var monthlyBarChart: BarChart
    private lateinit var monthlyLineChart: LineChart
    private lateinit var replayDayChart: LineChart
    private lateinit var replayMonthChart: LineChart
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var googleSignInClient: GoogleSignInClient
    private var mInterstitialAd: InterstitialAd? = null
    private lateinit var layoutDaily: LinearLayout
    private lateinit var layoutMonthly: LinearLayout
    private lateinit var layoutWeekly: LinearLayout

    companion object {
        // 일별
        val COLOR_DAILY_BAR = Color.parseColor("#AECBFA")      // 연핑크
        val COLOR_DAILY_LINE = Color.parseColor("#AECBFA")     // 초록
        val COLOR_DAILY_REPLAY = Color.parseColor("#AECBFA")   // 보라

        // 주별
        val COLOR_WEEKLY_BAR = Color.parseColor("#FFD8A8")     // 주황
        val COLOR_WEEKLY_LINE = Color.parseColor("#FFD8A8")    // 초록 (같게 유지)
        val COLOR_WEEKLY_REPLAY = Color.parseColor("#FFD8A8")  // 보라 (같게 유지)

        // 월별
        val COLOR_MONTHLY_BAR = Color.parseColor("#D1C4E9")    // 연핑크 (일별과 같음)
        val COLOR_MONTHLY_LINE = Color.parseColor("#D1C4E9")   // 초록
        val COLOR_MONTHLY_REPLAY = Color.parseColor("#D1C4E9") // 보라
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics_chart)

        MobileAds.initialize(this) {}
        loadInterstitialAd()

        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        tabLayout.addTab(tabLayout.newTab().setText("일별"))
        tabLayout.addTab(tabLayout.newTab().setText("주별"))
        tabLayout.addTab(tabLayout.newTab().setText("월별"))

        layoutDaily = findViewById(R.id.layout_daily)
        layoutWeekly = findViewById(R.id.layout_weekly)
        layoutMonthly = findViewById(R.id.layout_monthly)

        layoutDaily.visibility = View.VISIBLE
        layoutWeekly.visibility = View.GONE
        layoutMonthly.visibility = View.GONE

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        layoutDaily.visibility = View.VISIBLE
                        layoutWeekly.visibility = View.GONE
                        layoutMonthly.visibility = View.GONE
                    }
                    1 -> {
                        layoutDaily.visibility = View.GONE
                        layoutWeekly.visibility = View.VISIBLE
                        layoutMonthly.visibility = View.GONE
                    }
                    2 -> {
                        layoutDaily.visibility = View.GONE
                        layoutWeekly.visibility = View.GONE
                        layoutMonthly.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        barChart = findViewById(R.id.bar_chart)
        lineChart = findViewById(R.id.line_chart)
        monthlyBarChart = findViewById(R.id.monthly_bar_chart)
        monthlyLineChart = findViewById(R.id.monthly_line_chart)
        replayDayChart = findViewById(R.id.replay_day_chart)
        replayMonthChart = findViewById(R.id.replay_month_chart)
        val weeklyTestChart: BarChart = findViewById(R.id.weekly_test_chart)
        val weeklyRateChart: LineChart = findViewById(R.id.weekly_rate_chart)
        val weeklyReplayChart: LineChart = findViewById(R.id.weekly_replay_chart)

        bottomNavigation = findViewById(R.id.bottom_navigation)
        bottomNavigation.selectedItemId = R.id.nav_account
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_menu -> {
                    startActivity(Intent(this, ScenarioSelectActivity::class.java))
                    true
                }
                R.id.nav_dictation -> {
                    Toast.makeText(this, "시나리오를 선택하세요.", Toast.LENGTH_SHORT).show()
                    false
                }
                R.id.ai_review -> {
                    startActivity(Intent(this, AiReviewActivity::class.java))
                    true
                }
                R.id.nav_account -> true
                R.id.nav_exit -> {
                    val versionName = try {
                        packageManager.getPackageInfo(packageName, 0).versionName
                    } catch (e: Exception) {
                        "알 수 없음"
                    }
                    val exitMessage = """
                        정말 종료하시겠습니까?

                        📦 비전: $versionName
                        📧 문의: CREN-J (japyi0210@gmail.com)
                    """.trimIndent()
                    AlertDialog.Builder(this)
                        .setTitle("앱 종료")
                        .setMessage(exitMessage)
                        .setPositiveButton("예") { _, _ -> showAdOrExit() }
                        .setNegativeButton("아니오", null)
                        .show()
                    true
                }
                else -> false
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val logoutButton: Button = findViewById(R.id.btn_logout)
        logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            googleSignInClient.signOut().addOnCompleteListener {
                Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, StartActivity::class.java))
                finish()
            }
        }

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(user.uid).collection("reviews")
            .get()
            .addOnSuccessListener { snapshot ->
                val dayMap = mutableMapOf<String, MutableList<Int>>()
                val monthMap = mutableMapOf<String, MutableList<Int>>()
                val replayDayMap = mutableMapOf<String, MutableList<Int>>()
                val replayMonthMap = mutableMapOf<String, MutableList<Int>>()
                val weeklyTestCountMap = mutableMapOf<String, MutableList<Int>>()
                val weeklyAverageRateMap = mutableMapOf<String, MutableList<Int>>()
                val weeklyReplayMap = mutableMapOf<String, MutableList<Int>>()

                val fullFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val weekFormatter = SimpleDateFormat("yyyy-ww", Locale.getDefault())

                for (doc in snapshot) {
                    val date = doc.getTimestamp("timestamp")?.toDate() ?: continue
                    val similarity = doc.getLong("similarity")?.toInt() ?: continue
                    val replay = doc.getLong("replayCount")?.toInt() ?: 0

                    val dateStr = fullFormatter.format(date)
                    val monthStr = monthFormatter.format(date)
                    val weekStr = weekFormatter.format(date)

                    dayMap.getOrPut(dateStr) { mutableListOf() }.add(similarity)
                    monthMap.getOrPut(monthStr) { mutableListOf() }.add(similarity)
                    replayDayMap.getOrPut(dateStr) { mutableListOf() }.add(replay)
                    replayMonthMap.getOrPut(monthStr) { mutableListOf() }.add(replay)
                    weeklyTestCountMap.getOrPut(weekStr) { mutableListOf() }.add(1)
                    weeklyAverageRateMap.getOrPut(weekStr) { mutableListOf() }.add(similarity)
                    weeklyReplayMap.getOrPut(weekStr) { mutableListOf() }.add(replay)
                }

                drawTestCountBarChart(dayMap)
                drawAverageRateLineChart(dayMap)
                drawMonthlyTestCountBarChart(monthMap)
                drawMonthlyAverageRateLineChart(monthMap)
                drawReplayAverageChart(replayDayMap, replayDayChart, true)
                drawMonthlyReplayChart(replayMonthMap, replayMonthChart)
                drawWeeklyTestCountChart(weeklyTestCountMap, weeklyTestChart)
                drawWeeklyAverageRateChart(weeklyAverageRateMap, weeklyRateChart)
                drawWeeklyReplayChart(weeklyReplayMap, weeklyReplayChart)
            }
            .addOnFailureListener {
                Toast.makeText(this, "기록 불러오기 실패", Toast.LENGTH_SHORT).show()
            }
    }
    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-1872760638277957/9712274803", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { mInterstitialAd = ad }
                override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null }
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

    private fun generateLast7Days(): Pair<List<String>, List<String>> {
        val fullFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val fullDates = mutableListOf<String>()
        val displayLabels = mutableListOf<String>()
        repeat(7) {
            val date = calendar.time
            fullDates.add(fullFormat.format(date))
            displayLabels.add(displayFormat.format(date))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return Pair(fullDates, displayLabels)
    }

    private fun generateLast6Weeks(): Pair<List<String>, List<String>> {
        val fullFormat = SimpleDateFormat("yyyy-ww", Locale.getDefault()) // 저장용
        val displayFormat = SimpleDateFormat("MM월 W주", Locale.KOREA)    // 사용자 표시용
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.add(Calendar.WEEK_OF_YEAR, -5)

        val fullWeeks = mutableListOf<String>()
        val displayWeeks = mutableListOf<String>()
        repeat(6) {
            fullWeeks.add(fullFormat.format(calendar.time))
            displayWeeks.add(displayFormat.format(calendar.time))
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return Pair(fullWeeks, displayWeeks)
    }

    private fun generateLast6Months(): Pair<List<String>, List<String>> {
        val keyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault()) // Firestore key 용
        val labelFormat = SimpleDateFormat("yy-MM", Locale.getDefault()) // 라벨 표시용
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MONTH, -5)

        val keys = mutableListOf<String>()
        val labels = mutableListOf<String>()
        repeat(6) {
            val date = calendar.time
            keys.add(keyFormat.format(date))
            labels.add(labelFormat.format(date))
            calendar.add(Calendar.MONTH, 1)
        }
        return Pair(keys, labels)
    }

    private fun drawTestCountBarChart(data: Map<String, List<Int>>) {
        val (fullDates, displayLabels) = generateLast7Days()
        val entries = fullDates.mapIndexed { index, date ->
            BarEntry(index.toFloat(), data[date]?.size?.toFloat() ?: 0f)
        }

        val dataSet = BarDataSet(entries, "").apply {
            setDrawValues(true)  // 값을 표시하도록 설정
            valueTextSize = 13f
            valueTextColor = COLOR_DAILY_BAR
            valueTypeface = ResourcesCompat.getFont(this@StatisticsChartActivity, R.font.nanum_pen)
            setColor(COLOR_DAILY_BAR)
        }

        val cuteFont = ResourcesCompat.getFont(this, R.font.nanum_pen)

        barChart.data = BarData(dataSet)
        barChart.apply {
            setDrawGridBackground(false)
            setBackgroundColor(Color.WHITE)
            animateY(1000, Easing.EaseInOutQuad)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(displayLabels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 13f
                typeface = cuteFont
            }
            axisLeft.apply {
                axisMinimum = 0f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 12f
                typeface = cuteFont
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            setFitBars(true)
            invalidate()
        }
    }

    private fun drawAverageRateLineChart(data: Map<String, List<Int>>) {
        val (fullDates, displayLabels) = generateLast7Days()
        val entries = fullDates.mapIndexed { index, date ->
            Entry(index.toFloat(), data[date]?.average()?.toFloat() ?: 0f)
        }

        val dataSet = LineDataSet(entries, "").apply {
            setDrawValues(true)
            valueTextSize = 13f
            valueTextColor = COLOR_DAILY_LINE
            valueTypeface = ResourcesCompat.getFont(this@StatisticsChartActivity, R.font.nanum_pen)
            lineWidth = 2f
            circleRadius = 4f
            color = COLOR_DAILY_LINE
            setCircleColor(COLOR_DAILY_LINE)
        }

        val cuteFont = ResourcesCompat.getFont(this, R.font.nanum_pen)

        lineChart.data = LineData(dataSet)
        lineChart.apply {
            setDrawGridBackground(false)
            setBackgroundColor(Color.WHITE)
            animateY(1000, Easing.EaseInOutQuad)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(displayLabels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 13f
                typeface = cuteFont
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 20f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 12f
                typeface = cuteFont
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            invalidate()
        }
    }

    private fun drawReplayAverageChart(data: Map<String, List<Int>>, chart: LineChart, isDaily: Boolean) {
        val (fullKeys, labels) = if (isDaily) generateLast7Days() else generateLast6Months()
        val entries = fullKeys.mapIndexed { index, key ->
            Entry(index.toFloat(), data[key]?.average()?.toFloat() ?: 0f)
        }

        val color = if (isDaily) COLOR_DAILY_REPLAY else COLOR_MONTHLY_REPLAY

        val dataSet = LineDataSet(entries, "").apply {
            setDrawValues(true)
            valueTextSize = 13f
            valueTextColor = COLOR_DAILY_LINE
            valueTypeface = ResourcesCompat.getFont(this@StatisticsChartActivity, R.font.nanum_pen)
            lineWidth = 2f
            circleRadius = 4f
            this.color = COLOR_DAILY_REPLAY
            setCircleColor(color)
        }

        val cuteFont = ResourcesCompat.getFont(this, R.font.nanum_pen)

        chart.data = LineData(dataSet)
        chart.apply {
            setDrawGridBackground(false)
            setBackgroundColor(Color.WHITE)
            animateY(1000, Easing.EaseInOutQuad)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 13f
                typeface = cuteFont
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 5f
                granularity = 1f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 12f
                typeface = cuteFont
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return String.format("%.1f", value)
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            invalidate()
        }
    }

    private fun drawWeeklyTestCountChart(data: Map<String, List<Int>>, chart: BarChart) {
        val (fullWeeks, displayWeeks) = generateLast6Weeks()
        val entries = fullWeeks.mapIndexed { index, week ->
            BarEntry(index.toFloat(), data[week]?.size?.toFloat() ?: 0f)
        }

        val dataSet = BarDataSet(entries, "").apply {
            setDrawValues(true)  // 값을 표시하도록 설정
            valueTextSize = 13f
            valueTextColor = COLOR_WEEKLY_BAR
            valueTypeface = ResourcesCompat.getFont(this@StatisticsChartActivity, R.font.nanum_pen)
            setColors(COLOR_WEEKLY_BAR)
        }

        val cuteFont = ResourcesCompat.getFont(this, R.font.nanum_pen)

        chart.data = BarData(dataSet)
        chart.apply {
            setDrawGridBackground(false)
            setBackgroundColor(Color.WHITE)
            animateY(1000, Easing.EaseInOutQuad)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(displayWeeks)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 13f
                typeface = cuteFont
            }
            axisLeft.apply {
                axisMinimum = 0f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 12f
                typeface = cuteFont
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            setFitBars(true)
            invalidate()
        }
    }

    private fun drawWeeklyAverageRateChart(data: Map<String, List<Int>>, chart: LineChart) {
        val (fullWeeks, displayWeeks) = generateLast6Weeks()
        val entries = fullWeeks.mapIndexed { index, week ->
            Entry(index.toFloat(), data[week]?.average()?.toFloat() ?: 0f)
        }

        val dataSet = LineDataSet(entries, "").apply {
            setDrawValues(true)  // 값을 표시하도록 설정
            valueTextSize = 13f
            valueTextColor = COLOR_WEEKLY_LINE
            valueTypeface = ResourcesCompat.getFont(this@StatisticsChartActivity, R.font.nanum_pen)
            lineWidth = 2f
            circleRadius = 4f
            color = COLOR_WEEKLY_LINE
            setCircleColor(COLOR_WEEKLY_LINE)
        }

        val cuteFont = ResourcesCompat.getFont(this, R.font.nanum_pen)

        chart.data = LineData(dataSet)
        chart.apply {
            setDrawGridBackground(false)
            setBackgroundColor(Color.WHITE)
            animateY(1000, Easing.EaseInOutQuad)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(displayWeeks)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 13f
                typeface = cuteFont
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 20f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 12f
                typeface = cuteFont
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            invalidate()
        }
    }

    private fun drawWeeklyReplayChart(data: Map<String, List<Int>>, chart: LineChart) {
        val (fullWeeks, displayWeeks) = generateLast6Weeks()
        val entries = fullWeeks.mapIndexed { index, week ->
            Entry(index.toFloat(), data[week]?.average()?.toFloat() ?: 0f)
        }

        val dataSet = LineDataSet(entries, "").apply {
            setDrawValues(true)  // 값을 표시하도록 설정
            valueTextSize = 13f
            valueTextColor = COLOR_WEEKLY_REPLAY
            valueTypeface = ResourcesCompat.getFont(this@StatisticsChartActivity, R.font.nanum_pen)
            lineWidth = 2f
            circleRadius = 4f
            color = COLOR_WEEKLY_REPLAY
            setCircleColor(COLOR_WEEKLY_REPLAY)
        }

        val cuteFont = ResourcesCompat.getFont(this, R.font.nanum_pen)

        chart.data = LineData(dataSet)
        chart.apply {
            setDrawGridBackground(false)
            setBackgroundColor(Color.WHITE)
            animateY(1000, Easing.EaseInOutQuad)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(displayWeeks)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 13f
                typeface = cuteFont
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 5f
                granularity = 1f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 12f
                typeface = cuteFont
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return String.format("%.1f", value)
                    }
                }
            }

            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            invalidate()
        }
    }

    private fun drawMonthlyTestCountBarChart(data: Map<String, List<Int>>) {
        val (keys, labels) = generateLast6Months()
        val entries = keys.mapIndexed { index, month ->
            BarEntry(index.toFloat(), data[month]?.size?.toFloat() ?: 0f)
        }

        val dataSet = BarDataSet(entries, "").apply {
            setDrawValues(true)  // 값을 표시하도록 설정
            valueTextSize = 13f
            valueTextColor = COLOR_MONTHLY_BAR
            valueTypeface = ResourcesCompat.getFont(this@StatisticsChartActivity, R.font.nanum_pen)
            setColors(COLOR_MONTHLY_BAR)
        }

        val cuteFont = ResourcesCompat.getFont(this, R.font.nanum_pen)

        monthlyBarChart.data = BarData(dataSet)
        monthlyBarChart.apply {
            setDrawGridBackground(false)
            setBackgroundColor(Color.WHITE)
            animateY(1000, Easing.EaseInOutQuad)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 13f
                typeface = cuteFont
            }

            axisLeft.apply {
                axisMinimum = 0f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 12f
                typeface = cuteFont
            }

            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            setFitBars(true)
            invalidate()
        }
    }

    private fun drawMonthlyAverageRateLineChart(data: Map<String, List<Int>>) {
        val (keys, labels) = generateLast6Months()
        val entries = keys.mapIndexed { index, month ->
            Entry(index.toFloat(), data[month]?.average()?.toFloat() ?: 0f)
        }

        val dataSet = LineDataSet(entries, "").apply {
            setDrawValues(true)  // 값을 표시하도록 설정
            valueTextSize = 13f
            valueTextColor = COLOR_MONTHLY_LINE
            valueTypeface = ResourcesCompat.getFont(this@StatisticsChartActivity, R.font.nanum_pen)
            lineWidth = 2f
            circleRadius = 4f
            color = COLOR_MONTHLY_LINE
            setCircleColor(COLOR_MONTHLY_LINE)
        }

        val cuteFont = ResourcesCompat.getFont(this, R.font.nanum_pen)

        monthlyLineChart.data = LineData(dataSet)
        monthlyLineChart.apply {
            setDrawGridBackground(false)
            setBackgroundColor(Color.WHITE)
            animateY(1000, Easing.EaseInOutQuad)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 13f
                typeface = cuteFont
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 20f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 12f
                typeface = cuteFont
            }

            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            invalidate()
        }
    }

    private fun drawMonthlyReplayChart(data: Map<String, List<Int>>, chart: LineChart) {
        val (keys, labels) = generateLast6Months()
        val entries = keys.mapIndexed { index, month ->
            Entry(index.toFloat(), data[month]?.average()?.toFloat() ?: 0f)
        }

        val dataSet = LineDataSet(entries, "").apply {
            setDrawValues(true)  // 값을 표시하도록 설정
            valueTextSize = 13f
            valueTextColor = COLOR_MONTHLY_REPLAY
            valueTypeface = ResourcesCompat.getFont(this@StatisticsChartActivity, R.font.nanum_pen)
            lineWidth = 2f
            circleRadius = 4f
            color = COLOR_MONTHLY_REPLAY
            setCircleColor(COLOR_MONTHLY_REPLAY)
        }

        val cuteFont = ResourcesCompat.getFont(this, R.font.nanum_pen)

        chart.data = LineData(dataSet)
        chart.apply {
            setDrawGridBackground(false)
            setBackgroundColor(Color.WHITE)
            animateY(1000, Easing.EaseInOutQuad)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 13f
                typeface = cuteFont
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 5f
                granularity = 1f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawLabels(true)
                textColor = Color.DKGRAY
                textSize = 12f
                typeface = cuteFont
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return String.format("%.1f", value)
                    }
                }
            }

            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            invalidate()
        }
    }
}
