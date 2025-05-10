package com.japyi0210.simpleenglishdictation

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 2초 후 ScenarioSelectActivity로 전환
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, ScenarioSelectActivity::class.java)
            startActivity(intent)
            finish() // SplashActivity 종료
        }, 2000)
    }
}
