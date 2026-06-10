package com.michaldrabik.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoctaleCookieManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun saveCookies(cookies: String) {
    sharedPreferences.edit().putString(KEY_COOKIES, cookies).apply()
  }

  fun getCookies(): String? {
    val saved = sharedPreferences.getString(KEY_COOKIES, null)
    if (!saved.isNullOrBlank()) return saved

    // Fallback: Directly read from the system's cookie store for Moctale
    return android.webkit.CookieManager.getInstance().getCookie("https://www.moctale.in")
  }

  fun clearCookies() {
    sharedPreferences.edit().remove(KEY_COOKIES).apply()
  }

  fun isLoggedIn(): Boolean = getCookies() != null

  companion object {
    private const val PREFS_NAME = "moctale_prefs"
    private const val KEY_COOKIES = "moctale_cookies"
  }
}
