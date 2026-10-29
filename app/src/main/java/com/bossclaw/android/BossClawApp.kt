package com.bossclaw.android

import android.app.Application
import com.bossclaw.android.di.AppGraph
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class BossClawApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        graph = AppGraph(this)
        graph.init()
    }

    companion object {
        lateinit var graph: AppGraph
            private set
    }
}
