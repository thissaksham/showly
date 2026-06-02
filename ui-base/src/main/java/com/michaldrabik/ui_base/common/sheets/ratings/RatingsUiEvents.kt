package com.michaldrabik.ui_base.common.sheets.ratings

import com.michaldrabik.ui_base.common.sheets.ratings.RatingsBottomSheet.Options.Operation
import com.michaldrabik.ui_base.utilities.events.Event

class FinishUiEvent(
  val operation: Operation,
) : Event<Operation>(operation)
