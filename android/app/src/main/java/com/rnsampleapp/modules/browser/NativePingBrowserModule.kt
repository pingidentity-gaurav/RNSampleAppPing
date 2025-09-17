package com.rnsampleapp.modules.browser

import android.net.Uri
import com.facebook.fbreact.specs.NativePingBrowserSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.pingidentity.browser.BrowserLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL

class NativePingBrowserModule(context: ReactApplicationContext) :
    NativePingBrowserSpec(context) {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun getName() = "NativePingBrowser"

    override fun launch(url: String, redirectUri: String?, promise: Promise) {
        scope.launch {
            try {
                val result: Result<Uri> = BrowserLauncher.launch(URL(url))
                val uri = result.getOrThrow()
                promise.resolve(uri.toString())
            } catch (e: Exception) {
                promise.reject("PING_BROWSER_ERROR", e)
            }
        }
    }
}