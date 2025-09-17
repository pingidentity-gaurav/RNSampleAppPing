package com.rnsampleapp.modules.browser

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.rnsampleapp.modules.browser.NativePingBrowserModule


class NativePingBrowserPackage : TurboReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext) =
        if (name == "NativePingBrowser") NativePingBrowserModule(reactContext) else null

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider {
            val map = HashMap<String, ReactModuleInfo>()
            map["NativePingBrowser"] = ReactModuleInfo(
                /* name for JS */ "NativePingBrowser",
                /* class name */ "com.rnsampleapp.modules.browser.NativePingBrowserModule",
                /* canOverrideExistingModule */ false,
                /* needsEagerInit */ false,
                /* hasConstants */ false,
                /* isCxxModule */ false,
                /* isTurboModule */ true
            )
            map
        }
    }
}