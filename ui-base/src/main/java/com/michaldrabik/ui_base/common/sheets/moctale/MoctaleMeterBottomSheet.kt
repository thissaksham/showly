package com.michaldrabik.ui_base.common.sheets.moctale

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import com.michaldrabik.common.MoctaleCookieManager
import com.michaldrabik.ui_base.BaseBottomSheetFragment
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.databinding.ViewMoctaleMeterSheetBinding
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.openWebUrl
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_navigation.java.NavigationArgs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MoctaleMeterBottomSheet : BaseBottomSheetFragment(R.layout.view_moctale_meter_sheet) {

  @Inject
  lateinit var moctaleCookieManager: MoctaleCookieManager

  private val binding by viewBinding(ViewMoctaleMeterSheetBinding::bind)

  private val url by lazy { arguments?.getString(NavigationArgs.ARG_URL) ?: "" }

  private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      loadMeter()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    loadMeter()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupView() {
    binding.moctaleMeterExternalLink.onClick { openWebUrl(url) }
    binding.moctaleMeterLoginButton.onClick {
      try {
        val intent = android.content.Intent(requireContext(), Class.forName("com.michaldrabik.showly2.ui.moctale.MoctaleLoginActivity"))
        loginLauncher.launch(intent)
      } catch (e: Exception) {
        android.util.Log.e("MoctaleMeter", "Failed to launch login activity", e)
      }
    }

    with(binding.moctaleMeterWebView) {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      setBackgroundColor(android.graphics.Color.TRANSPARENT)
      
      // Disable scrollbars
      isVerticalScrollBarEnabled = false
      isHorizontalScrollBarEnabled = false
      overScrollMode = View.OVER_SCROLL_NEVER
      
      webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
          super.onPageStarted(view, url, favicon)
          // Ensure it stays hidden and transparent during load
          view?.visibility = View.INVISIBLE
          binding.moctaleMeterErrorText.visibleIf(false)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          super.onPageFinished(view, url)
          injectSurgicalScript()
        }

        override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
          super.onReceivedError(view, request, error)
          if (request?.isForMainFrame == true) {
            showNotFoundError()
          }
        }

        @SuppressLint("NewApi")
        override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
          super.onReceivedHttpError(view, request, errorResponse)
          if (request?.isForMainFrame == true && (errorResponse?.statusCode == 404 || errorResponse?.statusCode == 500)) {
            showNotFoundError()
          }
        }
      }
    }
  }

  private fun loadMeter() {
    val cookies = moctaleCookieManager.getCookies()
    if (cookies == null) {
      showLoginPrompt()
      return
    }

    // Sync cookies to WebView
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(binding.moctaleMeterWebView, true)
    
    // Clear and set to ensure freshness
    cookieManager.setCookie(url, cookies)
    cookieManager.flush()

    binding.moctaleMeterProgress.visibleIf(true)
    binding.moctaleMeterWebView.visibleIf(false, gone = false)
    binding.moctaleMeterLoginPrompt.visibleIf(false)

    binding.moctaleMeterWebView.loadUrl(url)
  }

  private fun injectSurgicalScript() {
    val script = """
      (function() {
        function findMeterAndIsolate() {
          try {
            // Find any element containing the text 'Moctale Meter'
            var targetText = 'Moctale Meter';
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
            var node;
            var meterCard = null;

            while(node = walker.nextNode()) {
              if (node.textContent.includes(targetText)) {
                // Found the text, now find the parent card (usually has 'border' or 'card' in class)
                var current = node.parentElement;
                while (current && current !== document.body) {
                  var className = current.className || "";
                  if (className.includes('border') || className.includes('text-card-foreground') || className.includes('rounded')) {
                    meterCard = current;
                    // Keep looking up to find the outermost container for the whole card
                    if (current.parentElement && (current.parentElement.className.includes('border') || current.parentElement.className.includes('rounded'))) {
                        meterCard = current.parentElement;
                    }
                    break;
                  }
                  current = current.parentElement;
                }
                if (meterCard) break;
              }
            }

            if (meterCard) {
              var modalColor = '#222327';
              
              // 1. Force the background colors immediately to match the app
              document.documentElement.style.backgroundColor = modalColor;
              document.body.style.backgroundColor = modalColor;
              document.body.style.margin = '0';
              document.body.style.padding = '0';
              document.body.style.overflow = 'hidden';
              document.documentElement.style.overflow = 'hidden';
              
              // 2. Hide all other children of body
              var bodyChildren = document.body.children;
              for (var i = 0; i < bodyChildren.length; i++) {
                 if (bodyChildren[i] !== meterCard) {
                     bodyChildren[i].style.display = 'none';
                 }
              }
              
              // 3. Move card to top level
              document.body.appendChild(meterCard);
              
              // 4. Style the card for native look
              meterCard.style.display = 'block';
              meterCard.style.visibility = 'visible';
              meterCard.style.backgroundColor = 'transparent';
              meterCard.style.background = 'transparent';
              meterCard.style.boxShadow = 'none';
              meterCard.style.border = 'none';
              meterCard.style.margin = '0';
              meterCard.style.padding = '16px';
              meterCard.style.width = '100%';
              meterCard.style.height = 'auto';

              // 5. Recursively fix transparency but keep colored indicator dots
              var all = meterCard.getElementsByTagName("*");
              for (var j=0; j < all.length; j++) {
                var el = all[j];
                el.style.visibility = 'visible';
                
                // If it has a specific background color (the dots), don't touch it
                var style = window.getComputedStyle(el);
                var bg = style.backgroundColor;
                var isIndicator = bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)' && 
                                  !bg.includes('0, 0, 0') && !bg.includes('8, 8, 8');
                
                if (!isIndicator && !el.querySelector('canvas') && el.tagName !== 'CANVAS') {
                    el.style.backgroundColor = 'transparent';
                    el.style.background = 'transparent';
                    el.style.boxShadow = 'none';
                    el.style.border = 'none';
                }
              }

              document.documentElement.style.visibility = 'visible';
              return "SUCCESS";
            }
          } catch(e) { console.log(e); }
          return "PENDING";
        }

        var res = findMeterAndIsolate();
        if (res !== "SUCCESS") {
           setTimeout(findMeterAndIsolate, 500);
           setTimeout(findMeterAndIsolate, 1500);
        }
        return res;
      })();
    """.trimIndent()

    binding.moctaleMeterWebView.evaluateJavascript(script) {
      if (it.contains("SUCCESS")) {
          showContent()
      } else {
          // If it's still pending, let's wait a bit and check one more time from Android side
          binding.moctaleMeterWebView.postDelayed({
             binding.moctaleMeterWebView.evaluateJavascript("(function(){ return document.body.innerHTML.includes('Moctale Meter') ? 'SUCCESS' : 'FAIL'; })();") { retryResult ->
                if (retryResult.contains("SUCCESS")) {
                   showContent()
                } else {
                   // Final fallback: just show the webview after 4 seconds regardless
                   binding.moctaleMeterWebView.postDelayed({ showContent() }, 2000)
                }
             }
          }, 1500)
      }
    }
  }

  private fun showContent() {
    binding.moctaleMeterProgress.visibleIf(false)
    binding.moctaleMeterWebView.visibility = View.VISIBLE
  }

  private fun showNotFoundError() {
    binding.moctaleMeterProgress.visibleIf(false)
    binding.moctaleMeterWebView.visibleIf(false)
    binding.moctaleMeterErrorText.visibleIf(true)
  }

  private fun showLoginPrompt() {
    binding.moctaleMeterWebView.visibleIf(false, gone = false)
    binding.moctaleMeterLoginPrompt.visibleIf(true)
  }

  override fun onResume() {
    super.onResume()
    // Refresh only if we were showing login prompt and now have cookies
    if (binding.moctaleMeterLoginPrompt.visibility == View.VISIBLE && moctaleCookieManager.isLoggedIn()) {
      loadMeter()
    }
  }
}
