package com.trainning.jh_chronicles

import android.app.Activity
import android.util.Log

/** 과제 시연 때 Logcat에서 Activity 생명주기 순서를 한 태그로 확인하기 위한 함수입니다. */
fun Activity.logLifecycle(event: String) {
    Log.d("ActivityLifecycle", "${javaClass.simpleName} -> $event")
}
