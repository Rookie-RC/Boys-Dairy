package com.rookierc.boysdairy.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Per-app language options. The choice itself is persisted by AppCompat, not by us. */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    CHINESE("zh-CN"),
    ENGLISH("en"),
}

fun currentAppLanguage(): AppLanguage {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty()) return AppLanguage.SYSTEM
    return when (locales.get(0)?.language) {
        "zh" -> AppLanguage.CHINESE
        "en" -> AppLanguage.ENGLISH
        else -> AppLanguage.SYSTEM
    }
}

/** Applies the language; AppCompat recreates the activity as needed. */
fun applyAppLanguage(language: AppLanguage) {
    val tag = language.languageTag
    val locales = if (tag == null) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(tag)
    }
    AppCompatDelegate.setApplicationLocales(locales)
}
