package com.ergrm.trainer.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/** A tiny bridge from [com.ergrm.trainer.MainActivity]'s intent handling (outside any
 *  Composable's scope) into [AppNav]'s in-memory [Overlay] state — the backup reminder
 *  notification's tap target is the only thing that needs to jump straight to a specific screen
 *  from outside the Compose tree, so a single-purpose event beats plumbing real deep links. */
object AppNavigationEvents {
    val openSettingsForBackup = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Set from MainActivity.onUserLeaveHint() — called only when the user deliberately navigates
    // away (Home key, recent-apps switcher), never when the app itself starts another activity
    // (a folder picker, the TCX share sheet, a permission prompt). AppNav checks this on the next
    // ON_START to reset to the Workout screen, without mistaking "briefly backgrounded for a
    // system picker" for "the user actually left and came back".
    //
    // A plain synchronous flag rather than a SharedFlow deliberately: handleIntent() (called from
    // onNewIntent, which Android guarantees runs before onStart) clears this flag directly when
    // the backup-reminder notification requests a specific destination, so that explicit request
    // always wins over the generic reset with no coroutine-dispatch race between the two.
    @Volatile
    var userLeftApp: Boolean = false
}
