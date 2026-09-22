package com.ergrm.trainer.data

import kotlinx.serialization.Serializable

/** What a device switch or reinstall would otherwise lose that isn't already covered elsewhere:
 *  Intervals.icu credentials, FTP/LTHR, remembered trainer/HR sensor, library folder. Workout
 *  history is backed up separately — one file per session, written automatically after every
 *  save (see BackupRepository.writeSessionToFolder / syncSessionsFromFolder) — rather than
 *  bundled into this manually-triggered export/import, which stays small and instant either way. */
@Serializable
data class SettingsBackup(val settings: AppSettings)
