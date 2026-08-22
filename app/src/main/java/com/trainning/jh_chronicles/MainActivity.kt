package com.trainning.jh_chronicles

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.trainning.jh_chronicles.databinding.ActivityMainBinding
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate - 대시보드 UI를 최초 생성")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 각 버튼은 대상 클래스가 분명한 '명시적 Intent'로 기능 Activity를 실행
        binding.diaryMenuBtn.setOnClickListener {
            startActivity(Intent(this, DiaryActivity::class.java))
        }
        binding.recordMenuBtn.setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }
        binding.weatherMenuBtn.setOnClickListener {
            startActivity(Intent(this, WeatherActivity::class.java))
        }
        binding.hospitalMenuBtn.setOnClickListener {
            startActivity(Intent(this, HospitalActivity::class.java))
        }
        binding.vaccinationMenuBtn.setOnClickListener {
            startActivity(Intent(this, VaccinationActivity::class.java))
        }

        // 로그아웃 버튼 실행
        binding.logOutBtn.setOnClickListener {
            AuthClass.auth.signOut()
            val intent = Intent(this, LogInActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        logLifecycle("onStart - 대시보드가 사용자에게 보이기 시작")
    }

    override fun onResume() {
        super.onResume()
        logLifecycle("onResume - 대시보드와 상호작용 가능")
    }

    override fun onPause() {
        logLifecycle("onPause - 다른 Activity가 앞에 나타남")
        super.onPause()
    }

    override fun onStop() {
        logLifecycle("onStop - 대시보드가 완전히 가려짐")
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        logLifecycle("onRestart - 기능 화면에서 대시보드로 돌아옴")
    }

    override fun onDestroy() {
        logLifecycle("onDestroy - 대시보드 객체 제거")
        super.onDestroy()
    }
}
