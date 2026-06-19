package com.michaldrabik.ui_people.details.recycler.views

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.michaldrabik.common.Config
import com.michaldrabik.ui_base.utilities.extensions.OutlineTransformation
import com.michaldrabik.ui_base.utilities.extensions.capitalizeWords
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visible
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.extensions.withFailListener
import com.michaldrabik.ui_model.Person
import com.michaldrabik.ui_people.R
import com.michaldrabik.ui_people.databinding.ViewPersonDetailsInfoBinding
import com.michaldrabik.ui_people.details.recycler.PersonDetailsItem

class PersonDetailsInfoView : ConstraintLayout {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewPersonDetailsInfoBinding.inflate(LayoutInflater.from(context), this)

  private val topLeftCornerRadius by lazy { context.dimenToPx(R.dimen.personImageCorner).toFloat() }
  private val cornerRadius by lazy { context.dimenToPx(R.dimen.mediaTileCorner).toFloat() }
  private val spaceNormal by lazy { context.dimenToPx(R.dimen.spaceNormal) }

  var onLinksClickListener: ((Person) -> Unit)? = null
  var onImageClickListener: (() -> Unit)? = null

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    updatePadding(left = spaceNormal, right = spaceNormal)
    clipToPadding = false
  }

  fun bind(item: PersonDetailsItem.MainInfo) {
    with(binding) {
      viewPersonDetailsTitle.text = item.person.name
      viewPersonDetailsSubtitle.text = item.person.characters.joinToString(", ")
      viewPersonDetailsLinkIcon.onClick { onLinksClickListener?.invoke(item.person) }
      viewPersonDetailsImage.onClick { onImageClickListener?.invoke() }
      viewPersonDetailsPlaceholder.onClick { onImageClickListener?.invoke() }

      item.person.birthday?.let { date ->
        viewPersonDetailsBirthdayLabel.visible()
        viewPersonDetailsBirthdayValue.visible()
        viewPersonDetailsAgeLabel.visible()
        viewPersonDetailsAgeValue.visible()
        val birthdayText = item.dateFormat
          ?.format(date)
          ?.capitalizeWords()
          ?.plus(if (!item.person.birthplace.isNullOrBlank()) "\n${item.person.birthplace}" else "")
        viewPersonDetailsBirthdayValue.text = birthdayText
        viewPersonDetailsAgeValue.text = item.person.getAge().toString()
      }
      item.person.deathday?.let { date ->
        viewPersonDetailsDeathdayLabel.visible()
        viewPersonDetailsDeathdayValue.visible()
        viewPersonDetailsDeathdayValue.text = item.dateFormat?.format(date)?.capitalizeWords()
      }
      viewPersonDetailsProgress.visibleIf(item.isLoading)
      val hasBio = !item.person.bio.isNullOrBlank()
      viewPersonDetailsSeparator.visibleIf(hasBio)
      (viewPersonDetailsSeparator.layoutParams as MarginLayoutParams).updateMargins(
        top = if (hasBio) spaceNormal else 0,
        bottom = if (hasBio) context.dimenToPx(R.dimen.spaceMedium) else 0
      )
    }
    renderImage(item.person)
  }

  private fun renderImage(person: Person) {
    with(binding) {
      Glide.with(this@PersonDetailsInfoView).clear(viewPersonDetailsImage)

      if (person.imagePath.isNullOrBlank()) {
        viewPersonDetailsImage.gone()
        viewPersonDetailsPlaceholder.visible()
        return
      }

      viewPersonDetailsImage.visible()
      viewPersonDetailsPlaceholder.gone()

      if (person.department == Person.Department.PRODUCTION) {
        viewPersonDetailsImage.background = null
        viewPersonDetailsImage.elevation = 0f
        viewPersonDetailsImage.clipToOutline = true
      } else {
        viewPersonDetailsImage.setBackgroundResource(R.drawable.bg_person_image_elevation)
        viewPersonDetailsImage.elevation = context.resources.getDimension(R.dimen.elevationNormal)
        viewPersonDetailsImage.clipToOutline = false
      }

      val baseUrl = if (person.department == Person.Department.PRODUCTION) Config.TMDB_IMAGE_BASE_LOGO_URL else Config.TMDB_IMAGE_BASE_ACTOR_URL

      Glide
        .with(this@PersonDetailsInfoView)
        .load("$baseUrl${person.imagePath}")
        .let {
          if (person.department == Person.Department.PRODUCTION) {
            it.transform(MultiTransformation(FitCenter(), OutlineTransformation(5f)))
          } else {
            it.transform(CenterCrop(), GranularRoundedCorners(topLeftCornerRadius, cornerRadius, cornerRadius, cornerRadius))
          }
        }
        .transition(DrawableTransitionOptions.withCrossFade(Config.IMAGE_FADE_DURATION_MS))
        .withFailListener {
          viewPersonDetailsImage.gone()
          viewPersonDetailsPlaceholder.visible()
        }.into(viewPersonDetailsImage)
    }
  }
}
