package com.trainning.jh_chronicles

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.jvm.java

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //헨들러에게 3초 뒤에 괄호안 코드를 실행해라
        Handler(Looper.getMainLooper()).postDelayed({
            if (AuthClass.checkAuth()) {
                Toast.makeText(this,"로그인 완료 : 이미 로그인 한 적이 있습니다", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                Toast.makeText(this,"회원가입 후 로그인을 진행 해 주세요", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LogInActivity::class.java))
            }
            finish()
        }, 3000)
    }
}