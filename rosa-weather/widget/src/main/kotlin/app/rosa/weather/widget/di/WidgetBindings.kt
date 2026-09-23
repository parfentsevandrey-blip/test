package app.rosa.weather.widget.di

import app.rosa.weather.core.data.sync.WeatherSyncListener
import app.rosa.weather.widget.WidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetBindings {
    /** Every sync (and every render tick) redraws the widgets. */
    @Binds
    @IntoSet
    abstract fun widgetListener(updater: WidgetUpdater): WeatherSyncListener
}
