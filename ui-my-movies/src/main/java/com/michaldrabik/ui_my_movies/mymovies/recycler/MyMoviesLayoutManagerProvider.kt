package com.michaldrabik.ui_my_movies.mymovies.recycler

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.VERTICAL
import androidx.recyclerview.widget.RecyclerView
import com.michaldrabik.ui_base.utilities.extensions.isTablet

internal object MyMoviesLayoutManagerProvider {

  fun provideLayoutManger(
    context: Context,
    gridSpanSize: Int,
  ): RecyclerView.LayoutManager =
    if (context.isTablet()) {
      provideTabletLayout(context, gridSpanSize)
    } else {
      providePhoneLayout(context)
    }

  private fun providePhoneLayout(
    context: Context,
  ): RecyclerView.LayoutManager = LinearLayoutManager(context, VERTICAL, false)

  private fun provideTabletLayout(
    context: Context,
    gridSpanSize: Int,
  ): RecyclerView.LayoutManager = GridLayoutManager(context, gridSpanSize)
}
