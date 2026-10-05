package com.lumen.player

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lumen.player.core.design.LumenFonts
import com.lumen.player.download.createDownloadChannel
import com.lumen.player.download.sources.YtDlpManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LumenApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var ytdlp: YtDlpManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        LumenFonts.init(assets)
        createDownloadChannel(this)
        ytdlp.init()
    }
}
