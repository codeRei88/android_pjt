package com.trainning.jh_chronicles

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

//파이어베이스 기능을 앱 전체에서 공유하여 이용하기 위한 클래스
class AuthClass : Application() {

    companion object{ // 괄호안 변수 및 함수 스태틱화
        lateinit var auth : FirebaseAuth //파이어배이스 인증메니저 객체를 저장하는 그릇
        var userEmail : String? = null //현재 로그인된 사용자 이메일

        fun checkAuth() : Boolean { //인증표가 있는지 검사하는 메서드
            val currentUser = auth.currentUser // 현재 로그인한 유저의 정보를 변수그릇에 담아라
            return currentUser?.let { //로그인 유저가 있다면 이메일 저장하고, 이메일 인증됐다면 true 아니면 false 반환
                userEmail = it.email
                it.isEmailVerified // 이메일 인증 여부를 반환
            } ?: false
        }
    }
    override fun onCreate() { //앱 초기화시 실행되는 메서드
        super.onCreate() //안드로이드 시스템이 원래 기본적으로 해야 하는 초기 설정
        FirebaseApp.initializeApp(this) //파이어베이스앱 전원on
        auth = FirebaseAuth.getInstance() // 로그인/인증관련 전담 객체를 auth변수에 담음
    }
}