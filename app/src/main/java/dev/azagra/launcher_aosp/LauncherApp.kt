package dev.azagra.launcher_aosp

import android.app.Application
import dev.azagra.launcher_aosp.data.AppRepository
import dev.azagra.launcher_aosp.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow

class Container(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val apps = AppRepository(app, scope)

    val homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    var currentPage = 0
}

class LauncherApp : Application() {
    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        container = Container(this)
        container.apps.start()
    }
}
