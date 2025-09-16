package com.pingstorage

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.facebook.fbreact.specs.NativePingStorageSpec
import com.facebook.react.bridge.*
import com.pingidentity.storage.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

fun createStringDataStore(context: Context, fileName: String): DataStore<String?> {
    return DataStoreFactory.create(
        serializer = DataToJsonSerializer(),
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    ) {
        File(context.filesDir, "datastore/$fileName")
    }
}

class NativePingStorageModule(context: ReactApplicationContext) :
    NativePingStorageSpec(context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var storage: Storage<String>? = null

    override fun getName() = "NativePingStorage"

    override fun configure(config: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                Log.d("NativePingStorage", "Configuring storage with: $config")

                val type = config.getString("type") ?: "encrypted"
                val fileName = config.getString("fileName") ?: "secure_prefs"
                val cacheStrategy = when (config.getString("cacheStrategy")) {
                    "CACHE" -> CacheStrategy.CACHE
                    "CACHE_ON_FAILURE" -> CacheStrategy.CACHE_ON_FAILURE
                    else -> CacheStrategy.NO_CACHE
                }

                storage = when (type) {
                    "memory" -> {
                        Log.d("NativePingStorage", "Using MemoryStorage")
                        MemoryStorage<String>()
                    }
                    "datastore" -> {
                        Log.d("NativePingStorage", "Using DataStoreStorage with fileName=$fileName")
                        val dataStore = createStringDataStore(reactApplicationContext, fileName)
                        DataStoreStorage(
                            dataStore = dataStore,
                            cacheStrategy = cacheStrategy
                        )
                    }
                    else -> {
                        val keyAlias = config.getString("keyAlias") ?: "defaultKey"
                        val strongBox = config.getBoolean("strongBoxPreferred")
                        Log.d(
                            "NativePingStorage",
                            "Using EncryptedDataStoreStorage with fileName=$fileName, keyAlias=$keyAlias, strongBox=$strongBox"
                        )
                        EncryptedDataStoreStorage<String> {
                            this.fileName = fileName
                            this.cacheStrategy = cacheStrategy
                            this.keyAlias = keyAlias
                            this.strongBoxPreferred = strongBox
                        }
                    }
                }

                Log.d("NativePingStorage", "Storage configured successfully ✅")
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e("NativePingStorage", "Error configuring storage", e)
                promise.reject("CONFIG_ERROR", e)
            }
        }
    }

    override fun save(item: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val jsonString = JSONObject(item.toHashMap()).toString()
                Log.d("NativePingStorage", "Saving item: $jsonString")
                storage?.save(jsonString)
                Log.d("NativePingStorage", "Item saved ✅")
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e("NativePingStorage", "Error saving item", e)
                promise.reject("SAVE_ERROR", e)
            }
        }
    }

    override fun get(promise: Promise) {
        scope.launch {
            try {
                Log.d("NativePingStorage", "Fetching item from storage...")
                val jsonString = storage?.get()
                if (jsonString != null) {
                    Log.d("NativePingStorage", "Retrieved raw JSON: $jsonString")
                    val json = JSONObject(jsonString)
                    val map = Arguments.createMap()
                    json.keys().forEach { key ->
                        map.putString(key, json.getString(key))
                    }
                    Log.d("NativePingStorage", "Returning map: $map")
                    promise.resolve(map)
                } else {
                    Log.d("NativePingStorage", "No item found")
                    promise.resolve(null)
                }
            } catch (e: Exception) {
                Log.e("NativePingStorage", "Error fetching item", e)
                promise.reject("GET_ERROR", e)
            }
        }
    }

    override fun remove(promise: Promise) {
        scope.launch {
            try {
                Log.d("NativePingStorage", "Deleting item from storage...")
                storage?.delete()
                Log.d("NativePingStorage", "Item deleted ✅")
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e("NativePingStorage", "Error deleting item", e)
                promise.reject("DELETE_ERROR", e)
            }
        }
    }
}
