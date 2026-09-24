package app.opal

import android.app.Application
import android.os.Build
import android.os.StrictMode
import app.opal.work.DirectoryRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OpalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // The :tunnel process builds its own graph lazily (TunnelRuntime); keep it lean.
        if (isTunnelProcess()) return
        if (BuildConfig.DEBUG) enableStrictMode()
        AppGraph.init(this)
        scheduleDirectoryRefresh()
    }

    /** Background directory refresh follows its setting (on by default, after onboarding). */
    private fun scheduleDirectoryRefresh() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            AppGraph.settings.settings
                .map { it.onboardingCompleted && it.backgroundDirectoryRefresh }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) DirectoryRefreshWorker.schedule(this@OpalApp)
                    else DirectoryRefreshWorker.cancel(this@OpalApp)
                }
        }
    }

    private fun isTunnelProcess(): Boolean {
        val name =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) getProcessName()
            else currentProcessNameLegacy()
        return name?.endsWith(":tunnel") == true
    }

    private fun currentProcessNameLegacy(): String? = runCatching {
        java.io.File("/proc/self/cmdline").readText().trimEnd('\u0000')
    }
        .getOrNull()

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }
}
