package com.michaldrabik.showly2.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.view.updateLayoutParams
import com.michaldrabik.showly2.R
import com.michaldrabik.showly2.databinding.ViewBottomMenuBinding
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets

class BottomMenuView : FrameLayout {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  val binding = ViewBottomMenuBinding.inflate(LayoutInflater.from(context), this)

  var isModeMenuEnabled = true

  init {
    rootView.doOnApplyWindowInsets { _, insets, _, _ ->
      val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
      binding.bottomNavigationView.updateLayoutParams<MarginLayoutParams> {
        height = context.dimenToPx(R.dimen.bottomNavigationHeight) + bottomInset
      }
    }
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    binding.bottomNavigationView.menu.forEach { it.isEnabled = enabled }
  }

  fun setupLongPressToggle(action: () -> Unit) {
    val menuView = binding.bottomNavigationView.getChildAt(0) as? ViewGroup ?: return
    menuView.children.forEach { view ->
      view.setOnLongClickListener {
        val isSelected = binding.bottomNavigationView.selectedItemId == view.id
        if (isModeMenuEnabled && isEnabled && isSelected) {
          action()
          true
        } else {
          false
        }
      }
    }
  }
}
