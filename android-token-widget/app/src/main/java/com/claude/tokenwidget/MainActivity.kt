package com.claude.tokenwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.claude.tokenwidget.ui.ConfigScreen
import com.claude.tokenwidget.ui.theme.ClaudeTokenTheme

/**
 * Hosts the configuration UI. Tapping the home-screen widget opens this
 * screen; it's where the user picks the data source and (in local mode) edits
 * the session / weekly limits and current usage.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClaudeTokenTheme {
                ConfigScreen()
            }
        }
    }
}
