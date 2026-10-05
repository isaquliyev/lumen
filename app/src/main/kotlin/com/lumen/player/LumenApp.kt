package com.lumen.player

import android.app.Application
import com.lumen.player.core.design.LumenFonts
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LumenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LumenFonts.init(assets)
    }
}
