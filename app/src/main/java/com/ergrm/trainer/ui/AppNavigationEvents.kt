package com.ergrm.trainer.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/** A tiny bridge from [com.ergrm.trainer.MainActivity]'s intent handling (outside any
 *  Composable's scope) into [AppNav]'s in-memory [Overlay] state — the backup reminder
 *  notification's tap target is the only thing that needs to jump straight to a specific screen
 *  from outside the Compose tree, so a single-purpose event beats plumbing real deep links. */
object AppNavigationEvents {
    val openSettingsForBackup = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Set from MainActivity.onUserLeaveHint(). In practice Android fires this not only for a
    // deliberate Home/recent-apps leave but also when this activity itself launches a SAF file/
    // folder picker or a share-sheet chooser — contrary to the simpler assumption this flag
    // started from. AppNav checks this on the next ON_START to reset to the Workout screen, but
    // only when pickerLaunchInFlight (below) says that ON_START ISN'T one of those picker returns.
    //
    // A plain synchronous flag rather than a SharedFlow deliberately: handleIntent() (called from
    // onNewIntent, which Android guarantees runs before onStart) clears this flag directly when
    // the backup-reminder notification requests a specific destination, so that explicit request
    // always wins over the generic reset with no coroutine-dispatch race between the two.
    @Volatile
    var userLeftApp: Boolean = false

    // Set true by a screen right before it calls launch() on a rememberLauncherForActivityResult
    // (a folder/file picker, a permission prompt) or starts a share-sheet chooser — anything that
    // takes over the foreground and returns here via onActivityResult/onStart, which turned out to
    // also set userLeftApp above despite being initiated by the app itself, not the user leaving.
    // AppNav's ON_START observer reads and clears this on every ON_START (it doesn't need a
    // per-launcher callback to clear it — the next ON_START after any one of these picker returns
    // is exactly the point this flag needs to have been true at), so a reset Task #68 would
    // otherwise trigger — losing e.g. Settings' pending "Import backup" confirmation before the
    // rider can tap Apply — is skipped for that one return trip.
    @Volatile
    var pickerLaunchInFlight: Boolean = false
}
