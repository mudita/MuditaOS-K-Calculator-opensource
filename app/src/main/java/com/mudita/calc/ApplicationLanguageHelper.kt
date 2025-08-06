package com.mudita.calc

import android.annotation.TargetApi
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.view.ContextThemeWrapper
import com.mudita.opencalculator.R
import java.util.Locale

class ApplicationLanguageHelper(base: Context) : ContextThemeWrapper(base, R.style.AppTheme) {
    companion object {
        fun wrap(ctx: Context): ContextThemeWrapper {
            var context = ctx
            val config = context.resources.configuration
                val locale = Locale.US
                Locale.setDefault(locale)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    setSystemLocale(config, locale)
                else
                    setSystemLocaleLegacy(config, locale)
                config.setLayoutDirection(locale)
                context = context.createConfigurationContext(config)
            return ApplicationLanguageHelper(context)
        }

        @SuppressWarnings("deprecation")
        fun setSystemLocaleLegacy(config: Configuration, locale: Locale) {
            config.locale = locale
        }

        @TargetApi(Build.VERSION_CODES.N)
        fun setSystemLocale(config: Configuration, locale: Locale) {
            config.setLocale(locale)
        }
    }
}
