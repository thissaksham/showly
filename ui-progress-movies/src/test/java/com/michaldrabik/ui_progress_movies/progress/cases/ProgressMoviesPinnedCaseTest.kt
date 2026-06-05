package com.michaldrabik.ui_progress_movies.progress.cases

import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_progress_movies.BaseMockTest
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@Suppress("EXPERIMENTAL_API_USAGE")
class ProgressMoviesPinnedCaseTest : BaseMockTest() {

  @RelaxedMockK lateinit var pinnedItemsRepository: PinnedItemsRepository

  private lateinit var SUT: ProgressMoviesPinnedCase

  @Before
  override fun setUp() {
    super.setUp()
    SUT = ProgressMoviesPinnedCase(pinnedItemsRepository)
  }

  @After
  fun tearDown() {
    clearAllMocks()
  }

  @Test
  fun `Should toggle pinned item properly`() =
    runTest {
      coEvery { pinnedItemsRepository.isItemPinned(any<Movie>()) } returns false
      SUT.togglePinned(Movie.EMPTY)
      coVerify(exactly = 1) { pinnedItemsRepository.addPinnedItem(any<Movie>()) }

      coEvery { pinnedItemsRepository.isItemPinned(any<Movie>()) } returns true
      SUT.togglePinned(Movie.EMPTY)
      coVerify(exactly = 1) { pinnedItemsRepository.removePinnedItem(any<Movie>()) }
    }
}
