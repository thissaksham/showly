package com.michaldrabik.ui_progress_movies.progress

import com.michaldrabik.repository.TranslationsRepository
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_progress_movies.BaseMockTest
import com.michaldrabik.ui_progress_movies.main.ProgressMoviesMainUiState
import com.michaldrabik.ui_progress_movies.progress.cases.ProgressMoviesItemsCase
import com.michaldrabik.ui_progress_movies.progress.cases.ProgressMoviesPinnedCase
import com.michaldrabik.ui_progress_movies.progress.cases.ProgressMoviesSortCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProgressMoviesViewModelTest : BaseMockTest() {

  @MockK lateinit var itemsCase: ProgressMoviesItemsCase
  @MockK lateinit var sortCase: ProgressMoviesSortCase
  @MockK lateinit var pinnedCase: ProgressMoviesPinnedCase
  @MockK lateinit var imagesProvider: MovieImagesProvider
  @RelaxedMockK lateinit var settingsRepository: SettingsRepository
  @MockK lateinit var translationsRepository: TranslationsRepository

  private lateinit var SUT: ProgressMoviesViewModel
  private val parentState = ProgressMoviesMainUiState(timestamp = 0L)

  @Before
  override fun setUp() {
    super.setUp()
    SUT = ProgressMoviesViewModel(
      itemsCase,
      sortCase,
      pinnedCase,
      imagesProvider,
      settingsRepository,
      translationsRepository,
    )
  }

  @Test
  fun `Should load items if parent timestamp changed`(): TestResult =
    runTest {
      coEvery { itemsCase.loadItems(any()) } returns emptyList()

      SUT.onParentState(parentState.copy(timestamp = 123L))

      coVerify { itemsCase.loadItems("") }
    }

  @Test
  fun `Should not reload items if parent timestamp is the same`(): TestResult =
    runTest {
      coEvery { itemsCase.loadItems(any()) } returns emptyList()

      SUT.onParentState(parentState.copy(timestamp = 0L))

      coVerify(exactly = 0) { itemsCase.loadItems(any()) }
    }

  @Test
  fun `Should load items if search query changed`(): TestResult =
    runTest {
      coEvery { itemsCase.loadItems(any()) } returns emptyList()

      SUT.onParentState(parentState.copy(searchQuery = "query"))

      coVerify { itemsCase.loadItems("query") }
    }
}
