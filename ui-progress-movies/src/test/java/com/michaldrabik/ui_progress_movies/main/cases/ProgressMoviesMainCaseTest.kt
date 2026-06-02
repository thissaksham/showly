package com.michaldrabik.ui_progress_movies.main.cases

import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Movie
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProgressMoviesMainCaseTest {

  @get:Rule
  val mockkRule = MockKRule(this)

  @RelaxedMockK lateinit var moviesRepository: MoviesRepository
  @RelaxedMockK lateinit var pinnedItemsRepository: PinnedItemsRepository

  private lateinit var SUT: ProgressMoviesMainCase

  @Before
  fun setUp() {
    SUT = ProgressMoviesMainCase(
      moviesRepository,
      pinnedItemsRepository,
    )
  }

  @After
  fun tearDown() {
    confirmVerified(moviesRepository, pinnedItemsRepository)
    clearAllMocks()
  }

  @Test
  fun `Should add movie to movies history properly`(): TestResult =
    runTest {
      val movie = Movie.EMPTY.copy(ids = com.michaldrabik.ui_model.Ids.EMPTY.copy(trakt = IdTrakt(123)))

      SUT.addToMyMovies(movie, null)

      coVerify { moviesRepository.myMovies.insert(IdTrakt(123), null) }
      coVerify { pinnedItemsRepository.removePinnedItem(movie) }
    }

  @Test
  fun `Should add movie to movies history properly using only ID`(): TestResult =
    runTest {
      SUT.addToMyMovies(IdTrakt(123))

      coVerify { moviesRepository.myMovies.insert(IdTrakt(123), null) }
    }
}
