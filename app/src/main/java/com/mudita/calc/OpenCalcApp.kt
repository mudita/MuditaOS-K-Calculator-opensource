package com.mudita.calc

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import com.mudita.opencalculator.BuildConfig.BUILD_TYPE
import com.mudita.opencalculator.BuildConfig.DEBUG
import com.mudita.opencalculator.BuildConfig.PROGUARD_UUID
import com.mudita.opencalculator.BuildConfig.SENTRY_DSN
import com.mudita.sentry.sdk.SentryInitializer

class OpenCalcApp : Application() {

    override fun onCreate() {
        super.onCreate()

        SentryInitializer.init(
            context = this,
            dsn = SENTRY_DSN,
            proguardUuid = PROGUARD_UUID,
            environment = BUILD_TYPE,
            isDebug = DEBUG,
        )

        // if the theme is overriding the system, the first creation doesn't work properly
        val forceDayNight = MyPreferences(this).forceDayNight
        if (forceDayNight != MODE_NIGHT_UNSPECIFIED && forceDayNight != MODE_NIGHT_FOLLOW_SYSTEM)
            setDefaultNightMode(forceDayNight)
    }

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(ApplicationLanguageHelper.wrap(newBase))
}
