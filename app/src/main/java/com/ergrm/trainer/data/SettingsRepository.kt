package com.ergrm.trainer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ergrm_settings")

data class AppSettings(
    val intervalsApiKey: String = "",
    val intervalsAthleteId: String = "",
    val ftpWatts: Int = 275,
    val lthrBpm: Int = 170,
    val lastDeviceAddress: String? = null,
    val lastDeviceName: String? = null,
    val lastHrDeviceAddress: String? = null,
    val lastHrDeviceName: String? = null,
    val libraryFolderUri: String? = null,
    val libraryFolderName: String? = null,
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
        val HR_DEVICE_ADDRESS = stringPreferencesKey("last_hr_device_address")
        val HR_DEVICE_NAME = stringPreferencesKey("last_hr_device_name")
        val LIBRARY_FOLDER_URI = stringPreferencesKey("library_folder_uri")
        val LIBRARY_FOLDER_NAME = stringPreferencesKey("library_folder_name")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            intervalsApiKey = prefs[Keys.API_KEY] ?: "",
            intervalsAthleteId = prefs[Keys.ATHLETE_ID] ?: "",
            ftpWatts = prefs[Keys.FTP] ?: 275,
            lthrBpm = prefs[Keys.LTHR] ?: 170,
            lastDeviceAddress = prefs[Keys.DEVICE_ADDRESS],
            lastDeviceName = prefs[Keys.DEVICE_NAME],
            lastHrDeviceAddress = prefs[Keys.HR_DEVICE_ADDRESS],
            lastHrDeviceName = prefs[Keys.HR_DEVICE_NAME],
            libraryFolderUri = prefs[Keys.LIBRARY_FOLDER_URI],
            libraryFolderName = prefs[Keys.LIBRARY_FOLDER_NAME],
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

    suspend fun rememberDevice(address: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_ADDRESS] = address
            prefs[Keys.DEVICE_NAME] = name
        }
    }

    suspend fun rememberHrDevice(address: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HR_DEVICE_ADDRESS] = address
            prefs[Keys.HR_DEVICE_NAME] = name
        }
    }

    suspend fun setLibraryFolder(uri: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIBRARY_FOLDER_URI] = uri
            prefs[Keys.LIBRARY_FOLDER_NAME] = name
        }
    }
}
