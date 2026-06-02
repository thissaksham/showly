package com.michaldrabik.ui_base.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.common.behaviour.SearchViewBehaviour
import com.michaldrabik.ui_base.databinding.ViewSearchBinding
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf

class SearchView : FrameLayout, CoordinatorLayout.AttachedBehavior {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewSearchBinding.inflate(LayoutInflater.from(context), this, true)

  var onSettingsClickListener: (() -> Unit)? = null
  var onStatsClickListener: (() -> Unit)? = null

  init {
    with(binding) {
      searchSettingsIcon.onClick { onSettingsClickListener?.invoke() }
      searchStatsIcon.onClick { onStatsClickListener?.invoke() }
    }
  }

  var hint: String
    get() = binding.searchViewText.text.toString()
    set(value) {
      binding.searchViewText.text = value
      binding.searchViewInput.hint = value
    }

  var settingsIconVisible: Boolean
    get() = binding.searchSettingsIcon.isVisible
    set(value) {
      binding.searchSettingsIcon.visibleIf(value)
    }

  var statsIconVisible: Boolean
    get() = binding.searchStatsIcon.isVisible
    set(value) {
      binding.searchStatsIcon.visibleIf(value)
    }

  val isSearching: Boolean
    get() = binding.searchViewInput.isVisible

  val input get() = binding.searchViewInput
  val textView get() = binding.searchViewText
  val iconView get() = binding.searchViewIcon
  val statsIconView get() = binding.searchStatsIcon
  val settingsIconView get() = binding.searchSettingsIcon
  val searchRoot get() = binding.searchViewRoot

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (layoutParams is LayoutParams) {
      (layoutParams as LayoutParams).apply {
        width = LayoutParams.MATCH_PARENT
        height = resources.getDimensionPixelSize(R.dimen.searchViewHeight)
      }
    }
  }

  fun applyWindowInsetBehaviour(topPadding: Int) {
    doOnApplyWindowInsets { _, insets, _, _ ->
      updatePadding(top = topPadding + insets.systemWindowInsetTop)
    }
  }

  override fun getBehavior() = SearchViewBehaviour(padding = 0)

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    binding.searchViewRoot.isEnabled = enabled
  }

  fun setTraktProgress(isProgress: Boolean, withIcon: Boolean = false) {
    // No-op. Trakt sync progress removed.
  }
}
