package com.michaldrabik.showly2.ui.moctale

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.michaldrabik.common.MoctaleCookieManager
import com.michaldrabik.showly2.databinding.ActivityMoctaleLoginBinding
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MoctaleLoginActivity : AppCompatActivity() {

  @Inject
  lateinit var moctaleCookieManager: MoctaleCookieManager

  private lateinit var binding: ActivityMoctaleLoginBinding

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMoctaleLoginBinding.inflate(layoutInflater)
    setContentView(binding.root)

    with(binding.moctaleWebView) {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true // Enable DOM storage for modern apps
      webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
          super.onPageStarted(view, url, favicon)
          binding.moctaleLoginProgress.visibleIf(true)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          super.onPageFinished(view, url)
          binding.moctaleLoginProgress.visibleIf(false)

          url?.let {
            val cookies = CookieManager.getInstance().getCookie(it)
            // If the user is on the main site (not login/signup) and we have any cookies, it's a success
            val isMainSite = !it.contains("login") && !it.contains("signup") && !it.contains("accounts")
            if (cookies != null && (isMainSite || cookies.contains("session") || cookies.contains("token"))) {
              moctaleCookieManager.saveCookies(cookies)
              setResult(RESULT_OK)
              finish()
            }
          }
        }
      }
      loadUrl("https://www.moctale.in/accounts/login")
    }
  }

  companion object {
    fun createIntent(context: Context): Intent = Intent(context, MoctaleLoginActivity::class.java)
  }
}
