package app.trakr

import android.app.Application
import app.trakr.data.AppContainer

class TrakrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
