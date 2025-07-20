package com.japyi0210.simpleenglishdictation

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.*

class StartActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.setLanguageCode("ko")

        firebaseAuth.currentUser?.let {
            if (it.isEmailVerified) {
                startActivity(Intent(this, ScenarioSelectActivity::class.java))
                finish()
                return
            } else {
                firebaseAuth.signOut()
            }
        }

        setContentView(R.layout.activity_start)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val loginBtn = findViewById<Button>(R.id.btn_login)
        val guestBtn = findViewById<Button>(R.id.btn_guest)
        val loginEmailBtn = findViewById<Button>(R.id.btn_login_email)
        val emailEditText = findViewById<EditText>(R.id.et_email)
        val passwordEditText = findViewById<EditText>(R.id.et_password)
        val togglePasswordBtn = findViewById<ImageButton>(R.id.btn_toggle_password)
        val forgotPwText = findViewById<TextView>(R.id.tv_forgot_password)

        // 로그인 버튼 초기 상태: 비활성화 + 회색
        loginEmailBtn.isEnabled = false
        loginEmailBtn.backgroundTintList = ContextCompat.getColorStateList(
            this,
            R.color.button_inactive
        )

        loginBtn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        guestBtn.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(this, ScenarioSelectActivity::class.java))
            finish()
        }

        togglePasswordBtn.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            passwordEditText.transformationMethod = if (isPasswordVisible)
                HideReturnsTransformationMethod.getInstance()
            else
                PasswordTransformationMethod.getInstance()
            togglePasswordBtn.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye_open else R.drawable.ic_eye_closed
            )
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                val enabled = Patterns.EMAIL_ADDRESS.matcher(email).matches() && password.length >= 6
                loginEmailBtn.isEnabled = enabled
                loginEmailBtn.backgroundTintList = ContextCompat.getColorStateList(
                    this@StartActivity,
                    if (enabled) R.color.button_active else R.color.button_inactive
                )
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        emailEditText.addTextChangedListener(watcher)
        passwordEditText.addTextChangedListener(watcher)

        loginEmailBtn.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "올바른 이메일 형식이 아닙니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "비밀번호는 최소 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = firebaseAuth.currentUser
                        if (user != null && user.isEmailVerified) {
                            Toast.makeText(this, "로그인 성공", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, ScenarioSelectActivity::class.java))
                            finish()
                        } else {
                            firebaseAuth.signOut()
                            Toast.makeText(this, "이메일 인증이 필요합니다. 메일함을 확인해주세요.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val message = when (task.exception) {
                            is FirebaseAuthInvalidCredentialsException -> "입력하신 이메일 또는 비밀번호가 올바르지 않습니다."
                            is FirebaseAuthInvalidUserException -> "등록된 계정을 찾을 수 없습니다."
                            else -> "로그인 실패: ${task.exception?.localizedMessage}"
                        }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        loginEmailBtn.setOnLongClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "올바른 이메일 형식이 아닙니다.", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            if (password.length < 6) {
                Toast.makeText(this, "비밀번호는 최소 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            AlertDialog.Builder(this)
                .setTitle("회원가입")
                .setMessage("입력하신 이메일로 새 계정을 생성할까요?")
                .setPositiveButton("가입") { _, _ ->
                    firebaseAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                firebaseAuth.currentUser?.sendEmailVerification()
                                Toast.makeText(this, "회원가입 성공! 이메일 인증을 완료해주세요.", Toast.LENGTH_LONG).show()
                                firebaseAuth.signOut()
                            } else {
                                Toast.makeText(this, "회원가입 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                .setNegativeButton("취소", null)
                .show()
            true
        }

        forgotPwText.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "유효한 이메일을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "비밀번호 재설정 이메일을 보냈습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "전송 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        findViewById<TextView>(R.id.tv_go_register).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

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
