package com.ergrm.trainer

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ergrm.trainer.ui.AppNavigationEvents
import com.ergrm.trainer.ui.ErgRmApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A workout screen the rider glances at mid-effort shouldn't dim or lock while in use.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ErgRmApp()
        }
        handleIntent(intent)
    }

    // launchMode="singleTop" (see the manifest) routes a tap on the backup reminder notification
    // here instead of spawning a second instance of the activity while the app is already open.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_BACKUP_SETTINGS, false)) {
            AppNavigationEvents.openSettingsForBackup.tryEmit(Unit)
        }
    }

    companion object {
        const val EXTRA_OPEN_BACKUP_SETTINGS = "open_backup_settings"
    }
}
