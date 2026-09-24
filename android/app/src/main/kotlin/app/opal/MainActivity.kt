package app.opal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val snapshot by AppGraph.tunnel.snapshot.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(snapshot.state.toString())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.tunnel.acquire()
    }

    override fun onStop() {
        AppGraph.tunnel.release()
        super.onStop()
    }
}
