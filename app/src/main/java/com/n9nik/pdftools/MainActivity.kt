package com.n9nik.pdftools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.n9nik.pdftools.ads.ConsentManager
import com.n9nik.pdftools.ui.PdfApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // PDFBox scratch space for streaming large documents without OOM.
        runCatching {
            System.setProperty("java.io.tmpdir", cacheDir.absolutePath)
        }

        val consentManager = ConsentManager(this)
        MobileAds.initialize(this) {}

        setContent {
            var consentReady by remember { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                consentManager.gatherConsent(this@MainActivity) { consentReady = true }
            }
            PdfApp(
                adsEnabled = consentReady && consentManager.canRequestAds,
                onPrivacyOptions = {
                    consentManager.showPrivacyOptions(this@MainActivity) {}
                },
            )
        }
    }
}
