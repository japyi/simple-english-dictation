package com.japyi0210.simpleenglishdictation

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.FirebaseApp

class SplashActivity : AppCompatActivity() {

    private val textArray = listOf("영", "받", "쓰", "AI")
    private val typingInterval = 500L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_splash)

        val firstLine = findViewById<TextView>(R.id.firstLine)
        val secondLine = findViewById<TextView>(R.id.secondLine)

        // ✅ 나눔펜 폰트 적용
        val nanumPenFont = ResourcesCompat.getFont(this, R.font.nanum_pen)
        firstLine.typeface = nanumPenFont
        secondLine.typeface = nanumPenFont

        // 1. 첫 문장: 1초 지연 후 페이드 인 + 슬라이드 인
        firstLine.translationX = -800f
        firstLine.alpha = 0f
        firstLine.visibility = View.VISIBLE

        mainHandler.postDelayed({
            ObjectAnimator.ofFloat(firstLine, View.ALPHA, 0f, 1f).apply {
                duration = 800
                start()
            }
            ObjectAnimator.ofFloat(firstLine, View.TRANSLATION_X, 0f).apply {
                duration = 800
                interpolator = DecelerateInterpolator()
                start()
            }
        }, 1000) // ✅ 1초 지연

        // 2. 딜레이 후 타이핑 애니메이션 시작
        mainHandler.postDelayed({ startTypingAnimation(secondLine) }, 2700)
    }

    private fun startTypingAnimation(secondLine: TextView) {
        val builder = StringBuilder()

        textArray.forEachIndexed { index, text ->
            val delay = index * typingInterval

            mainHandler.postDelayed({
                builder.append(text)
                secondLine.text = builder.toString()

                // 강조 애니메이션
                secondLine.animate()
                    .scaleX(1.1f).scaleY(1.1f).setDuration(150)
                    .withEndAction {
                        secondLine.animate()
                            .scaleX(1f).scaleY(1f).setDuration(150).start()
                    }
                    .start()

                // 마지막 글자 후 화면 전환
                if (index == textArray.lastIndex) {
                    mainHandler.postDelayed({
                        startActivity(Intent(this, StartActivity::class.java))
                        finish()
                    }, 3000) // ✅ 3초 대기
                }
            }, delay)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
