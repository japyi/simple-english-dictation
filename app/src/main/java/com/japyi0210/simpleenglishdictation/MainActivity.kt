// MainActivity.kt
package com.japyi0210.simpleenglishdictation

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.inputmethod.InputMethodManager
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.japyi0210.simpleenglishdictation.data.AppDatabase
import com.japyi0210.simpleenglishdictation.data.DictationRecord
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeech
    private lateinit var resultView: TextView
    private lateinit var translationView: TextView
    private lateinit var input: EditText
    private lateinit var checkBtn: Button
    private lateinit var playBtn: Button
    private lateinit var voiceModeGroup: RadioGroup
    private lateinit var radioFixed: RadioButton
    private lateinit var radioRandom: RadioButton
    private lateinit var scenarioTitleView: TextView
    private lateinit var scenarioNameMap: Map<String, String>

    private lateinit var currentSentence: Sentence
    private lateinit var sentences: List<Sentence>
    private val usedSentences = mutableSetOf<Sentence>()
    private var speechRate = 1.0f
    private var isSentencePlayed = false
    private var fixedVoice: Voice? = null

    private var interstitialAd: InterstitialAd? = null
    private val dao by lazy { AppDatabase.getDatabase(this).dictationDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scenarioNameMap = loadScenarioNameMap()
        val scenarioKey = intent.getStringExtra("scenario_key") ?: "default"
        val scenarioTitle = scenarioNameMap[scenarioKey] ?: "기본 시나리오"

        val testDeviceIds = listOf("3E446EC9116D91100BED7E1F8658114E")
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(configuration)

        MobileAds.initialize(this) {}
        val adView = findViewById<AdView>(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        playBtn = findViewById(R.id.button_play)
        checkBtn = findViewById(R.id.button_check)
        input = findViewById(R.id.editText_input)
        val speed = findViewById<SeekBar>(R.id.speedSeekBar)
        resultView = findViewById(R.id.textView_result)
        translationView = findViewById(R.id.textView_translation)
        val exitBtn = findViewById<Button>(R.id.button_exit)
        val backToScenariosBtn = findViewById<Button>(R.id.button_back_to_scenarios)
        val appInfoBtn = findViewById<Button>(R.id.button_app_info)
        scenarioTitleView = findViewById(R.id.textView_scenario)
        scenarioTitleView.text = "📘 현재 시나리오: $scenarioTitle"

        voiceModeGroup = findViewById(R.id.voiceModeGroup)
        radioFixed = findViewById(R.id.radio_fixed)
        radioRandom = findViewById(R.id.radio_random)

        exitBtn.isEnabled = false
        exitBtn.alpha = 0.5f

        InterstitialAd.load(this,
            "ca-app-pub-3940256099942544/1033173712",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    exitBtn.isEnabled = true
                    exitBtn.alpha = 1.0f
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                }
            })

        backToScenariosBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("목차로 이동")
                .setMessage("정말 목차 화면으로 돌아가시겠습니까?")
                .setPositiveButton("예") { _, _ ->
                    val intent = Intent(this, ScenarioSelectActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("아니오", null)
                .show()
        }

        appInfoBtn.setOnClickListener {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            AlertDialog.Builder(this)
                .setTitle("심플 영어 받아쓰기")
                .setMessage("""
                    버전: $versionName
                    문의: japyi0210@gmail.com
                """.trimIndent())
                .setPositiveButton("확인", null)
                .show()
        }

        sentences = loadSentences()

        lifecycleScope.launch {
            dao.clearAll()
            updateResultWithStats()
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setSpeechRate(speechRate)

                val voices = tts.voices.filter {
                    it.locale.language == "en" && !it.isNetworkConnectionRequired
                }

                fixedVoice = voices.find { it.name.contains("female", ignoreCase = true) }
                    ?: voices.find { it.name.contains("en-us-x-sfg", ignoreCase = true) }
                            ?: voices.randomOrNull()

                fixedVoice?.let {
                    tts.voice = it
                }

                radioFixed.isChecked = true
            }
        }

        playBtn.setOnClickListener {
            if (!isSentencePlayed) {
                val remaining = sentences.filterNot { usedSentences.contains(it) }
                currentSentence = if (remaining.isEmpty()) {
                    Toast.makeText(this, "모든 문장을 다 풀었습니다! 다시 시작합니다.", Toast.LENGTH_SHORT).show()
                    usedSentences.clear()
                    sentences.random()
                } else {
                    remaining.random()
                }
                usedSentences.add(currentSentence)
            }

            isSentencePlayed = true

            val availableVoices = tts.voices.filter {
                it.locale.language == "en" && !it.isNetworkConnectionRequired
            }
            if (radioRandom.isChecked && availableVoices.isNotEmpty()) {
                tts.voice = availableVoices.random()
            } else if (radioFixed.isChecked && fixedVoice != null) {
                tts.voice = fixedVoice
            }

            tts.speak(currentSentence.english, TextToSpeech.QUEUE_FLUSH, null, null)
            translationView.text = ""
            translationView.visibility = TextView.GONE

            checkBtn.text = "✅ 정답 확인"
            checkBtn.isEnabled = true
            checkBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00AA77"))
            playBtn.text = "🎧 문장 다시 듣기"

            lifecycleScope.launch {
                updateResultWithStats()
            }
        }

        checkBtn.setOnClickListener {
            if (checkBtn.text == "🔊 다시 듣기") {
                tts.speak(currentSentence.english, TextToSpeech.QUEUE_FLUSH, null, null)
                return@setOnClickListener
            }

            if (!isSentencePlayed) return@setOnClickListener

            val userInputText = input.text.toString().trim()
            val correct = currentSentence.english.trim()
            val similarity = calculateSimilarity(userInputText.lowercase(), correct.lowercase())
            val isCorrect = similarity >= 85

            val message = when {
                similarity == 100 -> "⭕️ 정답입니다!"
                similarity >= 85 -> "🟥 거의 정답이에요! ($similarity% 일치)"
                else -> "❌ 오답입니다. ($similarity% 일치)"
            }
            resultView.text = message
            resultView.setTextColor(
                when {
                    similarity == 100 -> Color.parseColor("#4CAF50")
                    similarity >= 85 -> Color.parseColor("#FFC107")
                    else -> Color.parseColor("#F44336")
                }
            )

            translationView.text = """
                🇰🇷 ${currentSentence.korean}
                🇺🇸 ${currentSentence.english}
                📝 $userInputText
            """.trimIndent()
            translationView.visibility = TextView.VISIBLE
            resultView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))

            input.setText("")
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(input.windowToken, 0)

            checkBtn.text = "🔊 다시 듣기"
            checkBtn.isEnabled = true
            checkBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFA500"))
            playBtn.text = "🎧 다음 문장 듣기"

            isSentencePlayed = false

            lifecycleScope.launch {
                dao.insert(
                    DictationRecord(
                        sentence = correct,
                        userInput = userInputText,
                        similarity = similarity,
                        correct = isCorrect
                    )
                )
            }
        }

        speed.max = 20
        speed.progress = 10
        speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speechRate = progress / 10f
                tts.setSpeechRate(speechRate)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        exitBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("앱 종료")
                .setMessage("정말 종료하시겠습니까?")
                .setPositiveButton("예") { _, _ ->
                    interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            finishAffinity()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            finishAffinity()
                        }
                    }
                    interstitialAd?.show(this) ?: finishAffinity()
                }
                .setNegativeButton("아니오", null)
                .show()
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    private suspend fun updateResultWithStats() {
        val total = dao.getTotalCount()
        val correctCount = dao.getCorrectCount()
        val rate = if (total == 0) 0 else (correctCount * 100) / total
        resultView.text = "현재 정답률: ${rate}% ($correctCount/$total)"
        resultView.setTextColor(Color.BLACK)
    }

    data class Sentence(val english: String, val korean: String)

    private fun loadSentences(): List<Sentence> {
        val scenarioKey = intent.getStringExtra("scenario_key") ?: "default"
        return try {
            if (scenarioKey == "all") {
                val files = assets.list("")?.filter {
                    it.startsWith("scenario_") && it.endsWith(".txt")
                } ?: emptyList()
                files.flatMap { file ->
                    try {
                        assets.open(file).bufferedReader().readLines().mapNotNull { line ->
                            val parts = line.split("\t")
                            if (parts.size == 2) Sentence(parts[0].trim(), parts[1].trim()) else null
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }.shuffled()
            } else {
                val fileName = "scenario_${scenarioKey}.txt"
                val inputStream = assets.open(fileName)
                inputStream.bufferedReader().readLines().mapNotNull { line ->
                    val parts = line.split("\t")
                    if (parts.size == 2) Sentence(parts[0].trim(), parts[1].trim()) else null
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "문장을 불러오는 데 실패했습니다.", Toast.LENGTH_LONG).show()
            emptyList()
        }
    }

    private fun loadScenarioNameMap(): Map<String, String> {
        return try {
            assets.open("scenarios.txt")
                .bufferedReader()
                .readLines()
                .mapNotNull { line ->
                    val parts = line.split("\t")
                    if (parts.size == 2) parts[1].trim() to parts[0].trim() else null
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun calculateSimilarity(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }

        val distance = dp[m][n]
        val maxLen = maxOf(m, n)
        return if (maxLen == 0) 100 else ((1 - distance.toDouble() / maxLen) * 100).toInt()
    }
}
