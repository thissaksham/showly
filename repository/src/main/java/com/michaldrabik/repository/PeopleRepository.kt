package com.michaldrabik.repository

import com.michaldrabik.common.Config
import com.michaldrabik.common.Mode
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.nowUtcDay
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.PersonShowMovie
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.RemoteDataSource
import com.michaldrabik.data_remote.tmdb.model.TmdbPerson.Type
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.repository.utilities.LocalIdResolver
import com.michaldrabik.ui_model.IdTmdb
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Image
import com.michaldrabik.ui_model.ImageType
import com.michaldrabik.ui_model.Person
import com.michaldrabik.ui_model.Person.Department
import com.michaldrabik.ui_model.PersonCredit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class PeopleRepository @Inject constructor(
  private val settingsRepository: SettingsRepository,
  private val localSource: LocalDataSource,
  private val remoteSource: RemoteDataSource,
  private val transactions: TransactionsProvider,
  private val mappers: Mappers,
) {

  companion object {
    const val ACTORS_DISPLAY_LIMIT = 30
    const val CREW_DISPLAY_LIMIT = 20
  }

  // In-memory cache of production-house credits, keyed by TMDB company id. Avoids re-fetching
  // TMDB on every Movies/Shows/Collection filter toggle (filters are applied downstream, so the
  // underlying credits list is identical across toggles). Reuses the people-credits TTL.
  private data class CachedCredits(
    val timestampMs: Long,
    val credits: List<PersonCredit>,
  )

  private val companyCreditsCache = ConcurrentHashMap<Long, CachedCredits>()

  // Person credits are display-only too, for the same reason, so they cache the same way.
  private val personCreditsCache = ConcurrentHashMap<Long, CachedCredits>()

  suspend fun loadDetails(person: Person): Person {
    if (person.department == Department.PRODUCTION) {
      val companyId = -person.ids.tmdb.id
      val remoteCompany = remoteSource.tmdb.fetchCompanyDetails(companyId)
      return mappers.person.fromCompany(remoteCompany)
    }

    val local = localSource.people.getById(person.ids.tmdb.id)
    if (local?.detailsUpdatedAt != null) {
      return mappers.person.fromDatabase(local, person.characters)
    }

    val language = settingsRepository.language
    val remotePerson = remoteSource.tmdb.fetchPersonDetails(person.ids.tmdb.id)
    var bioTranslation: String? = null
    if (language != Config.DEFAULT_LANGUAGE) {
      val translations = remoteSource.tmdb.fetchPersonTranslations(person.ids.tmdb.id)
      bioTranslation = translations[language]?.biography
    }

    val personUi = mappers.person.fromNetwork(remotePerson).copy(
      bioTranslation = bioTranslation,
      imagePath = person.imagePath ?: remotePerson.profile_path,
    )
    val dbPerson = mappers.person.toDatabase(personUi, nowUtc())

    localSource.people.upsert(listOf(dbPerson))

    return personUi
  }

  suspend fun loadCredits(person: Person) =
    coroutineScope {
      // Production "houses" are companies, not people, and take the discover path.
      if (person.department == Department.PRODUCTION) {
        loadCompanyCredits(person)
      } else {
        loadPersonCredits(person)
      }
    }

  /**
   * Everything a person worked on, from TMDB's combined credits.
   *
   * This used to translate the TMDB person id to a Trakt one first and fetch credits
   * from there. That lookup is gone, and it failed silently - an empty list, not an
   * error - so every person's filmography came up blank.
   *
   * Results are resolved against the local DB by TMDB id, so collected titles keep
   * the id their watch history hangs off. Like company credits they are display-only
   * and deliberately NOT persisted: these rows carry only summary fields, and writing
   * them would make the details screen serve a half-empty cached show for days.
   */
  private suspend fun loadPersonCredits(person: Person): List<PersonCredit> {
    val tmdbId = person.ids.tmdb.id
    if (tmdbId <= 0) return emptyList()

    personCreditsCache[tmdbId]
      ?.takeIf { nowUtcMillis() - it.timestampMs < Config.PEOPLE_CREDITS_CACHE_DURATION }
      ?.let { return it.credits }

    val type = if (person.department == Department.ACTING) Type.CAST else Type.CREW
    val items = remoteSource.tmdb.fetchPersonCredits(tmdbId, type)

    // Pair each item with its id up front: an item without one cannot be resolved
    // to a local row, and carrying the nullable id further just spreads the check.
    val showItems = items.mapNotNull { item -> item.id?.takeIf { item.isShow }?.let { it to item } }.distinctBy { it.first }
    val movieItems = items.mapNotNull { item -> item.id?.takeIf { item.isMovie }?.let { it to item } }.distinctBy { it.first }

    val localShows = localSource.shows
      .getByTmdbIds(showItems.map { it.first })
      .associateBy { it.idTmdb }
    val localMovies = localSource.movies
      .getByTmdbIds(movieItems.map { it.first })
      .associateBy { it.idTmdb }

    val showCredits = showItems.map { (id, item) ->
      PersonCredit(
        show = localShows[id]
          ?.let { mappers.show.fromDatabase(it) }
          ?: mappers.show.fromTmdbSearch(item, LocalIdResolver.newId(id)),
        movie = null,
        image = Image.createUnknown(ImageType.POSTER),
        translation = null,
      )
    }

    val movieCredits = movieItems.map { (id, item) ->
      PersonCredit(
        show = null,
        movie = localMovies[id]
          ?.let { mappers.movie.fromDatabase(it) }
          ?: mappers.movie.fromTmdbSearch(item, LocalIdResolver.newId(id)),
        image = Image.createUnknown(ImageType.POSTER),
        translation = null,
      )
    }

    val credits = showCredits + movieCredits
    personCreditsCache[tmdbId] = CachedCredits(nowUtcMillis(), credits)
    return credits
  }

  /**
   * Production "houses" are companies, not people: Trakt exposes no studio -> titles lookup, so
   * their filmography is fetched from TMDB discover-by-company — one page of the studio's most
   * popular titles PLUS a dedicated query for upcoming/unreleased ones (which have ~0 popularity
   * and would otherwise be buried far below page 1). Single page per query (no pagination).
   * Each result is resolved against the local DB by TMDB id:
   *  - collected titles take their real Trakt id (My/Watchlist badges + direct navigation work);
   *  - unknown titles keep a negative, TMDB-derived placeholder id that the details screen
   *    resolves to a real Trakt id on click.
   * Results are display-only and deliberately NOT persisted, to avoid polluting the shows/movies
   * tables with placeholder rows.
   */
  private suspend fun loadCompanyCredits(person: Person): List<PersonCredit> {
    val companyId = -person.ids.tmdb.id

    companyCreditsCache[companyId]
      ?.takeIf { nowUtcMillis() - it.timestampMs < Config.PEOPLE_CREDITS_CACHE_DURATION }
      ?.let { return it.credits }

    val credits = coroutineScope {
      val today = nowUtcDay().toString()

      val popularMoviesAsync = async { remoteSource.tmdb.discoverMoviesByCompany(companyId).results ?: emptyList() }
      val popularShowsAsync = async { remoteSource.tmdb.discoverShowsByCompany(companyId).results ?: emptyList() }
      val upcomingMoviesAsync = async {
        remoteSource.tmdb
          .discoverMoviesByCompany(companyId, sortBy = "primary_release_date.asc", releasedAfter = today)
          .results ?: emptyList()
      }
      val upcomingShowsAsync = async {
        remoteSource.tmdb
          .discoverShowsByCompany(companyId, sortBy = "first_air_date.asc", airedAfter = today)
          .results ?: emptyList()
      }

      val moviesDiscovery = (popularMoviesAsync.await() + upcomingMoviesAsync.await()).distinctBy { it.id }
      val showsDiscovery = (popularShowsAsync.await() + upcomingShowsAsync.await()).distinctBy { it.id }

      val localMovies = localSource.movies.getByTmdbIds(moviesDiscovery.map { it.id }).associateBy { it.idTmdb }
      val localShows = localSource.shows.getByTmdbIds(showsDiscovery.map { it.id }).associateBy { it.idTmdb }

      val movieCredits = moviesDiscovery.map { item ->
        val local = localMovies[item.id]
        val movie = if (local != null) {
          mappers.movie.fromDatabase(local)
        } else {
          mappers.movie
            .fromTmdbDiscovery(item)
            .copy(ids = Ids.EMPTY.copy(tmdb = IdTmdb(item.id), trakt = IdTrakt(LocalIdResolver.newId(item.id))))
        }
        PersonCredit(
          movie = movie,
          show = null,
          image = Image.createUnknown(ImageType.POSTER),
          translation = null,
        )
      }

      val showCredits = showsDiscovery.map { item ->
        val local = localShows[item.id]
        val show = if (local != null) {
          mappers.show.fromDatabase(local)
        } else {
          mappers.show
            .fromTmdbDiscovery(item)
            .copy(ids = Ids.EMPTY.copy(tmdb = IdTmdb(item.id), trakt = IdTrakt(LocalIdResolver.newId(item.id))))
        }
        PersonCredit(
          movie = null,
          show = show,
          image = Image.createUnknown(ImageType.POSTER),
          translation = null,
        )
      }

      movieCredits + showCredits
    }

    companyCreditsCache[companyId] = CachedCredits(nowUtcMillis(), credits)
    return credits
  }

  suspend fun loadAllForShow(showIds: Ids): Map<Department, List<Person>> {
    val timestamp = nowUtc()

    val localTimestamp = localSource.peopleShowsMovies.getTimestampForShow(showIds.trakt.id) ?: 0
    val local = localSource.people.getAllForShow(showIds.trakt.id)
    if (local.isNotEmpty() && localTimestamp + Config.ACTORS_CACHE_DURATION > timestamp.toMillis()) {
      return local
        .map { mappers.person.fromDatabase(it) }
        .groupBy { it.department }
        .mapValues { v ->
          v.value
            .sortedWith(compareBy { it.imagePath.isNullOrBlank() })
        }
    }

    val jobsFilter = Person.Job.entries.map { it.slug }
    val crewFilter = arrayOf(
      Department.DIRECTING,
      Department.WRITING,
      Department.PRODUCTION,
    ).map { it.slug }

    val remoteTmdbPeople = remoteSource.tmdb.fetchShowPeople(showIds.tmdb.id)
    val remoteShowDetails = remoteSource.tmdb.fetchShowDetails(showIds.tmdb.id)

    val remoteTmdbCrew = remoteTmdbPeople
      .getOrDefault(Type.CREW, emptyList())
      .asSequence()
      .filter { it.department in crewFilter }
      .filter { it.jobs?.any { job -> (job.job ?: "") in jobsFilter } == true }
      .sortedWith(compareBy { it.profile_path.isNullOrBlank() })
      .map { mappers.person.fromNetwork(it) }
      .groupBy { it.department }

    val actors = remoteTmdbPeople
      .getOrDefault(Type.CAST, emptyList())
      .sortedWith(compareBy { it.profile_path.isNullOrBlank() })
      .map { mappers.person.fromNetwork(it) }
      .take(ACTORS_DISPLAY_LIMIT)

    val directors = remoteTmdbCrew[Department.DIRECTING]
      ?.take(CREW_DISPLAY_LIMIT)
      ?.distinctBy { it.ids.tmdb } ?: emptyList()

    val writers = remoteTmdbCrew[Department.WRITING]
      ?.take(CREW_DISPLAY_LIMIT)
      ?.distinctBy { it.ids.tmdb } ?: emptyList()

    val production = remoteShowDetails.production_companies?.map {
      Person(
        ids = Ids.EMPTY.copy(tmdb = IdTmdb(-it.id.toLong())),
        name = it.name,
        department = Department.PRODUCTION,
        bio = null,
        bioTranslation = null,
        characters = emptyList(),
        jobs = emptyList(),
        episodesCount = 0,
        birthplace = null,
        imagePath = it.logo_path,
        homepage = null,
        birthday = null,
        deathday = null,
      )
    } ?: emptyList()

    val dbTmdbCast = actors.map {
      PersonShowMovie(
        id = 0,
        idTmdbPerson = it.ids.tmdb.id,
        mode = Mode.SHOWS.type,
        department = Department.ACTING.slug,
        character = it.characters.joinToString(","),
        job = it.jobs.joinToString(",") { job -> job.slug },
        episodesCount = it.episodesCount,
        idTraktShow = showIds.trakt.id,
        idTraktMovie = null,
        createdAt = timestamp,
        updatedAt = timestamp,
      )
    }

    val dbTmdbCrew = (directors + writers + production).map {
      PersonShowMovie(
        id = 0,
        idTmdbPerson = it.ids.tmdb.id,
        mode = Mode.SHOWS.type,
        department = it.department.slug,
        character = it.characters.joinToString(","),
        job = it.jobs.joinToString(",") { job -> job.slug },
        episodesCount = it.episodesCount,
        idTraktShow = showIds.trakt.id,
        idTraktMovie = null,
        createdAt = timestamp,
        updatedAt = timestamp,
      )
    }

    val dbTmdbPeople = (actors + directors + writers + production)
      .map { mappers.person.toDatabase(it, null) }

    with(localSource) {
      transactions.withTransaction {
        people.upsert(dbTmdbPeople)
        peopleShowsMovies.insertForShow(dbTmdbCast + dbTmdbCrew, showIds.trakt.id)
      }
    }

    val showActors = actors.map { it.copy(department = Department.ACTING) }
    return (showActors + directors + writers + production)
      .groupBy { it.department }
  }

  suspend fun loadAllForMovie(movieIds: Ids): Map<Department, List<Person>> {
    val timestamp = nowUtc()

    val localTimestamp = localSource.peopleShowsMovies.getTimestampForMovie(movieIds.trakt.id) ?: 0
    val local = localSource.people.getAllForMovie(movieIds.trakt.id)
    if (local.isNotEmpty() && localTimestamp + Config.ACTORS_CACHE_DURATION > timestamp.toMillis()) {
      return local
        .map { mappers.person.fromDatabase(it) }
        .groupBy { it.department }
        .mapValues { v ->
          v.value
            .sortedWith(compareBy { it.imagePath.isNullOrBlank() })
        }
    }

    val jobsFilter = Person.Job.entries.map { it.slug }
    val crewFilter = arrayOf(
      Department.DIRECTING,
      Department.WRITING,
      Department.PRODUCTION,
    ).map { it.slug }

    val remoteTmdbPeople = remoteSource.tmdb.fetchMoviePeople(movieIds.tmdb.id)
    val remoteMovieDetails = remoteSource.tmdb.fetchMovieDetails(movieIds.tmdb.id)

    val remoteTmdbCrew = remoteTmdbPeople
      .getOrDefault(Type.CREW, emptyList())
      .asSequence()
      .filter { it.department in crewFilter }
      .filter { it.job in jobsFilter }
      .sortedWith(compareBy { it.profile_path.isNullOrBlank() })
      .map { mappers.person.fromNetwork(it) }
      .groupBy { it.department }

    val actors = remoteTmdbPeople
      .getOrDefault(Type.CAST, emptyList())
      .sortedWith(compareBy { it.profile_path.isNullOrBlank() })
      .map { mappers.person.fromNetwork(it) }
      .take(ACTORS_DISPLAY_LIMIT)

    val directors = remoteTmdbCrew[Department.DIRECTING]
      ?.take(CREW_DISPLAY_LIMIT)
      ?.distinctBy { it.ids.tmdb } ?: emptyList()

    val writers = remoteTmdbCrew[Department.WRITING]
      ?.take(CREW_DISPLAY_LIMIT)
      ?.distinctBy { it.ids.tmdb } ?: emptyList()

    val production = remoteMovieDetails.production_companies?.map {
      Person(
        ids = Ids.EMPTY.copy(tmdb = IdTmdb(-it.id.toLong())),
        name = it.name,
        department = Department.PRODUCTION,
        bio = null,
        bioTranslation = null,
        characters = emptyList(),
        jobs = emptyList(),
        episodesCount = 0,
        birthplace = null,
        imagePath = it.logo_path,
        homepage = null,
        birthday = null,
        deathday = null,
      )
    } ?: emptyList()

    val dbTmdbCast = actors.map {
      PersonShowMovie(
        id = 0,
        idTmdbPerson = it.ids.tmdb.id,
        mode = Mode.MOVIES.type,
        department = Department.ACTING.slug,
        character = it.characters.joinToString(","),
        job = it.jobs.joinToString(",") { job -> job.slug },
        episodesCount = it.episodesCount,
        idTraktShow = null,
        idTraktMovie = movieIds.trakt.id,
        createdAt = timestamp,
        updatedAt = timestamp,
      )
    }

    val dbTmdbCrew = (directors + writers + production).map {
      PersonShowMovie(
        id = 0,
        idTmdbPerson = it.ids.tmdb.id,
        mode = Mode.MOVIES.type,
        department = it.department.slug,
        character = it.characters.joinToString(","),
        job = it.jobs.joinToString(",") { job -> job.slug },
        episodesCount = it.episodesCount,
        idTraktShow = null,
        idTraktMovie = movieIds.trakt.id,
        createdAt = timestamp,
        updatedAt = timestamp,
      )
    }

    val dbTmdbPeople = (actors + directors + writers + production)
      .map { mappers.person.toDatabase(it, null) }

    with(localSource) {
      transactions.withTransaction {
        people.upsert(dbTmdbPeople)
        peopleShowsMovies.insertForMovie(dbTmdbCast + dbTmdbCrew, movieIds.trakt.id)
      }
    }

    val movieActors = actors.map { it.copy(department = Department.ACTING) }
    return (movieActors + directors + writers + production)
      .groupBy { it.department }
  }
}
