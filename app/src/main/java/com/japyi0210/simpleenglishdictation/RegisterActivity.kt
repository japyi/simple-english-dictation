package com.japyi0210.simpleenglishdictation

import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class RegisterActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        firebaseAuth = FirebaseAuth.getInstance()

        val emailEditText = findViewById<EditText>(R.id.et_register_email)
        val passwordEditText = findViewById<EditText>(R.id.et_register_password)
        val confirmEditText = findViewById<EditText>(R.id.et_register_confirm_password)
        val registerBtn = findViewById<Button>(R.id.btn_register)
        val backToLoginText = findViewById<TextView>(R.id.tv_register_back)

        val togglePassword = findViewById<ImageButton>(R.id.btn_toggle_password)
        val toggleConfirm = findViewById<ImageButton>(R.id.btn_toggle_confirm)

        var isPasswordVisible = false
        var isConfirmVisible = false

        togglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePassword.setImageResource(R.drawable.ic_eye_open)
            } else {
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePassword.setImageResource(R.drawable.ic_eye_closed)
            }
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        toggleConfirm.setOnClickListener {
            isConfirmVisible = !isConfirmVisible
            if (isConfirmVisible) {
                confirmEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                toggleConfirm.setImageResource(R.drawable.ic_eye_open)
            } else {
                confirmEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                toggleConfirm.setImageResource(R.drawable.ic_eye_closed)
            }
            confirmEditText.setSelection(confirmEditText.text.length)
        }

        registerBtn.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmEditText.text.toString().trim()

            // 입력 유효성 검사
            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "모든 칸을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "올바른 이메일 형식을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "비밀번호는 최소 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 회원가입 처리
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // 이메일 인증 메일 발송
                        firebaseAuth.currentUser?.sendEmailVerification()

                        Toast.makeText(this, "회원가입 완료! 이메일 인증 메일을 확인해주세요.", Toast.LENGTH_LONG).show()

                        // 인증 전 로그인 방지를 위해 로그아웃
                        firebaseAuth.signOut()

                        // 로그인 화면으로 돌아감
                        finish()
                    } else {
                        Toast.makeText(this, "회원가입 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // 로그인 화면으로 돌아가기
        backToLoginText.setOnClickListener {
            finish()
        }
    }
}
