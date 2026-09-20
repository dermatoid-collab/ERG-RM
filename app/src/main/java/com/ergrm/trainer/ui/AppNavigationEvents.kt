package com.ergrm.trainer.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/** A tiny bridge from [com.ergrm.trainer.MainActivity]'s intent handling (outside any
 *  Composable's scope) into [AppNav]'s in-memory [Overlay] state — the backup reminder
 *  notification's tap target is the only thing that needs to jump straight to a specific screen
 *  from outside the Compose tree, so a single-purpose event beats plumbing real deep links. */
object AppNavigationEvents {
    val openSettingsForBackup = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
