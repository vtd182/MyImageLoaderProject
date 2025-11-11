package com.example.myimageloaderproject

import android.app.Application
import com.example.myimageloaderproject.core.di.AppContainer

class MyApplication : Application() {
    
    lateinit var appContainer: AppContainer
        private set
    
    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
