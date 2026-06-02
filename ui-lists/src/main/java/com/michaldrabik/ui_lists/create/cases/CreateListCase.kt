package com.michaldrabik.ui_lists.create.cases

import com.michaldrabik.repository.ListsRepository
import com.michaldrabik.ui_model.CustomList
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class CreateListCase @Inject constructor(
  private val listsRepository: ListsRepository,
) {

  suspend fun createList(
    name: String,
    description: String?,
  ): CustomList {
    return listsRepository.createList(
      name = name,
      description = description,
      idTrakt = null,
      idSlug = null,
    )
  }

  suspend fun updateList(list: CustomList): CustomList {
    return listsRepository.updateList(
      id = list.id,
      idTrakt = list.idTrakt,
      idSlug = list.idSlug,
      name = list.name,
      description = list.description,
    )
  }
}
