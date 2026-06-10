package com.michaldrabik.data_remote.moctale

import com.michaldrabik.ui_model.MoctaleMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class MoctaleScraper @Inject constructor(
  @Named("okHttpBase") private val okHttpClient: OkHttpClient,
) {

  suspend fun fetchMeter(url: String, cookies: String): MoctaleMeter? = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url(url)
        .addHeader("Cookie", cookies)
        .addHeader("User-Agent", USER_AGENT)
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        val html = response.body?.string() ?: return@withContext null
        
        // Log a snippet of HTML to see if we are actually getting the content or a login page
        if (html.contains("Login") || html.contains("accounts/login")) {
           Timber.e("Moctale Scraper: Received Login page instead of content. Cookies might be invalid or expired.")
        }

        val doc = Jsoup.parse(html)
        
        // Main percentage - Look for the large text or fallback to anything matching the pattern
        val meterElement = doc.select("div.text-\\[42px\\]").firstOrNull()
          ?: doc.getElementsContainingOwnText("%").firstOrNull { it.text().trim().matches(Regex("^\\d+%$")) }
        
        val percentage = meterElement?.text()?.trim() ?: "--%"
        
        // Vote count text (e.g. "3112/3876 Votes")
        val voteText = doc.select("div.text-\\[20px\\]").firstOrNull { it.text().contains("Votes") }?.text()?.trim() ?: "No votes found"
        
        // Breakdown percentages - Try aria-label first, then fallback to searching siblings of the labels
        val perfection = getBreakdownValue(doc, "Perfection")
        val goForIt = getBreakdownValue(doc, "Go for it")
        val timepass = getBreakdownValue(doc, "Timepass")
        val skip = getBreakdownValue(doc, "Skip")
        
        val meter = MoctaleMeter(
          percentage = percentage,
          voteText = voteText,
          perfection = perfection,
          goForIt = goForIt,
          timepass = timepass,
          skip = skip
        )
        
        Timber.d("Scraped Moctale Meter Result: $meter")
        meter
      }
    } catch (e: Exception) {
      Timber.e(e, "Error scraping Moctale Meter from $url")
      null
    }
  }

  private fun getBreakdownValue(doc: org.jsoup.nodes.Document, label: String): String {
    // Strategy 1: Look for the specific aria-label attribute
    val fromAria = doc.select("[aria-label^=$label]").firstOrNull()?.attr("aria-label")?.split(":")?.lastOrNull()?.trim()
    if (!fromAria.isNullOrBlank()) return fromAria
    
    // Strategy 2: Find the label text and look for a percentage in the same parent container
    val elementWithText = doc.getElementsContainingOwnText(label).firstOrNull()
    val parent = elementWithText?.parent()
    val fromSibling = parent?.getElementsContainingOwnText("%")?.firstOrNull()?.text()?.trim()
    
    return fromSibling ?: "0%"
  }

  companion object {
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
  }
}
