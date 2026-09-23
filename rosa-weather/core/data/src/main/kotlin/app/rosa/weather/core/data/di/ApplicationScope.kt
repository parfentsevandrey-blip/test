package app.rosa.weather.core.data.di

import javax.inject.Qualifier

/**
 * A process-lifetime scope for work that must outlive the screen that started it (a sync after
 * adding a city, redrawing widgets on a theme change). Failures are logged, never fatal.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
