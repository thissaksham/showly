package com.michaldrabik.ui_base.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import com.michaldrabik.common.Config.SPOILERS_RATINGS_HIDE_SYMBOL
import com.michaldrabik.ui_base.databinding.ViewRatingsStripBinding
import com.michaldrabik.ui_base.utilities.extensions.colorFromAttr
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_model.Ratings

class RatingsStripView : LinearLayout {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewRatingsStripBinding.inflate(LayoutInflater.from(context), this)

  var onTraktClick: ((Ratings) -> Unit)? = null
  var onImdbClick: ((Ratings) -> Unit)? = null
  var onMetaClick: ((Ratings) -> Unit)? = null
  var onRottenClick: ((Ratings) -> Unit)? = null
  var onMoctaleClick: ((Ratings) -> Unit)? = null

  private val colorPrimary by lazy { context.colorFromAttr(android.R.attr.textColorPrimary) }
  private val colorSecondary by lazy { context.colorFromAttr(android.R.attr.textColorSecondary) }

  private lateinit var ratings: Ratings

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    orientation = HORIZONTAL
    gravity = Gravity.TOP
  }

  fun bind(ratings: Ratings) {
    if (this::ratings.isInitialized && this.ratings == ratings) return
    this.ratings = ratings
    with(binding) {
      bindValue(
        ratingsValue = ratings.trakt,
        layoutView = viewRatingsStripTrakt,
        valueView = viewRatingsStripTraktValue,
        progressView = viewRatingsStripTraktProgress,
        linkView = viewRatingsStripTraktLinkIcon,
        isHidden = ratings.isHidden,
        isTapToReveal = ratings.isTapToReveal,
        callback = onTraktClick,
      )
      bindValue(
        ratingsValue = ratings.imdb,
        layoutView = viewRatingsStripImdb,
        valueView = viewRatingsStripImdbValue,
        progressView = viewRatingsStripImdbProgress,
        linkView = viewRatingsStripImdbLinkIcon,
        isHidden = ratings.isHidden,
        isTapToReveal = ratings.isTapToReveal,
        callback = onImdbClick,
      )
      bindValue(
        ratingsValue = ratings.metascore,
        layoutView = viewRatingsStripMeta,
        valueView = viewRatingsStripMetaValue,
        progressView = viewRatingsStripMetaProgress,
        linkView = viewRatingsStripMetaLinkIcon,
        isHidden = ratings.isHidden,
        isTapToReveal = ratings.isTapToReveal,
        callback = onMetaClick,
      )
      bindValue(
        ratingsValue = ratings.rottenTomatoes,
        layoutView = viewRatingsStripRotten,
        valueView = viewRatingsStripRottenValue,
        progressView = viewRatingsStripRottenProgress,
        linkView = viewRatingsStripRottenLinkIcon,
        isHidden = ratings.isHidden,
        isTapToReveal = ratings.isTapToReveal,
        callback = onRottenClick,
      )
      bindValue(
        ratingsValue = if (ratings.moctaleUrl != null) Ratings.Value("", false) else null,
        layoutView = viewRatingsStripMoctale!!,
        valueView = viewRatingsStripMoctaleValue!!,
        progressView = viewRatingsStripMoctaleProgress!!,
        linkView = viewRatingsStripMoctaleLinkIcon!!,
        isHidden = false,
        isTapToReveal = false,
        callback = { onMoctaleClick?.invoke(it) },
      )
    }
  }

  private fun bindValue(
    ratingsValue: Ratings.Value?,
    layoutView: View,
    valueView: TextView,
    progressView: View,
    linkView: View,
    isHidden: Boolean,
    isTapToReveal: Boolean,
    callback: ((Ratings) -> Unit)?,
  ) {
    val rating = ratingsValue?.value
    val isLoading = ratingsValue?.isLoading == true
    with(valueView) {
      visibleIf(!isLoading && !rating.isNullOrBlank(), gone = false)
      text = if (isHidden) {
        tag = rating
        SPOILERS_RATINGS_HIDE_SYMBOL
      } else {
        rating
      }
      setTextColor(if (rating != null) colorPrimary else colorSecondary)
    }

    with(layoutView) {
      onClick {
        if (!this@RatingsStripView.ratings.isAnyLoading()) {
          if (isHidden && isTapToReveal && !rating.isNullOrBlank() && valueView.text == SPOILERS_RATINGS_HIDE_SYMBOL) {
            valueView.tag?.let { valueView.text = it.toString() }
          } else {
            callback?.invoke(this@RatingsStripView.ratings)
          }
        }
      }
    }

    progressView.visibleIf(isLoading)
    linkView.visibleIf(!isLoading && rating.isNullOrBlank())
  }

  fun isBound() = this::ratings.isInitialized && !this.ratings.isAnyLoading()
}
