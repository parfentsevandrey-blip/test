package app.papersky.weather

import android.app.Application

/** Test application: same dependency graph, no background scheduling. */
class TestApp : Application(), ContainerHost {
    override val container: AppContainer by lazy { AppContainer(this) }
}
