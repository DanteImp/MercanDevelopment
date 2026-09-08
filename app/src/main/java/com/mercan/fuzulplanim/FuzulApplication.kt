package com.mercan.fuzulplanim

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class FuzulApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
