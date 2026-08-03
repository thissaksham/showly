package com.michaldrabik.ui_backup

import com.google.common.truth.Truth.assertThat
import com.michaldrabik.ui_backup.features.google.BackupCounts
import org.junit.Test

/**
 * The rule that decides whether an upload is allowed to replace what is already in
 * Drive. A restore died a tenth of the way through, and fifteen minutes later the
 * periodic backup uploaded that tenth over the only good copy.
 */
class BackupShrinkGuardTest {

  private val fullLibrary = BackupCounts(shows = 233, movies = 435, episodes = 6858)

  @Test
  fun `Should refuse a backup taken from a half finished restore`() {
    // Roughly what a restore that died early would produce.
    val partial = BackupCounts(shows = 23, movies = 40, episodes = 680)

    assertThat(partial.wouldTruncate(fullLibrary)).isTrue()
  }

  @Test
  fun `Should refuse an empty backup over a full one`() {
    assertThat(BackupCounts(0, 0, 0).wouldTruncate(fullLibrary)).isTrue()
  }

  @Test
  fun `Should allow a library that only lost a few items`() {
    // Deleting shows is normal and must never be mistaken for damage.
    val pruned = BackupCounts(shows = 210, movies = 400, episodes = 6500)

    assertThat(pruned.wouldTruncate(fullLibrary)).isFalse()
  }

  @Test
  fun `Should allow a growing library`() {
    val grown = BackupCounts(shows = 240, movies = 450, episodes = 7000)

    assertThat(grown.wouldTruncate(fullLibrary)).isFalse()
  }

  @Test
  fun `Should allow the first ever backup`() {
    // Nothing stored yet means there is nothing to protect.
    assertThat(fullLibrary.wouldTruncate(BackupCounts(0, 0, 0))).isFalse()
  }

  @Test
  fun `Should read back the counts it wrote onto the Drive file`() {
    val restored = BackupCounts.fromProperties(fullLibrary.toProperties())

    assertThat(restored).isEqualTo(fullLibrary)
  }

  @Test
  fun `Should treat a file with no recorded counts as unknown`() {
    // A backup written before counts were recorded. Unknown must not read as zero,
    // which would look like an empty backup and disable the guard.
    assertThat(BackupCounts.fromProperties(null)).isNull()
    assertThat(BackupCounts.fromProperties(mapOf("shows" to "233"))).isNull()
  }
}
