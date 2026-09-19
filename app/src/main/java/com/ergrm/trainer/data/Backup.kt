package com.ergrm.trainer.data

import com.ergrm.trainer.history.WorkoutSession
import kotlinx.serialization.Serializable

/** Everything a device switch or reinstall would otherwise lose: app settings (Intervals.icu
 *  credentials, FTP/LTHR, remembered trainer/HR sensor, library folder) plus the full workout
 *  history, combined into one file so there's a single export/import action to keep in sync. */
@Serializable
data class AppBackup(
    val settings: AppSettings,
    val history: List<WorkoutSession> = emptyList(),
)
