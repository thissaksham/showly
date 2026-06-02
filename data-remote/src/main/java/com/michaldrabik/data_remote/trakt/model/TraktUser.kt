package com.michaldrabik.data_remote.trakt.model

data class TraktUser(
  val username: String?,
  val name: String?,
  val ids: Ids?,
  val images: Images?,
) {
  data class Images(
    val avatar: Avatar?,
  ) {
    data class Avatar(
      val full: String?,
    )
  }
}
