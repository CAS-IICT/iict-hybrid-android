package com.hicling.iictcling

import android.app.Application
import android.content.Context

class App : Application() {
    companion object {
        private var appContext: Context? = null
        private var app: Application? = null
        fun getContext(): Context {
            return appContext!!
        }
        fun getApp():Application{
            return app!!
        }

    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        app= this
    }
}