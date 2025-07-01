package com.japyi0210.simpleenglishdictation

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.ktx.Firebase
import com.japyi0210.simpleenglishdictation.data.AppDatabase
import com.japyi0210.simpleenglishdictation.data.DictationRecord
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeech
    private lateinit var resultView: TextView
    private lateinit var translationView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var input: EditText
    private lateinit var checkBtn: Button
    private lateinit var playBtn: Button
    private lateinit var voiceModeGroup: RadioGroup
    private lateinit var radioFixed: RadioButton
    private lateinit var radioRandom: RadioButton
    private lateinit var speedLabel: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var scenarioImageView: ImageView
    private lateinit var scenarioNameMap: Map<String, String>
    private lateinit var currentSentence: Sentence
    private lateinit var sentences: List<Sentence>
    private val usedSentences = mutableSetOf<Sentence>()
    private var sentenceIndex = 0
    private var speechRate = 1.0f
    private var isSentencePlayed = false
    private var fixedVoice: Voice? = null
    private var isCheckEnabled = false
    private var replayCount = 0
    private var orderMode: String = "랜덤"
    private var isFeedbackFetched = false

    private var mInterstitialAd: InterstitialAd? = null

    private val dao by lazy { AppDatabase.getDatabase(this).dictationDao() }

    data class Sentence(val english: String, val korean: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scenarioNameMap = loadScenarioNameMap()
        val scenarioKey = intent.getStringExtra("scenario_key") ?: "default"
        orderMode = intent.getStringExtra("order_mode") ?: "랜덤"
        val scenarioTitle = scenarioNameMap[scenarioKey] ?: "무작위 문장"

        scenarioImageView = findViewById(R.id.imageView_scenario)
        loadScenarioImageFromAssets(scenarioKey)

        // 시나리오 제목 표시
        val titleView: TextView = findViewById(R.id.textView_scenario_title)
        titleView.text = scenarioTitle

        MobileAds.initialize(this) {}
        findViewById<AdView>(R.id.adView).loadAd(AdRequest.Builder().build())
        loadInterstitialAd()

        playBtn = findViewById(R.id.button_play)
        checkBtn = findViewById(R.id.button_check)
        input = findViewById(R.id.editText_input)
        resultView = findViewById(R.id.textView_result)
        translationView = findViewById(R.id.textView_translation)
        feedbackView = findViewById(R.id.textView_feedback)
        speedLabel = findViewById(R.id.speedLabel)
        voiceModeGroup = findViewById(R.id.voiceModeGroup)
        radioFixed = findViewById(R.id.radio_fixed)
        radioRandom = findViewById(R.id.radio_random)

        val speed = findViewById<SeekBar>(R.id.speedSeekBar)
        speed.max = 20
        speed.progress = 10
        speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speechRate = progress / 10f
                tts.setSpeechRate(speechRate)
                speedLabel.text = "배속: %.1fx".format(speechRate)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bottomNavigation = findViewById(R.id.bottom_navigation)
        bottomNavigation.selectedItemId = R.id.nav_dictation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_menu -> {
                    startActivity(Intent(this, ScenarioSelectActivity::class.java))
                    finish()
                    true
                }
                R.id.ai_review -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        startActivity(Intent(this, StartActivity::class.java))
                    } else {
                        startActivity(Intent(this, AiReviewActivity::class.java))
                    }
                    true
                }
                R.id.nav_account -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
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

                    val exitMessage = """
                        정말 종료하시겠습니까?

                        📦 버전: $versionName
                        📧 문의: CREN-J (japyi0210@gmail.com)
                    """.trimIndent()

                    AlertDialog.Builder(this)
                        .setTitle("앱 종료")
                        .setMessage(exitMessage)
                        .setPositiveButton("예") { _, _ ->
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
                        .setNegativeButton("아니오", null)
                        .show()
                    true
                }
                else -> false
            }
        }

        sentences = loadSentences()
        sentenceIndex = 0

        lifecycleScope.launch {
            dao.clearAll()
            updateResultWithStats()
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setSpeechRate(speechRate)
                val voices = tts.voices.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
                fixedVoice = voices.find { it.name.contains("female", true) }
                    ?: voices.find { it.name.contains("en-us-x-sfg", true) } ?: voices.randomOrNull()
                fixedVoice?.let { tts.voice = it }
                radioFixed.isChecked = true
            }
        }

        playBtn.setOnClickListener {
            if (playBtn.text == "🎙 문장 다시 듣기") {
                val voices = tts.voices.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
                tts.voice = when {
                    radioRandom.isChecked && voices.isNotEmpty() -> voices.random()
                    radioFixed.isChecked && fixedVoice != null -> fixedVoice
                    else -> tts.voice
                }
                tts.speak(currentSentence.english, TextToSpeech.QUEUE_FLUSH, null, null)
                replayCount++
                return@setOnClickListener
            }

            if (usedSentences.size >= sentences.size) {
                Toast.makeText(this, "모든 문장을 다 풀었습니다! 다시 시작합니다.", Toast.LENGTH_SHORT).show()
                usedSentences.clear()
                sentenceIndex = 0
            }

            if (orderMode == "순서대로") {
                if (sentenceIndex >= sentences.size) sentenceIndex = 0
                currentSentence = sentences[sentenceIndex++]
            } else {
                val remaining = sentences.filterNot { usedSentences.contains(it) }
                currentSentence = if (remaining.isEmpty()) {
                    usedSentences.clear()
                    sentences.random()
                } else {
                    remaining.random()
                }
                usedSentences.add(currentSentence)
            }

            usedSentences.add(currentSentence)
            replayCount = 0
            isFeedbackFetched = false
            isSentencePlayed = true

            val voices = tts.voices.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            tts.voice = when {
                radioRandom.isChecked && voices.isNotEmpty() -> voices.random()
                radioFixed.isChecked && fixedVoice != null -> fixedVoice
                else -> tts.voice
            }

            tts.speak(currentSentence.english, TextToSpeech.QUEUE_FLUSH, null, null)
            replayCount++

            translationView.visibility = TextView.GONE
            feedbackView.visibility = TextView.GONE
            checkBtn.text = "⏳ 10초 후에 제출하실 수 있습니다."
            checkBtn.isEnabled = false
            checkBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CCCCCC"))
            isCheckEnabled = false
            playBtn.text = "🎙 문장 다시 듣기"

            Handler(Looper.getMainLooper()).postDelayed({
                checkBtn.text = "✅ 정답 확인"
                checkBtn.isEnabled = true
                checkBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00AA77"))
                isCheckEnabled = true
            }, 10_000)

            lifecycleScope.launch { updateResultWithStats() }
        }

        checkBtn.setOnClickListener {
            if (checkBtn.text == "🔊 다시 듣기") {
                val voices = tts.voices.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
                tts.voice = when {
                    radioRandom.isChecked && voices.isNotEmpty() -> voices.random()
                    radioFixed.isChecked && fixedVoice != null -> fixedVoice
                    else -> tts.voice
                }
                tts.speak(currentSentence.english, TextToSpeech.QUEUE_FLUSH, null, null)
                replayCount++
                return@setOnClickListener
            }

            // ✅ 기존 정답 확인 로직 유지
            val userInputText = input.text.toString().trim()
            val correctText = currentSentence.english.trim()
            val similarity = calculateSimilarity(userInputText.lowercase(), correctText.lowercase())
            val isCorrect = similarity >= 85

            val message = when {
                similarity == 100 -> "⭕️ 정답입니다!"
                similarity >= 85 -> "🔵 거의 정답이에요! ($similarity% 일치)"
                else -> "❌ 오답입니다. ($similarity% 일치)"
            }

            resultView.text = """
                $message
                🔁 문장 듣기: ${replayCount}회
            """.trimIndent()
            resultView.setTypeface(null, Typeface.BOLD)
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

            if (!isFeedbackFetched) {
                feedbackView.text = "💬 AI 피드백 요청 중..."
                feedbackView.visibility = TextView.VISIBLE

                getFeedbackFromChatGPT(userInputText, correctText, similarity) { feedback ->
                    runOnUiThread {
                        feedbackView.text = "AI 피드백: $feedback"
                        isFeedbackFetched = true
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser != null) {
                            val db = FirebaseFirestore.getInstance()
                            val reviewData = hashMapOf(
                                "sentence" to correctText,
                                "userInput" to userInputText,
                                "feedback" to feedback,
                                "timestamp" to FieldValue.serverTimestamp(),
                                "similarity" to similarity,
                                "replayCount" to replayCount
                            )
                            db.collection("users")
                                .document(currentUser.uid)
                                .collection("reviews")
                                .add(reviewData)
                        }
                    }
                }
            }

            resultView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
            input.setText("")
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken, 0)

            checkBtn.text = "🔊 다시 듣기"
            checkBtn.isEnabled = true
            checkBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFA500"))
            playBtn.text = "🎙 다음 문장 듣기"

            lifecycleScope.launch {
                dao.insert(
                    DictationRecord(
                        sentence = correctText,
                        userInput = userInputText,
                        similarity = similarity,
                        correct = isCorrect,
                        replayCount = replayCount
                    )
                )
            }
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            "ca-app-pub-1872760638277957/9712274803",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    mInterstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                }
            }
        )
    }

    private fun loadSentences(): List<Sentence> {
        val scenarioKey = intent.getStringExtra("scenario_key") ?: "default"

        return try {
            val lines = if (scenarioKey == "all") {
                val files = assets.list("")?.filter {
                    it.startsWith("scenario_") && it.endsWith(".txt")
                } ?: emptyList()
                files.flatMap { file ->
                    try {
                        assets.open(file).bufferedReader().readLines()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            } else {
                val fileName = "scenario_${scenarioKey}.txt"
                assets.open(fileName).bufferedReader().readLines()
            }

            val sentenceList = lines.mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size == 2) Sentence(parts[0].trim(), parts[1].trim()) else null
            }

            if (orderMode == "랜덤") sentenceList.shuffled() else sentenceList
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
                    if (parts.size >= 2) parts[1].trim() to parts[0].trim() else null
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

    private suspend fun updateResultWithStats() {
        val total = dao.getTotalCount()
        val correctCount = dao.getCorrectCount()
        val rate = if (total == 0) 0 else (correctCount * 100) / total
        resultView.text = "정답률: ${rate}% ($correctCount/$total)"
        resultView.setTextColor(Color.BLACK)
    }

    private fun fetchApiKeyFromRemoteConfig(onKeyFetched: (String?) -> Unit) {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf("openai_api_key" to ""))

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                val apiKey = if (task.isSuccessful) {
                    remoteConfig.getString("openai_api_key")
                } else {
                    null
                }
                onKeyFetched(apiKey)
            }
    }

    private fun getFeedbackFromChatGPT(userInput: String, correctText: String, similarity: Int, onResult: (String) -> Unit) {
        fetchApiKeyFromRemoteConfig { apiKey ->
            if (apiKey.isNullOrBlank()) {
                onResult("❗ API 키를 불러올 수 없습니다.")
                return@fetchApiKeyFromRemoteConfig
            }

            val client = OkHttpClient()
            val prompt = """
                - You are an English dictation app.
                - The app reads \"$correctText\", and the user submits \"$userInput\".
                - Provide a feedback in KOREAN using 2 or 3 sentences.
                - If the answer is incorrect, point out exactly what part was wrong and explain why it was a mistake.
            """.trimIndent()

            val json = JSONObject().apply {
                put("model", "gpt-4.1-nano")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(RequestBody.create("application/json".toMediaTypeOrNull(), json.toString()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onResult("❗ AI 연결 실패: ${e.localizedMessage}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let {
                        try {
                            val reply = JSONObject(it)
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            onResult(reply.trim())
                        } catch (e: Exception) {
                            onResult("❗ 응답 파싱 오류")
                        }
                    } ?: onResult("❗ 응답이 비어 있습니다.")
                }
            })
        }
    }

    private fun loadScenarioImageFromAssets(scenarioKey: String) {
        val assetManager = assets
        val fileName = "scenarios/$scenarioKey.webp"

        try {
            val inputStream = assetManager.open(fileName)
            val drawable = Drawable.createFromStream(inputStream, null)
            scenarioImageView.setImageDrawable(drawable)
        } catch (e: Exception) {
            try {
                val fallback = assetManager.open("scenarios/default.webp")
                val drawable = Drawable.createFromStream(fallback, null)
                scenarioImageView.setImageDrawable(drawable)
            } catch (_: Exception) {
                // 기본 이미지도 없는 경우 무시
            }
        }
    }
    }