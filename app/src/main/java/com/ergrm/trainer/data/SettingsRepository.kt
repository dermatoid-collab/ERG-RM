package com.ergrm.trainer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.dataStore by preferencesDataStore(name = "ergrm_settings")

@Serializable
data class AppSettings(
    val intervalsApiKey: String = "",
    val intervalsAthleteId: String = "",
    val ftpWatts: Int = 275,
    val lthrBpm: Int = 174,
    val lastDeviceAddress: String? = null,
    val lastDeviceName: String? = null,
    val lastDeviceNickname: String? = null,
    val lastHrDeviceAddress: String? = null,
    val lastHrDeviceName: String? = null,
    val lastHrDeviceNickname: String? = null,
    val libraryFolderUri: String? = null,
    val libraryFolderName: String? = null,
    val backupFolderUri: String? = null,
    val backupFolderName: String? = null,
) {
    val intervalsConfigured: Boolean get() = intervalsApiKey.isNotBlank() && intervalsAthleteId.isNotBlank()
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("intervals_api_key")
        val ATHLETE_ID = stringPreferencesKey("intervals_athlete_id")
        val FTP = intPreferencesKey("ftp_watts")
        val LTHR = intPreferencesKey("lthr_bpm")
        val DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val DEVICE_NAME = stringPreferencesKey("last_device_name")
        val DEVICE_NICKNAME = stringPreferencesKey("last_device_nickname")
        val HR_DEVICE_ADDRESS = stringPreferencesKey("last_hr_device_address")
        val HR_DEVICE_NAME = stringPreferencesKey("last_hr_device_name")
        val HR_DEVICE_NICKNAME = stringPreferencesKey("last_hr_device_nickname")
        val LIBRARY_FOLDER_URI = stringPreferencesKey("library_folder_uri")
        val LIBRARY_FOLDER_NAME = stringPreferencesKey("library_folder_name")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val BACKUP_FOLDER_NAME = stringPreferencesKey("backup_folder_name")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            intervalsApiKey = prefs[Keys.API_KEY] ?: "",
            intervalsAthleteId = prefs[Keys.ATHLETE_ID] ?: "",
            ftpWatts = prefs[Keys.FTP] ?: 275,
            lthrBpm = prefs[Keys.LTHR] ?: 174,
            lastDeviceAddress = prefs[Keys.DEVICE_ADDRESS],
            lastDeviceName = prefs[Keys.DEVICE_NAME],
            lastDeviceNickname = prefs[Keys.DEVICE_NICKNAME],
            lastHrDeviceAddress = prefs[Keys.HR_DEVICE_ADDRESS],
            lastHrDeviceName = prefs[Keys.HR_DEVICE_NAME],
            lastHrDeviceNickname = prefs[Keys.HR_DEVICE_NICKNAME],
            libraryFolderUri = prefs[Keys.LIBRARY_FOLDER_URI],
            libraryFolderName = prefs[Keys.LIBRARY_FOLDER_NAME],
            backupFolderUri = prefs[Keys.BACKUP_FOLDER_URI],
            backupFolderName = prefs[Keys.BACKUP_FOLDER_NAME],
        )
    }

    suspend fun updateIntervalsCredentials(apiKey: String, athleteId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.API_KEY] = apiKey
            prefs[Keys.ATHLETE_ID] = athleteId
        }
    }

    suspend fun updateFtp(ftpWatts: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.FTP] = ftpWatts }
    }

    suspend fun updateLthr(lthrBpm: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.LTHR] = lthrBpm }
    }

    /** A nickname belongs to the device it was set on — reconnecting to the *same* address keeps
     *  it, but connecting to a *different* one clears it rather than carrying it over onto a
     *  device it was never set for. */
    suspend fun rememberDevice(address: String, name: String) {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_ADDRESS] != address) prefs.remove(Keys.DEVICE_NICKNAME)
            prefs[Keys.DEVICE_ADDRESS] = address
            prefs[Keys.DEVICE_NAME] = name
        }
    }

    suspend fun rememberHrDevice(address: String, name: String) {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.HR_DEVICE_ADDRESS] != address) prefs.remove(Keys.HR_DEVICE_NICKNAME)
            prefs[Keys.HR_DEVICE_ADDRESS] = address
            prefs[Keys.HR_DEVICE_NAME] = name
        }
    }

    /** Blank clears the nickname (falls back to the device's standard BLE name everywhere it's
     *  shown), rather than storing an empty string as if it were a real one. */
    suspend fun setDeviceNickname(nickname: String) {
        context.dataStore.edit { prefs ->
            if (nickname.isBlank()) prefs.remove(Keys.DEVICE_NICKNAME) else prefs[Keys.DEVICE_NICKNAME] = nickname
        }
    }

    suspend fun setHrDeviceNickname(nickname: String) {
        context.dataStore.edit { prefs ->
            if (nickname.isBlank()) prefs.remove(Keys.HR_DEVICE_NICKNAME) else prefs[Keys.HR_DEVICE_NICKNAME] = nickname
        }
    }

    suspend fun setLibraryFolder(uri: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIBRARY_FOLDER_URI] = uri
            prefs[Keys.LIBRARY_FOLDER_NAME] = name
        }
    }

    suspend fun setBackupFolder(uri: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKUP_FOLDER_URI] = uri
            prefs[Keys.BACKUP_FOLDER_NAME] = name
        }
    }

    /** Restores every field from a backup in one atomic write. Nullable fields are only written
     *  when present in the backup, so restoring an older backup (from before a field existed)
     *  can't clobber a value set since then with a null. */
    suspend fun applyBackup(s: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.API_KEY] = s.intervalsApiKey
            prefs[Keys.ATHLETE_ID] = s.intervalsAthleteId
            prefs[Keys.FTP] = s.ftpWatts
            prefs[Keys.LTHR] = s.lthrBpm
            s.lastDeviceAddress?.let { prefs[Keys.DEVICE_ADDRESS] = it }
            s.lastDeviceName?.let { prefs[Keys.DEVICE_NAME] = it }
            s.lastDeviceNickname?.let { prefs[Keys.DEVICE_NICKNAME] = it }
            s.lastHrDeviceAddress?.let { prefs[Keys.HR_DEVICE_ADDRESS] = it }
            s.lastHrDeviceName?.let { prefs[Keys.HR_DEVICE_NAME] = it }
            s.lastHrDeviceNickname?.let { prefs[Keys.HR_DEVICE_NICKNAME] = it }
            s.libraryFolderUri?.let { prefs[Keys.LIBRARY_FOLDER_URI] = it }
            s.libraryFolderName?.let { prefs[Keys.LIBRARY_FOLDER_NAME] = it }
            s.backupFolderUri?.let { prefs[Keys.BACKUP_FOLDER_URI] = it }
            s.backupFolderName?.let { prefs[Keys.BACKUP_FOLDER_NAME] = it }
        }
    }
}
