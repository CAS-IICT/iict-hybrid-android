package com.hicling.iictcling

import android.app.Application
import android.content.Context

class App : Application() {
    companion object {
        private var appContext: Application? = null
        fun getContext(): Context {
            return appContext!!
        }

    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
    }
}