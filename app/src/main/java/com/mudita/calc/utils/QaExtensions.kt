package com.mudita.calc.utils

import android.app.Activity
import android.app.AlertDialog
import android.view.MotionEvent
import android.view.Window
import com.mudita.opencalculator.BuildConfig

private const val REQUIRED_TAP_COUNT = 5
private const val TAP_INTERVAL_MS = 300

fun Activity.displayBuildInfoOnTap() {
    val windowCallback = window.callback

    if (BuildConfig.BUILD_TYPE != "qa") {
        return
    }

    window.callback = object : Window.Callback by windowCallback {
        private var tapCount = 0
        private var lastTapTime = 0L
        private val resetDelay = 3_000L

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val now = System.currentTimeMillis()
                val timeSinceLastTap = now - lastTapTime

                tapCount = if (timeSinceLastTap < TAP_INTERVAL_MS) {
                    tapCount + 1
                } else {
                    1
                }

                if (timeSinceLastTap > resetDelay) {
                    tapCount = 1
                }

                lastTapTime = now

                if (tapCount == REQUIRED_TAP_COUNT) {
                    AlertDialog.Builder(this@displayBuildInfoOnTap)
                        .setTitle("QA Version")
                        .setMessage("Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        .setPositiveButton("OK", null)
                        .show()
                    tapCount = 0
                }
            }
            return windowCallback.dispatchTouchEvent(event)
        }
    }
}
