package com.cozyhome.weather

import android.app.Application
import com.cozyhome.weather.widget.WidgetUpdateWorker

class CozyWeatherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetUpdateWorker.schedule(this)
    }
}
