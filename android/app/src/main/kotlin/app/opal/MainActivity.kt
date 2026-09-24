package app.opal

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.opal.core.data.AppLocaleStore
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.settings.AppLanguage
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.ThemeMode
import app.opal.feature.settings.SystemIntents
import app.opal.ui.OpalRoot
import app.opal.ui.ShellHost
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** First settings value; the splash screen stays until it is known (theme, onboarding). */
    private val initial = mutableStateOf<AppSettings?>(null)
    private var jank: JankReporter? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { initial.value == null }
        lifecycleScope.launch { initial.value = AppGraph.settings.current() }
        if (BuildConfig.DEBUG) jank = JankReporter(window)

        setContent {
            val first = initial.value ?: return@setContent
            val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(first)
            val snapshot by AppGraph.tunnel.snapshot.collectAsStateWithLifecycle()
            val dark =
                when (settings.theme) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            DisposableEffect(dark) {
                // System bar icons follow the app theme, which may differ from the system one.
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            OpalTheme(dark = dark, simplifiedGraphics = settings.simplifiedGraphics) {
                OpalRoot(
                    startWithOnboarding = !first.onboardingCompleted,
                    tunnelState = snapshot.state,
                    host = shellHost,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.tunnel.acquire()
        lifecycleScope.launch { PrepareOnOpen.onAppVisible() }
    }

    override fun onResume() {
        super.onResume()
        jank?.resume()
    }

    override fun onPause() {
        jank?.pause()
        super.onPause()
    }

    override fun onStop() {
        if (!isChangingConfigurations) PrepareOnOpen.onAppHidden()
        AppGraph.tunnel.release()
        super.onStop()
    }

    private val shellHost =
        object : ShellHost {
            override val versionName: String = BuildConfig.VERSION_NAME
            override val language: AppLanguage
                get() = AppLocaleStore.current(this@MainActivity)

            override fun setLanguage(language: AppLanguage) {
                // Android 13+ recreates the activity itself; below that we do it.
                if (AppLocaleStore.set(this@MainActivity, language)) recreate()
            }

            override fun onboardingFinished() {
                lifecycleScope.launch {
                    AppGraph.settings.update { it.copy(onboardingCompleted = true) }
                    PrepareOnOpen.onAppVisible()
                }
            }

            override fun openVpnSettings() {
                SystemIntents.openVpnSettings(this@MainActivity)
            }

            override fun requestBatteryExemption() =
                SystemIntents.requestBatteryExemption(this@MainActivity)

            override fun isIgnoringBatteryOptimizations(): Boolean =
                SystemIntents.read(this@MainActivity).ignoringBatteryOptimizations
        }
}
