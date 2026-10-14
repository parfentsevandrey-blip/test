package com.example.retrodatabases

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.retrodatabases.ui.AppNavHost
import com.example.retrodatabases.ui.theme.RetroDatabasesTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RetroDatabasesTheme {
                AppNavHost()
            }
        }
    }
}
