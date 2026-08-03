package com.michaldrabik.ui_backup.features.google

import org.json.JSONObject
import timber.log.Timber

/**
 * How much is in a backup. Recorded on the Drive file itself so the next upload can
 * tell whether it is about to replace a full library with a broken one, without
 * having to download and parse the old copy every time.
 */
data class BackupCounts(
  val shows: Int,
  val movies: Int,
  val episodes: Int,
) {

  val total: Int get() = shows + movies + episodes

  /**
   * Whether replacing [stored] with this looks like damage rather than deletions.
   *
   * A half-finished restore uploaded roughly a tenth of a library over the good copy;
   * someone genuinely pruning their library does not lose half of it inside one 15
   * minute window. An empty or unknown stored backup is never worth protecting.
   */
  fun wouldTruncate(stored: BackupCounts): Boolean = stored.total > 0 && total < stored.total * SHRINK_TOLERANCE

  fun toProperties(): Map<String, String> =
    mapOf(
      PROP_SHOWS to shows.toString(),
      PROP_MOVIES to movies.toString(),
      PROP_EPISODES to episodes.toString(),
    )

  companion object {
    private const val SHRINK_TOLERANCE = 0.5

    private const val PROP_SHOWS = "shows"
    private const val PROP_MOVIES = "movies"
    private const val PROP_EPISODES = "episodes"

    fun fromProperties(properties: Map<String, String>?): BackupCounts? {
      val shows = properties?.get(PROP_SHOWS)?.toIntOrNull() ?: return null
      val movies = properties[PROP_MOVIES]?.toIntOrNull() ?: return null
      val episodes = properties[PROP_EPISODES]?.toIntOrNull() ?: return null
      return BackupCounts(shows, movies, episodes)
    }

    /**
     * Counted straight off the json rather than the parsed model: this also has to
     * work on a file written by an older version, whose shape may not match the
     * current model, and a count is all that is needed.
     */
    fun of(json: String): BackupCounts? =
      try {
        val root = JSONObject(json)
        val shows = root.optJSONObject("shows")
        val movies = root.optJSONObject("movies")
        BackupCounts(
          shows = shows?.optJSONArray("cH")?.length() ?: 0,
          movies = movies?.optJSONArray("cH")?.length() ?: 0,
          episodes = shows?.optJSONArray("pEp")?.length() ?: 0,
        )
      } catch (error: Throwable) {
        Timber.w(error, "Could not count a backup payload.")
        null
      }
  }
}

/**
 * Thrown instead of replacing a much larger backup with a much smaller one. Carries
 * both sides so the user can be asked, rather than being told it simply failed.
 */
class BackupShrinkException(
  val stored: BackupCounts,
  val incoming: BackupCounts,
) : Exception(
    "Refusing to overwrite a backup of ${stored.total} items with one of ${incoming.total}.",
  )
