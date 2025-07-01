package com.japyi0210.simpleenglishdictation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import java.util.*

data class Review(
    val sentence: String = "",
    val userInput: String = "",
    val feedback: String = "",
    val similarity: Int = 0,
    val timestamp: Date? = null,
    val id: String = "",
    val replayCount: Int = 0
)

class AiReviewActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReviewAdapter
    private val reviewList = mutableListOf<Review>()

    private var mInterstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_review)

        MobileAds.initialize(this) {}
        loadInterstitialAd()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ReviewAdapter(reviewList) // 삭제 기능 제거
        recyclerView.adapter = adapter

        loadReviews()
        setupBottomNavigation()
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

    private fun loadReviews() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인 후 사용 가능합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .document(currentUser.uid)
            .collection("reviews")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                reviewList.clear()
                for (doc in documents) {
                    val review = doc.toObject(Review::class.java).copy(id = doc.id)
                    reviewList.add(review)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "리뷰를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.selectedItemId = R.id.ai_review

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
                R.id.ai_review -> true
                R.id.nav_account -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        val intent = Intent(this, LoginActivity::class.java)
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

                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("앱 종료")
                        .setMessage(exitMessage)
                        .setPositiveButton("예") { _, _ ->
                            showAdOrExit()
                        }
                        .setNegativeButton("아니오", null)
                        .show()
                    true
                }

                else -> false
            }
        }
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
