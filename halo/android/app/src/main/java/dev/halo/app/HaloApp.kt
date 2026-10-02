package dev.halo.app

import android.app.Application
import dev.halo.core.initLogging

class HaloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initLogging()
        Halo.init(this)
    }
}
