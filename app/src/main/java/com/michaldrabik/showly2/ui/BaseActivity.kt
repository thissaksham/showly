package com.michaldrabik.showly2.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.michaldrabik.common.Mode
import com.michaldrabik.showly2.R
import com.michaldrabik.ui_navigation.java.NavigationArgs

abstract class BaseActivity : AppCompatActivity() {

  protected val actionKeys = arrayOf(
    NavigationArgs.ACTION_SHOW_DETAILS,
    NavigationArgs.ACTION_MOVIE_DETAILS,
    NavigationArgs.ACTION_SEARCH,
  )

  protected fun findNavHostFragment() =
    supportFragmentManager.findFragmentById(R.id.navigationHost) as? NavHostFragment

  protected open fun handleSearchWidgetClick(bundle: Bundle?) {
    if (bundle?.containsKey(NavigationArgs.ACTION_SEARCH) == true) {
      findNavHostFragment()?.findNavController()?.navigate(R.id.searchFragment)
    }
  }

  protected fun handleNotification(
    bundle: Bundle?,
    onHandled: () -> Unit,
  ) {
    bundle?.getString(NavigationArgs.ARG_ID)?.let { id ->
      val navBundle = bundleOf(NavigationArgs.ARG_SHOW_ID to id.toLong())
      findNavHostFragment()?.findNavController()?.navigate(R.id.showDetailsFragment, navBundle)
      onHandled()
    }
    bundle?.getString(NavigationArgs.ARG_MOVIE_ID)?.let { id ->
      val navBundle = bundleOf(NavigationArgs.ARG_MOVIE_ID to id.toLong())
      findNavHostFragment()?.findNavController()?.navigate(R.id.movieDetailsFragment, navBundle)
      onHandled()
    }
  }

  protected fun handleShowMovieExtra(
    bundle: Bundle,
    action: String,
    onHandled: () -> Unit,
  ) {
    if (!actionKeys.contains(action)) return

    when (action) {
      NavigationArgs.ACTION_SHOW_DETAILS -> {
        val id = bundle.getLong(NavigationArgs.ARG_SHOW_ID)
        val navBundle = bundleOf(NavigationArgs.ARG_SHOW_ID to id)
        findNavHostFragment()?.findNavController()?.navigate(R.id.showDetailsFragment, navBundle)
        onHandled()
      }
      NavigationArgs.ACTION_MOVIE_DETAILS -> {
        val id = bundle.getLong(NavigationArgs.ARG_MOVIE_ID)
        val navBundle = bundleOf(NavigationArgs.ARG_MOVIE_ID to id)
        findNavHostFragment()?.findNavController()?.navigate(R.id.movieDetailsFragment, navBundle)
        onHandled()
      }
      NavigationArgs.ACTION_SEARCH -> {
        val query = bundle.getString(NavigationArgs.ARG_QUERY)
        val mode = bundle.getSerializable(NavigationArgs.ARG_TYPE) as? Mode
        val navBundle = bundleOf(
          NavigationArgs.ARG_QUERY to query,
          NavigationArgs.ARG_TYPE to mode,
        )
        findNavHostFragment()?.findNavController()?.navigate(R.id.searchFragment, navBundle)
        onHandled()
      }
    }
  }
}
