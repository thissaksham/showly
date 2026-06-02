package com.michaldrabik.ui_lists.manage.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.ListsRepository
import com.michaldrabik.ui_lists.manage.recycler.ManageListsItem
import com.michaldrabik.ui_model.IdTrakt
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class ManageListsCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val listsRepository: ListsRepository,
) {

  suspend fun loadLists(
    itemId: IdTrakt,
    itemType: String,
  ) = withContext(dispatchers.IO) {
    val lists = listsRepository.loadAll()
    val listIds = listsRepository.loadListIdsForItem(itemId, itemType)
    lists.map { list ->
      ManageListsItem(
        list = list,
        isChecked = listIds.contains(list.id),
        isEnabled = true,
      )
    }
  }

  suspend fun addToList(
    itemId: IdTrakt,
    itemType: String,
    listItem: ManageListsItem,
  ) = withContext(dispatchers.IO) {
    listsRepository.addToList(listItem.list.id, itemId, itemType)
  }

  suspend fun removeFromList(
    itemId: IdTrakt,
    itemType: String,
    listItem: ManageListsItem,
  ) = withContext(dispatchers.IO) {
    listsRepository.removeFromList(listItem.list.id, itemId, itemType)
  }
}
