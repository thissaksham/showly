package com.michaldrabik.ui_progress.history.utilities.groupers

import com.michaldrabik.common.extensions.toLocalZone
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.ui_progress.history.entities.HistoryListItem
import com.michaldrabik.ui_progress.history.entities.HistoryListItem.Episode
import java.time.temporal.ChronoUnit.DAYS
import javax.inject.Inject

internal class HistoryItemsGrouper @Inject constructor() {

  fun groupByDay(
    items: List<Episode>,
    language: String,
  ): List<HistoryListItem> {
    val itemsMap = items
      .groupBy {
        it.episode.lastWatchedAt
          ?.toLocalZone()
          ?.truncatedTo(DAYS)
      }.toSortedMap { d1, d2 ->
        when {
          (d1 == null && d2 == null) -> 0
          d1 == null -> 1
          d2 == null -> -1
          else -> d2.compareTo(d1) // Descending
        }
      }

    return itemsMap.entries.fold(mutableListOf()) { acc, entry ->
      acc.apply {
        if (entry.value.isNotEmpty()) {
          add(
            HistoryListItem.Header(
              date = entry.key?.toLocalDateTime(),
              language = language,
            ),
          )
          addAll(
            entry.value.sortedWith { e1, e2 ->
              if (entry.key == null) {
                compareValues(e1.episode.firstAired?.toMillis(), e2.episode.firstAired?.toMillis())
              } else {
                compareByDescending<Episode> { it.episode.lastWatchedAt?.toMillis() }
                  .thenByDescending { it.episode.season }
                  .thenByDescending { it.episode.number }
                  .compare(e1, e2)
              }
            },
          )
        }
      }
    }
  }
}
