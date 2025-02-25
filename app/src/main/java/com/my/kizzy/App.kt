package com.my.kizzy

import android.app.Application
import android.content.Context
import com.developer.crashx.config.CrashConfig
import com.google.android.material.color.DynamicColors
import com.my.kizzy.utils.AppUtils
import com.my.kizzy.utils.Log
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class App: Application() {
    init {
        instance = this
    }
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        applicationScope = CoroutineScope(SupervisorJob())
        DynamicColors.applyToActivitiesIfAvailable(this)
        CrashConfig.Builder.create()
            .errorActivity(CrashHandler::class.java)
            .apply()
        Log.init(this)
        AppUtils.init(this)
    }

    companion object{
        fun getContext(): Context {
            return instance.applicationContext
        }

        private lateinit var instance: App
        lateinit var applicationScope: CoroutineScope
    }
}