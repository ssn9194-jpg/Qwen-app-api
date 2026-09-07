package com.aistudio.superapp

import android.app.Application
import com.aistudio.superapp.di.AppContainer
import com.aistudio.superapp.di.DefaultAppContainer

class AiStudioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
