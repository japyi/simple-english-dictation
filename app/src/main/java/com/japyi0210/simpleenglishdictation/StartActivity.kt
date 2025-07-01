package com.japyi0210.simpleenglishdictation

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class StartActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase 초기화
        FirebaseApp.initializeApp(this)
        firebaseAuth = FirebaseAuth.getInstance()

        // 🔐 자동 로그인 처리
        firebaseAuth.currentUser?.let {
            startActivity(Intent(this, ScenarioSelectActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_start)

        // Google 로그인 옵션 설정
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // strings.xml에 정의 필요
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 버튼 찾기
        val loginBtn = findViewById<Button>(R.id.btn_login)
        val guestBtn = findViewById<Button>(R.id.btn_guest)

        // 구글 로그인 버튼 클릭 시
        loginBtn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        // 로그인 없이 사용 버튼 클릭 시
        guestBtn.setOnClickListener {
            if (firebaseAuth.currentUser != null) {
                firebaseAuth.signOut()
            }
            startActivity(Intent(this, ScenarioSelectActivity::class.java))
            finish()
        }
    }

    // 로그인 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.result
                firebaseAuthWithGoogle(account)
            } catch (e: Exception) {
                Log.e(TAG, "구글 로그인 실패: ${e.message}")
                Toast.makeText(this, "구글 로그인 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Firebase 인증 처리
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        if (account == null) {
            Toast.makeText(this, "Google 계정을 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    Toast.makeText(this, "로그인 성공: ${user?.email}", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, ScenarioSelectActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Firebase 인증 실패", Toast.LENGTH_SHORT).show()
                }
            }
    }

    companion object {
        private const val RC_SIGN_IN = 1001
        private const val TAG = "StartActivity"
    }
}
