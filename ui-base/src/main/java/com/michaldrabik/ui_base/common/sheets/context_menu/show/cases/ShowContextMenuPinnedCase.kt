package com.michaldrabik.ui_base.common.sheets.context_menu.show.cases

import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.OnHoldItemsRepository
import com.michaldrabik.ui_model.IdTrakt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowContextMenuPinnedCase @Inject constructor(
  private val pinnedItemsRepository: PinnedItemsRepository,
  private val onHoldItemsRepository: OnHoldItemsRepository,
) {

  fun addToTopPinned(traktId: IdTrakt) {
    pinnedItemsRepository.addShowPinnedItem(traktId)
    onHoldItemsRepository.addItem(traktId)
  }

  fun removeFromTopPinned(traktId: IdTrakt) {
    pinnedItemsRepository.removePinnedItem(com.michaldrabik.ui_model.Show.EMPTY.copy(ids = com.michaldrabik.ui_model.Ids.EMPTY.copy(trakt = traktId)))
  }
}
