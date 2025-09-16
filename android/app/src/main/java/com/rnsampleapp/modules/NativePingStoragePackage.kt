package com.pingstorage

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class NativePingStoragePackage : TurboReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext) =
        if (name == "NativePingStorage") NativePingStorageModule(reactContext) else null

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider {
            val map = HashMap<String, ReactModuleInfo>()
            map["NativePingStorage"] = ReactModuleInfo(
                /* name for JS */ "NativePingStorage",
                /* class name */ "com.pingstorage.NativePingStorageModule",
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
