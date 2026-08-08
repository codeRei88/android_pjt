package com.trainning.jh_chronicles

import android.R.attr.password
import android.R.attr.text
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.trainning.jh_chronicles.AuthClass.Companion.auth
import com.trainning.jh_chronicles.AuthClass.Companion.userEmail
import com.trainning.jh_chronicles.databinding.ActivityLogInBinding

// 이메일/비밀번호 회원가입과 로그인을 처리하는 화면
class LogInActivity : AppCompatActivity() {

    // 액티비티가 처음 생성될 때 호출되는 생명주기 메서드
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // activity_log_in.xml의 뷰를 코드에서 사용하기 위한 View Binding 객체
        val binding = ActivityLogInBinding.inflate(layoutInflater)

        // 상태 표시줄과 내비게이션 바 영역까지 화면을 확장
        enableEdgeToEdge()

        // View Binding으로 만든 레이아웃을 액티비티 화면으로 설정
        setContentView(binding.root)

        // 화면 내용이 상태 표시줄과 내비게이션 바에 가리지 않도록 시스템 바 여백 적용
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            // 시스템 바가 차지하는 상하좌우 크기를 가져옴
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 시스템 바 크기만큼 루트 뷰에 패딩 설정
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)

            // 처리한 WindowInsets 반환
            insets
        }

        // 회원가입 모드에서만 사용할 시스템 뒤로가기 콜백
        // 처음에는 로그인 모드이므로 false를 전달하여 비활성화 상태로 생성
        val registerBackCallback = object : OnBackPressedCallback(false) {

            // 회원가입 모드에서 시스템 뒤로가기 또는 뒤로가기 제스처를 사용하면 호출됨
            override fun handleOnBackPressed() {
                // 회원가입 시작 버튼과 로그인 버튼을 다시 표시
                binding.registerStartBtn.visibility = View.VISIBLE
                binding.loginBtn.visibility = View.VISIBLE

                // 실제 가입 요청 버튼을 숨겨 로그인 모드로 복귀
                binding.registerBtn.visibility = View.INVISIBLE

                // 로그인 모드로 돌아왔으므로 회원가입용 뒤로가기 처리 비활성화
                isEnabled = false
            }
        }

        // 위에서 만든 뒤로가기 콜백을 현재 액티비티에 등록
        onBackPressedDispatcher.addCallback(this, registerBackCallback)

        // 상단의 '회원가입' 버튼을 누르면 로그인 모드에서 회원가입 모드로 전환
        binding.registerStartBtn.setOnClickListener {
            // 회원가입 모드에서는 시작 버튼과 로그인 버튼을 숨김
            binding.registerStartBtn.visibility = View.GONE
            binding.loginBtn.visibility = View.GONE

            // 이메일과 비밀번호로 실제 가입을 요청하는 버튼 표시
            binding.registerBtn.visibility = View.VISIBLE

            // 뒤로가기를 누르면 로그인 모드로 돌아갈 수 있도록 콜백 활성화
            registerBackCallback.isEnabled = true
        }

        // 회원가입 모드의 '가입' 버튼을 눌렀을 때 실행
        binding.registerBtn.setOnClickListener {
            // 입력한 이메일과 비밀번호의 앞뒤 공백을 제거하여 저장
            val userEmail = binding.emailArea.text.toString().trim()
            val userPassword = binding.passwordArea.text.toString().trim()

            // 이메일이나 비밀번호 중 하나라도 비어 있으면 회원가입 요청 중단
            if(userEmail.isEmpty() || userPassword.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Firebase 이메일/비밀번호 인증은 6자리 이상의 비밀번호가 필요함
            if (userPassword.length < 6) {
                Toast.makeText(this, "비밀번호는 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 이전에 로그인된 Firebase 사용자가 남아 있을 수 있으므로 로그아웃
            auth.signOut()

            // Firebase Authentication에 새 이메일/비밀번호 계정 생성 요청
            auth.createUserWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener(this) { task ->
                    // 계정 생성에 성공한 경우
                    if (task.isSuccessful) {
                        // 계정 생성 직후 로그인된 사용자에게 이메일 인증 메일 발송 요청
                        auth.currentUser?.sendEmailVerification()
                            ?.addOnCompleteListener { mailTask ->
                                // Firebase가 인증 메일 발송 요청을 성공적으로 처리한 경우
                                if(mailTask.isSuccessful){
                                    Toast.makeText(this,"인증 메일을 보냈습니다, 메일인증 후 로그인 해 주세요.", Toast.LENGTH_SHORT).show()

                                    // 이메일 인증 전에는 로그인 상태를 유지하지 않도록 로그아웃
                                    auth.signOut()

                                    // 회원가입 처리가 끝났으므로 버튼들을 로그인 모드로 되돌림
                                    binding.registerStartBtn.visibility = View.VISIBLE
                                    binding.loginBtn.visibility = View.VISIBLE
                                    binding.registerBtn.visibility = View.INVISIBLE

                                    // 로그인 모드에서는 회원가입용 뒤로가기 콜백 비활성화
                                    registerBackCallback.isEnabled = false
                                }
                            }
                    } else {
                        // 이미 사용 중인 이메일 등의 이유로 계정 생성에 실패하면 오류 표시
                        Toast.makeText(this, "회원가입 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }

        }

        // 로그인 모드의 '로그인' 버튼을 눌렀을 때 실행
        binding.loginBtn.setOnClickListener {
            // 입력한 이메일과 비밀번호의 앞뒤 공백을 제거하여 저장
            val userEmail = binding.emailArea.text.toString().trim()
            val userPassword = binding.passwordArea.text.toString().trim()

            // 이메일이나 비밀번호 중 하나라도 비어 있으면 로그인 요청 중단
            if(userEmail.isEmpty() || userPassword.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Firebase Authentication에 입력한 이메일과 비밀번호로 로그인 요청
            auth.signInWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener(this) { task ->
                    // 이메일과 비밀번호가 일치하여 로그인에 성공한 경우
                    if (task.isSuccessful) {
                        // 방금 로그인한 Firebase 사용자 객체 가져오기
                        val user = auth.currentUser

                        // 서버의 최신 이메일 인증 상태를 가져오기 위해 사용자 정보 새로고침
                        user?.reload()?.addOnCompleteListener { reloadTask ->
                            // 사용자 정보 새로고침에 성공한 경우
                            if(reloadTask.isSuccessful) {
                                // 사용자가 인증 메일의 링크를 눌러 이메일 인증을 완료했는지 확인
                                if(user.isEmailVerified) {
                                    Toast.makeText(this,"로그인을 완료했습니다", Toast.LENGTH_SHORT).show()

                                    // 인증까지 완료된 사용자를 메인 화면으로 이동
                                    val intent = Intent(this, MainActivity::class.java)
                                    startActivity(intent)

                                    // 뒤로가기로 로그인 화면에 다시 돌아오지 않도록 현재 화면 종료
                                    finish()
                                } else {
                                    // 계정은 존재하지만 이메일 인증이 완료되지 않은 경우 로그인 차단
                                    Toast.makeText(this,"이메일 인증을 완료 후 로그인 해 주세요",Toast.LENGTH_SHORT).show()

                                    // 인증 전 사용자가 로그인 상태로 남지 않도록 로그아웃
                                    auth.signOut()
                                }
                            } else {
                                // 네트워크 문제 등으로 최신 사용자 정보를 가져오지 못한 경우 오류 표시
                                Toast.makeText(this,"사용자 정보를 가져오지 못했습니다 : ${reloadTask.exception?.message}", Toast.LENGTH_SHORT).show()
                            }

                        }

                    } else {
                        // 계정이 없거나 비밀번호가 틀린 경우 Firebase 오류 메시지 표시
                        Toast.makeText(this,"로그인에 실패했습니다 : ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

    }
}
