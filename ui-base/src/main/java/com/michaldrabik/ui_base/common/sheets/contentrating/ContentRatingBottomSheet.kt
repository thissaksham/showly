package com.michaldrabik.ui_base.common.sheets.contentrating

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.michaldrabik.ui_base.BaseBottomSheetFragment
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.databinding.ViewContentRatingSheetBinding
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.openWebUrl
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_navigation.java.NavigationArgs
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ContentRatingBottomSheet : BaseBottomSheetFragment(R.layout.view_content_rating_sheet) {

  private val binding by viewBinding(ViewContentRatingSheetBinding::bind)

  private val url by lazy { arguments?.getString(NavigationArgs.ARG_URL) ?: "" }
  private val bundleTitle by lazy { arguments?.getString("title") ?: "" }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (dialog as? BottomSheetDialog)?.behavior?.isDraggable = false
    setupView()
    loadContent()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupView() {
    binding.contentRatingTitle.text = if (bundleTitle.isNotBlank()) "IMDb Parents Guide - $bundleTitle" else "IMDb Parents Guide"
    binding.contentRatingExternalLink.onClick { openWebUrl(url) }
    binding.contentRatingRetryButton.onClick {
      binding.contentRatingRetryButton.visibleIf(false)
      binding.contentRatingErrorText.visibleIf(false)
      loadContent()
    }

    with(binding.contentRatingWebView) {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      setBackgroundColor(android.graphics.Color.TRANSPARENT)

      isVerticalScrollBarEnabled = false
      isHorizontalScrollBarEnabled = false
      overScrollMode = View.OVER_SCROLL_NEVER

      webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
          super.onPageStarted(view, url, favicon)
          view?.visibility = View.INVISIBLE
          binding.contentRatingErrorText.visibleIf(false)
          binding.contentRatingRetryButton.visibleIf(false)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          super.onPageFinished(view, url)
          injectIsolationScript()
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

  private fun loadContent() {
    binding.contentRatingProgress.visibleIf(true)
    binding.contentRatingWebView.visibleIf(false, gone = false)
    binding.contentRatingWebView.loadUrl(url)
  }

  private fun injectIsolationScript() {
    val script = """
      (function() {
        function findAndIsolateContentRating() {
          try {
            var baseNode = null;
            var minLen = 999999;
            var all = document.getElementsByTagName('*');

            // Find smallest DOM container with actual advisory ratings (Mild/Moderate/Severe/None)
            // This perfectly distinguishes the rating chart from IMDb's hidden Jump Menu link lists
            for (var i = 0; i < all.length; i++) {
              var el = all[i];
              var text = el.textContent || "";
              if (text.indexOf('Sex & Nudity') !== -1 && text.indexOf('Violence') !== -1) {
                var hasRating = text.indexOf('Mild') !== -1 || text.indexOf('Moderate') !== -1 || 
                                text.indexOf('Severe') !== -1 || text.indexOf('None') !== -1;
                if (hasRating) {
                  var lower = text.toLowerCase();
                  if (lower.indexOf('jump to') === -1 && lower.indexOf('navbar') === -1) {
                    if (text.length < minLen) {
                      minLen = text.length;
                      baseNode = el;
                    }
                  }
                }
              }
            }

            var targetSection = null;
            if (baseNode) {
              targetSection = baseNode;
              while (targetSection.parentElement && 
                     targetSection.parentElement !== document.body && 
                     targetSection.parentElement.textContent.length < 1500) {
                targetSection = targetSection.parentElement;
              }
            }

            if (targetSection) {
              // 1. Force truly transparent backgrounds and flexbox center alignment
              document.documentElement.style.backgroundColor = 'transparent';
              document.documentElement.style.height = '100%';
              document.documentElement.style.overflow = 'hidden';

              document.body.style.backgroundColor = 'transparent';
              document.body.style.margin = '0';
              document.body.style.padding = '0';
              document.body.style.overflow = 'hidden';
              document.body.style.display = 'flex';
              document.body.style.flexDirection = 'column';
              document.body.style.justifyContent = 'center';
              document.body.style.alignItems = 'center';
              document.body.style.minHeight = '100vh';

              var bodyChildren = document.body.children;
              for (var i = 0; i < bodyChildren.length; i++) {
                if (bodyChildren[i] !== targetSection) {
                  bodyChildren[i].style.display = 'none';
                }
              }

              document.body.appendChild(targetSection);

              targetSection.style.display = 'block';
              targetSection.style.visibility = 'visible';
              targetSection.style.backgroundColor = 'transparent';
              targetSection.style.margin = 'auto';
              targetSection.style.padding = '8px 0px';
              targetSection.style.width = '100%';
              targetSection.style.boxSizing = 'border-box';

              // 2. Hide redundant "Content rating" or "Parents guide" headings inside the scraped box
              var headings = targetSection.querySelectorAll('h1, h2, h3, h4, span, div');
              for (var h = 0; h < headings.length; h++) {
                var hText = headings[h].textContent ? headings[h].textContent.trim().toLowerCase() : "";
                if (hText === 'content rating' || hText === 'parents guide') {
                  var hElem = headings[h];
                  // Hide surrounding header container row if it contains only this heading
                  if (hElem.parentElement && hElem.parentElement !== targetSection && (hElem.parentElement.textContent || "").trim().length < 35) {
                    hElem.parentElement.style.display = 'none';
                  } else {
                    hElem.style.display = 'none';
                  }
                }
              }

              // 3. Style descendants: make containers transparent but keep colored vertical pills
              var descendants = targetSection.getElementsByTagName('*');
              for (var j = 0; j < descendants.length; j++) {
                var d = descendants[j];
                d.style.visibility = 'visible';

                var hasText = d.textContent && d.textContent.trim().length > 0;
                if (hasText) {
                  // Text wrapper or container row -> strip background
                  d.style.backgroundColor = 'transparent';
                  d.style.background = 'transparent';
                  d.style.boxShadow = 'none';
                } else {
                  // Empty node (colored indicator pill). Keep color unless black/white/grey.
                  var style = window.getComputedStyle(d);
                  var bg = style.backgroundColor;
                  if (bg && (bg.includes('0, 0, 0') || bg.includes('255, 255, 255') || bg.includes('28, 28, 30'))) {
                    d.style.backgroundColor = 'transparent';
                  }
                }

                // Adjust text colors for dark theme
                var tag = d.tagName;
                if (tag === 'SPAN' || tag === 'P' || tag === 'H1' || tag === 'H2' || 
                    tag === 'H3' || tag === 'H4' || tag === 'A' || tag === 'LI' || 
                    tag === 'DIV' || tag === 'LABEL') {
                  var st = window.getComputedStyle(d);
                  var cColor = st.color;
                  if (cColor && (cColor.includes('0, 0, 0') || cColor.includes('51, 51, 51') || 
                      cColor.includes('17, 17, 17') || cColor.includes('rgb(0') || cColor === 'rgb(0, 0, 0)')) {
                    d.style.color = '#E5E5E7';
                  }
                }
              }

              // 4. Disable all clicks & navigation (make chart purely read-only)
              targetSection.style.pointerEvents = 'none';
              var links = targetSection.querySelectorAll('a, button, [role="button"]');
              for (var k = 0; k < links.length; k++) {
                var linkElem = links[k];
                linkElem.removeAttribute('href');
                linkElem.style.pointerEvents = 'none';
                var linkText = (linkElem.textContent || "").trim().toLowerCase();
                if (linkText === 'edit' || linkText.includes('see more') || linkText.includes('add content')) {
                  linkElem.style.display = 'none';
                }
              }

              // 5. Hide trailing right arrows / chevrons (>)
              var chevrons = targetSection.querySelectorAll('svg, path, i, img, [class*="chevron"], [class*="arrow"], [data-testid*="chevron"]');
              for (var c = 0; c < chevrons.length; c++) {
                chevrons[c].style.display = 'none';
              }

              // 4. Extract clean title from IMDb metadata
              var rawTitle = document.title || "";
              var og = document.querySelector('meta[property="og:title"]');
              if (og && og.content) rawTitle = og.content;
              var cleanTitle = rawTitle.replace(/ - parents guide.*/i, '').replace(/ - imdb.*/i, '').trim();

              document.documentElement.style.visibility = 'visible';
              return 'SUCCESS###' + cleanTitle;
            }
          } catch(e) { console.log(e); }
          return 'PENDING';
        }

        return findAndIsolateContentRating();
      })();
    """.trimIndent()

    pollForContent(script, attempt = 0)
  }

  private fun pollForContent(script: String, attempt: Int) {
    if (view == null) return
    val webView = binding.contentRatingWebView
    webView.evaluateJavascript(script) { result ->
      if (view == null) return@evaluateJavascript
      when {
        result.contains("SUCCESS") -> {
          val cleanRes = result.trim('"').replace("\\u0027", "'").replace("\\'", "'")
          val parts = cleanRes.split("###")
          val displayTitle = if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else bundleTitle
          if (displayTitle.isNotBlank()) {
            binding.contentRatingTitle.text = "IMDb Parents Guide - ${displayTitle}"
          }
          showContent()
        }
        attempt < MAX_ISOLATE_ATTEMPTS -> webView.postDelayed({ pollForContent(script, attempt + 1) }, ISOLATE_POLL_MS)
        else -> showNotFoundError()
      }
    }
  }

  private fun showContent() {
    binding.contentRatingProgress.visibleIf(false)
    binding.contentRatingWebView.visibility = View.VISIBLE
  }

  private fun showNotFoundError() {
    binding.contentRatingProgress.visibleIf(false)
    binding.contentRatingWebView.visibleIf(false)
    binding.contentRatingErrorText.visibleIf(true)
    binding.contentRatingRetryButton.visibleIf(true)
  }

  companion object {
    private const val MAX_ISOLATE_ATTEMPTS = 30
    private const val ISOLATE_POLL_MS = 400L
  }
}
