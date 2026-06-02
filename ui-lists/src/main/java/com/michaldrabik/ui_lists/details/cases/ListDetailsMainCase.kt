package com.michaldrabik.ui_lists.details.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.repository.ListsRepository
import com.michaldrabik.ui_lists.details.recycler.ListDetailsItem
import com.michaldrabik.ui_model.CustomList
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class ListDetailsMainCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val localSource: LocalDataSource,
  private val transactions: TransactionsProvider,
  private val listsRepository: ListsRepository,
) {

  suspend fun loadDetails(id: Long): CustomList =
    withContext(dispatchers.IO) {
      listsRepository.loadById(id)
    }

  suspend fun updateRanks(
    listId: Long,
    items: List<ListDetailsItem>,
  ): List<ListDetailsItem> =
    withContext(dispatchers.IO) {
      transactions.withTransaction {
        val listItems = localSource.customListsItems.getItemsById(listId)
        val updatedItems = listItems.map { listItem ->
          val item = items.find { it.id == listItem.id }
          if (item != null) {
            listItem.copy(rank = (items.indexOf(item) + 1).toLong())
          } else {
            listItem
          }
        }
        localSource.customListsItems.update(updatedItems)
      }
      items
    }

  suspend fun deleteList(
    id: Long,
  ) = withContext(dispatchers.IO) {
    transactions.withTransaction {
      listsRepository.deleteList(id)
    }
  }

  fun isQuickRemoveEnabled(list: CustomList): Boolean = false
}
