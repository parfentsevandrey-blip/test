package com.lumina.calendarwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumina.calendarwidget.ui.CustomizeScreen
import com.lumina.calendarwidget.ui.CustomizeViewModel
import com.lumina.calendarwidget.ui.theme.LuminaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuminaTheme {
                val vm: CustomizeViewModel = viewModel()
                CustomizeScreen(vm)
            }
        }
    }
}
