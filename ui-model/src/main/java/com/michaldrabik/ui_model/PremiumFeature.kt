package com.michaldrabik.ui_model

import android.content.Context
import androidx.annotation.StringRes

enum class PremiumFeature(
  @param:StringRes val tag: Int,
) {
  THEME(R.string.tagTheme),
  WIDGET_TRANSPARENCY(R.string.tagWidgetTransparency),
  QUICK_RATING(R.string.tagQuickRating),
  VIEW_TYPES(R.string.tagViewsTypes),
  ;

  companion object {
    fun fromTag(
      context: Context,
      tag: String,
    ) = entries.firstOrNull { context.getString(it.tag) == tag }
  }
}
