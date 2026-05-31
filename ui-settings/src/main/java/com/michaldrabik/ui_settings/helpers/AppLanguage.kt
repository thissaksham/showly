package com.michaldrabik.ui_settings.helpers

import androidx.annotation.StringRes
import com.michaldrabik.ui_settings.R
import timber.log.Timber

enum class AppLanguage(
  val code: String,
  val displayNameRaw: String,
  @StringRes val displayName: Int,
) {
  ENGLISH("en", "English", R.string.textLanguageEnglish),
  ;

  companion object {
    fun fromCode(code: String): AppLanguage {
      Timber.d("Looking for AppLanguage with code: $code")
      return entries.firstOrNull { it.code == code } ?: ENGLISH
    }
  }
}
