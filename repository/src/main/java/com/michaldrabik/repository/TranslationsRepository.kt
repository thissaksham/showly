package com.michaldrabik.repository

import android.content.SharedPreferences
import com.michaldrabik.common.Config.DEFAULT_LANGUAGE
import com.michaldrabik.common.ConfigVariant
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.EpisodeTranslation
import com.michaldrabik.data_local.database.model.MovieTranslation
import com.michaldrabik.data_local.database.model.ShowTranslation
import com.michaldrabik.data_local.database.model.TranslationsMoviesSyncLog
import com.michaldrabik.data_local.database.model.TranslationsSyncLog
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.data_remote.tmdb.model.TmdbEpisode
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.utilities.LocalIdResolver
import com.michaldrabik.repository.settings.SettingsRepository.Key.LANGUAGE
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.SeasonTranslation
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.Translation
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class TranslationsRepository @Inject constructor(
  @Named("miscPreferences") private var miscPreferences: SharedPreferences,
  private val remoteSource: RemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
) {

  fun getLanguage() = miscPreferences.getString(LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

  suspend fun loadAllShowsLocal(language: String = DEFAULT_LANGUAGE): Map<Long, Translation> {
    val local = localSource.showTranslations.getAll(language)
    return local.associate {
      Pair(it.idTrakt, mappers.translation.fromDatabase(it))
    }
  }

  suspend fun loadAllMoviesLocal(language: String = DEFAULT_LANGUAGE): Map<Long, Translation> {
    val local = localSource.movieTranslations.getAll(language)
    return local.associate {
      Pair(it.idTrakt, mappers.translation.fromDatabase(it))
    }
  }

  suspend fun loadTranslation(
    show: Show,
    language: String = DEFAULT_LANGUAGE,
    onlyLocal: Boolean = false,
  ): Translation? {
    val local = localSource.showTranslations.getById(show.traktId, language)
    local?.let {
      return mappers.translation.fromDatabase(it)
    }
    if (onlyLocal) return null

    val timestamp = localSource.translationsShowsSyncLog.getById(show.traktId)?.syncedAt ?: 0
    if (nowUtcMillis() - timestamp < ConfigVariant.TRANSLATION_SYNC_SHOW_MOVIE_COOLDOWN) {
      return Translation.EMPTY
    }

    val tmdbId = show.ids.tmdb.id.takeIf { it > 0 } ?: LocalIdResolver.tmdbIdOf(show.traktId)
    val remote = tmdbId?.let {
      runCatching { remoteSource.tmdb.fetchShowTranslation(it, language) }.getOrNull()
    }

    val translation = Translation(
      title = remote?.name ?: "",
      overview = remote?.overview ?: "",
      language = language,
    )
    val translationDb = ShowTranslation.fromTraktId(
      show.traktId,
      translation.title,
      language,
      translation.overview,
      nowUtcMillis(),
    )

    if (translationDb.overview.isNotBlank() || translationDb.title.isNotBlank()) {
      localSource.showTranslations.insertSingle(translationDb)
    }
    localSource.translationsShowsSyncLog.upsert(TranslationsSyncLog(show.traktId, nowUtcMillis()))

    return translation
  }

  suspend fun loadTranslation(
    movie: Movie,
    language: String = DEFAULT_LANGUAGE,
    onlyLocal: Boolean = false,
  ): Translation? {
    val local = localSource.movieTranslations.getById(movie.traktId, language)
    local?.let {
      return mappers.translation.fromDatabase(it)
    }
    if (onlyLocal) return null

    val timestamp = localSource.translationsMoviesSyncLog.getById(movie.traktId)?.syncedAt ?: 0
    if (nowUtcMillis() - timestamp < ConfigVariant.TRANSLATION_SYNC_SHOW_MOVIE_COOLDOWN) {
      return Translation.EMPTY
    }

    val tmdbId = movie.ids.tmdb.id.takeIf { it > 0 } ?: LocalIdResolver.tmdbIdOf(movie.traktId)
    val remote = tmdbId?.let {
      runCatching { remoteSource.tmdb.fetchMovieTranslation(it, language) }.getOrNull()
    }

    val translation = Translation(
      title = remote?.title ?: "",
      overview = remote?.overview ?: "",
      language = language,
    )
    val translationDb = MovieTranslation.fromTraktId(
      movie.traktId,
      translation.title,
      language,
      translation.overview,
      nowUtcMillis(),
    )

    if (translationDb.overview.isNotBlank() || translationDb.title.isNotBlank()) {
      localSource.movieTranslations.insertSingle(translationDb)
    }
    localSource.translationsMoviesSyncLog.upsert(TranslationsMoviesSyncLog(movie.traktId, nowUtcMillis()))

    return translation
  }

  suspend fun loadTranslation(
    episode: Episode,
    showId: IdTrakt,
    language: String = DEFAULT_LANGUAGE,
    onlyLocal: Boolean = false,
  ): Translation? {
    val nowMillis = nowUtcMillis()
    val local = localSource.episodesTranslations.getById(episode.ids.trakt.id, showId.id, language)
    local?.let {
      val isCacheValid = nowMillis - it.updatedAt < ConfigVariant.TRANSLATION_SYNC_EPISODE_COOLDOWN
      if (it.title.isNotBlank() && it.overview.isNotBlank()) {
        return mappers.translation.fromDatabase(it)
      }
      if ((it.title.isNotBlank() || it.overview.isNotBlank()) && (isCacheValid || onlyLocal)) {
        return mappers.translation.fromDatabase(it)
      }
    }

    if (onlyLocal) return null

    // TMDB returns a whole season at a time, keyed by episode number rather than by
    // id, so each one is matched back to the local episode it belongs to.
    val remoteEpisodes = fetchSeasonEpisodes(showId, episode.season, language)
    val localIds = localEpisodeIds(showId)

    remoteEpisodes.forEach { item ->
      val number = item.episode_number ?: return@forEach
      val localId = localIds[episode.season to number] ?: return@forEach
      val dbItem = EpisodeTranslation.fromTraktId(
        traktEpisodeId = localId,
        traktShowId = showId.id,
        title = item.name ?: "",
        overview = item.overview ?: "",
        language = language,
        createdAt = nowMillis,
      )
      localSource.episodesTranslations.insertSingle(dbItem)
    }

    return remoteEpisodes
      .find { it.episode_number == episode.number }
      ?.let { Translation(it.name ?: "", it.overview ?: "", language) }
  }

  private suspend fun fetchSeasonEpisodes(
    showId: IdTrakt,
    seasonNumber: Int,
    language: String,
  ): List<TmdbEpisode> {
    val tmdbId = localSource.shows
      .getById(showId.id)
      ?.idTmdb
      ?.takeIf { it > 0 }
      ?: LocalIdResolver.tmdbIdOf(showId.id)
      ?: return emptyList()

    return runCatching {
      remoteSource.tmdb
        .fetchSeasonTranslation(tmdbId, seasonNumber, language)
        .episodes
        .orEmpty()
    }.getOrDefault(emptyList())
  }

  private suspend fun localEpisodeIds(showId: IdTrakt): Map<Pair<Int, Int>, Long> =
    localSource.episodes
      .getAllByShowId(showId.id)
      .associate { (it.seasonNumber to it.episodeNumber) to it.idTrakt }

  suspend fun loadTranslations(
    season: Season,
    showId: IdTrakt,
    language: String = DEFAULT_LANGUAGE,
  ): List<SeasonTranslation> {
    val episodes = season.episodes.toList()
    val episodesIds = season.episodes.map { it.ids.trakt.id }

    val local = localSource.episodesTranslations.getByIds(episodesIds, showId.id, language)
    val hasAllTranslated = local.isNotEmpty() && local.all { it.title.isNotBlank() && it.overview.isNotBlank() }
    val isCacheValid =
      local.isNotEmpty() && nowUtcMillis() - local.first().updatedAt < ConfigVariant.TRANSLATION_SYNC_EPISODE_COOLDOWN

    if (hasAllTranslated || (!hasAllTranslated && isCacheValid)) {
      return episodes.map { episode ->
        val translation = local.find { it.idTrakt == episode.ids.trakt.id }
        SeasonTranslation(
          ids = episode.ids.copy(),
          title = translation?.title ?: "",
          overview = translation?.overview ?: "",
          seasonNumber = season.number,
          episodeNumber = episode.number,
          language = language,
          isLocal = true,
        )
      }
    }

    val remoteEpisodes = fetchSeasonEpisodes(showId, season.number, language)

    remoteEpisodes.forEach { item ->
      val localEpisode = episodes.find { it.number == item.episode_number } ?: return@forEach
      val dbItem = EpisodeTranslation.fromTraktId(
        traktEpisodeId = localEpisode.ids.trakt.id,
        traktShowId = showId.id,
        title = item.name ?: "",
        language = language,
        overview = item.overview ?: "",
        createdAt = nowUtcMillis(),
      )
      localSource.episodesTranslations.insertSingle(dbItem)
    }

    return episodes.map { episode ->
      val translation = remoteEpisodes.find { it.episode_number == episode.number }
      SeasonTranslation(
        ids = episode.ids.copy(),
        title = translation?.name ?: "",
        overview = translation?.overview ?: "",
        seasonNumber = season.number,
        episodeNumber = episode.number,
        language = language,
        isLocal = true,
      )
    }
  }

}
