package app.opal

import android.app.Application
import android.os.Build
import android.os.StrictMode

class OpalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // The :tunnel process builds its own graph lazily (TunnelRuntime); keep it lean.
        if (isTunnelProcess()) return
        if (BuildConfig.DEBUG) enableStrictMode()
        AppGraph.init(this)
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
