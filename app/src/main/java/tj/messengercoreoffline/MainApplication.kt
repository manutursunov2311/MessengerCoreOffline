package tj.messengercoreoffline

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import tj.messengercoreoffline.di.appModule
import tj.messengercoreoffline.di.uiModule

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        startKoin { 
            androidLogger()
            androidContext(this@MainApplication)
            modules(appModule, uiModule)
        }
    }
    
}