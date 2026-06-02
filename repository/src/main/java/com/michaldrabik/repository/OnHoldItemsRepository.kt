package com.michaldrabik.repository

import android.content.SharedPreferences
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Show
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class OnHoldItemsRepository @Inject constructor(
  @Named("progressOnHoldPreferences") private val sharedPreferences: SharedPreferences,
) {

  fun getAll(): List<IdTrakt> = sharedPreferences.all.keys.map { IdTrakt(it.toLong()) }

  fun addItem(show: Show) = addItem(IdTrakt(show.traktId))

  fun addItem(showId: IdTrakt) = sharedPreferences.edit().putLong(showId.id.toString(), showId.id).apply()

  fun removeItem(show: Show) = removeOnHold(IdTrakt(show.traktId))

  fun removeOnHold(showId: IdTrakt) = sharedPreferences.edit().remove(showId.id.toString()).apply()

  fun isOnHold(show: Show) = isOnHold(IdTrakt(show.traktId))

  fun isOnHold(showId: IdTrakt) = sharedPreferences.contains(showId.id.toString())
}
