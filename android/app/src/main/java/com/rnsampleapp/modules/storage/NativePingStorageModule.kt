package com.rnsampleapp.modules.storage

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.facebook.fbreact.specs.NativePingStorageSpec
import com.facebook.react.bridge.*
import com.pingidentity.journey.*
import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.callback.PasswordCallback
import com.pingidentity.journey.callback.TextInputCallback
import com.pingidentity.journey.callback.TextOutputCallback
import com.pingidentity.journey.module.Oidc
import com.pingidentity.journey.module.Session
import com.pingidentity.journey.module.SessionConfig
import com.pingidentity.journey.module.RequestUrl
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.SSOToken
import com.pingidentity.journey.journey
import com.pingidentity.journey.options
import com.pingidentity.orchestrate.Node
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.Session
import com.pingidentity.orchestrate.module.Cookie
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Module
import com.pingidentity.oidc.*
import com.pingidentity.oidc.module.*
import com.pingidentity.oidc.exception.AuthorizeException
import com.pingidentity.storage.*
import com.pingidentity.utils.Result
import com.pingidentity.utils.Result.Failure
import com.pingidentity.utils.Result.Success
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

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
    // Journey SDK instance
    private var journey: Journey? = null
    // The most recent node from the Journey SDK
    private var lastNode: Node? = null

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


    // ===== Journey Methods =====

    /**
     * Helper function to convert a Journey SDK Node into a WritableMap for React Native.
     */
    private fun serializeNode(node: Node): WritableMap {

        val map = Arguments.createMap()
        map.putString("id", node.hashCode().toString())

        when (node) {
            is ContinueNode -> {
                Log.d("NativePingStorage", "Serializing ContinueNode with ${node.callbacks.size} callbacks")
                map.putString("type", "ContinueNode")
                val callbacksArray = Arguments.createArray()
                node.callbacks.forEach { cb ->
                    val callbackMap = Arguments.createMap()
                    callbackMap.putString("type", cb::class.java.simpleName)
                    // Add specific properties based on callback type
                    when (cb) {
                        is TextOutputCallback -> {
                            callbackMap.putString("message", cb.message)
                            callbackMap.putString("prompt", cb.message)
                        }
                        is TextInputCallback -> {
                            callbackMap.putString("prompt", cb.prompt)
                            callbackMap.putString("value", cb.text ?: "")
                        }
                        is PasswordCallback -> {
                            callbackMap.putString("prompt", cb.prompt)
                            callbackMap.putString("value", "") // Don't expose password
                        }
                        is NameCallback -> {
                            callbackMap.putString("prompt", cb.prompt)
                            callbackMap.putString("value", cb.name ?: "")
                        }
                        else -> {
                            // For unknown callback types, try to get prompt if available
                            callbackMap.putString("prompt", "")
                            callbackMap.putString("value", "")
                        }
                    }
                    callbacksArray.pushMap(callbackMap)
                }
                map.putArray("callbacks", callbacksArray)
            }
            is ErrorNode -> {
                map.putString("type", "ErrorNode")
                map.putString("message", node.message)
            }
            is SuccessNode -> {
                map.putString("type", "SuccessNode")
                // The session should be automatically established by the SDK when SuccessNode is received
                // The user can retrieve session data via getSession() method
                Log.d("NativePingStorage", "Serialized SuccessNode - session established, data available via getSession()")
            }
            is FailureNode -> {
                map.putString("type", "FailureNode")
                map.putString("message", node.cause.message ?: node.cause.toString())
                Log.d("NativePingStorage", "Serialized FailureNode with message: ${node.cause.message ?: node.cause.toString()}")
            }
            else -> {
                map.putString("type", "UnknownNode")
            }
        }
        Log.d("NativePingStorage", "Serialized node map: ${map.toString()}")
        return map
    }
    
    override fun configureJourney(config: ReadableMap, promise: Promise) {
        try {
            Log.d("NativePingStorage", "configureJourney called with: $config")

            if (!config.hasKey("serverUrl")) {
                promise.reject("CONFIG_ERROR", "Missing required parameter: serverUrl")
                return
            }
            val serverUrl = config.getString("serverUrl")!!

            // Optional parameters
            val realm = config.getString("realm")
            val cookieName = config.getString("cookie")
            val clientId = config.getString("clientId")
            val discoveryEndpoint = config.getString("discoveryEndpoint")
            val redirectUri = config.getString("redirectUri")
            val scopesArray = config.getArray("scopes")?.toArrayList()?.mapNotNull { it.toString() }

            // Initialize the Journey SDK 
            Log.d("NativePingStorage", "Initializing Journey SDK...")
            
            this.journey = Journey {
                this.timeout = 30000 // Network request timeout in milliseconds (default: 15000ms)
                this.serverUrl = serverUrl
                realm?.let { this.realm = it }
                cookieName?.let { 
                    this.cookie = it

                }
                
                // Configure OIDC module 
                if (clientId != null && discoveryEndpoint != null && redirectUri != null) {
                     Log.d("NativePingStorage", "Configuring OIDC module...")
                    this.module(Oidc) {
                        this.clientId = clientId
                        this.discoveryEndpoint = discoveryEndpoint
                        this.redirectUri = redirectUri
                        this.scopes = scopesArray?.toMutableSet() ?: mutableSetOf("openid", "email", "address", "profile", "phone")
                    }
                    Log.d("NativePingStorage", "OIDC module configured successfully")
                } else {
                    Log.d("NativePingStorage", "OIDC module NOT configured - missing parameters")
                }
            }
            if(this.journey != null) {
                Log.d("NativePingStorage", "Journey SDK initialized successfully")
                promise.resolve(true)
            } else {
                Log.d("NativePingStorage", "Journey SDK initialization FAILED")
            }
        } catch (e: Exception) {
            Log.e("NativePingStorage", "Error configuring Journey", e)
            promise.reject("CONFIG_ERROR", "Failed to configure Journey: ${e.message}", e)
        }
    }


    override fun start(journeyName: String, options: ReadableMap?, promise: Promise) {
        Log.d("NativePingStorage", "Start called for journey: '$journeyName' with options: $options")

        val journeyInstance = this.journey
        if (journeyInstance == null) {
            promise.reject("NOT_CONFIGURED", "Journey SDK has not been configured. Call configureJourney() first.")
            return
        }

        val forceAuth = options?.getBoolean("forceAuth") ?: false
        val noSession = options?.getBoolean("noSession") ?: false

        scope.launch {
            try {
                Log.d("NativePingStorage", "Starting journey with forceAuth=$forceAuth, noSession=$noSession")
                val node = journeyInstance.start(journeyName) {
                    this.forceAuth = forceAuth
                    this.noSession = noSession
                }
                this@NativePingStorageModule.lastNode = node
                val serialized = serializeNode(node)
                promise.resolve(serialized)
            } catch (e: Exception) {
                Log.e("NativePingStorage", "Exception in start: ${e.message}", e)
                promise.reject("START_ERROR", "Failed to start journey '$journeyName': ${e.message}", e)
            }
        }
    }

    override fun next(nodeId: String, input: ReadableMap, promise: Promise) {
        Log.d("NativePingStorage", "next called for nodeId: $nodeId with input: $input")

        val journeyInstance = this.journey
        if (journeyInstance == null) {
            promise.reject("NOT_CONFIGURED", "Journey SDK has not been configured. Call configureJourney() first.")
            return
        }

        val currentNode = this.lastNode
        if (currentNode == null) {
            promise.reject("NO_ACTIVE_JOURNEY", "There is no active journey in progress. Call start() first.")
            return
        }

        if (currentNode !is ContinueNode) {
            promise.reject("INVALID_STATE", "The current journey node is not a ContinueNode and does not accept input.")
            return
        }

        // Apply input from the ReadableMap to the corresponding callbacks
        try {
            val inputMap = input.toHashMap()
            
            if (inputMap.containsKey("callbacks")) {
                val callbacksArray = inputMap["callbacks"] as? ArrayList<*>
                Log.d("NativePingStorage", "Found callbacks array with ${callbacksArray?.size} items")
                
                callbacksArray?.forEach { callbackData ->
                    val callbackMap = callbackData as? HashMap<*, *>
                    val type = callbackMap?.get("type") as? String
                    val value = callbackMap?.get("value") as? String ?: ""
                    
                    when (type) {
                        "NameCallback" -> {
                            val nameCB = currentNode.callbacks.firstOrNull { it is NameCallback } as? NameCallback
                            nameCB?.let {
                                it.name = value
                            }
                        }
                        "PasswordCallback" -> {
                            val passCB = currentNode.callbacks.firstOrNull { it is PasswordCallback } as? PasswordCallback
                            passCB?.let {
                                it.password = value
                            }
                        }
                        "TextInputCallback" -> {
                            val textCB = currentNode.callbacks.firstOrNull { it is TextInputCallback } as? TextInputCallback
                            textCB?.let {
                                it.text = value
                            }
                        }
                        else -> {
                            Log.w("NativePingStorage", "Unhandled callback type in next: $type. No input applied.")
                        }
                    }
                }
            } else {
                Log.w("NativePingStorage", "No 'callbacks' key found in input. Available keys: ${inputMap.keys}")
            }
        } catch (e: Exception) {
            promise.reject("INPUT_ERROR", "Failed to set callback values: ${e.message}", e)
            return
        }

        scope.launch {
            try {
                val nextNode = currentNode.next()
                this@NativePingStorageModule.lastNode = nextNode
                promise.resolve(serializeNode(nextNode))
            } catch (e: Exception) {
                Log.e("NativePingStorage", "Exception in next: ${e.message}", e)
                promise.reject("NEXT_ERROR", "Failed to proceed to next node: ${e.message}", e)
            }
        }
    }

    override fun resume(uri: String, promise: Promise) {
        Log.d("NativePingStorage", "resume called with uri: $uri")
        
        val journeyInstance = this.journey
        if (journeyInstance == null) {
            promise.reject("NOT_CONFIGURED", "Journey SDK has not been configured. Call configureJourney() first.")
            return
        }
        
        scope.launch {
            try {
                val resumedNode = journeyInstance.resume(android.net.Uri.parse(uri))
                this@NativePingStorageModule.lastNode = resumedNode
                val serialized = serializeNode(resumedNode)
                promise.resolve(serialized)
            } catch (e: Exception) {
                Log.e("NativePingStorage", "Exception in resume: ${e.message}", e)
                promise.reject("RESUME_ERROR", "Failed to resume journey with URI '$uri': ${e.message}", e)
            }
        }
    }

    override fun getSession(promise: Promise) {
        
        val journeyInstance = this.journey
        if (journeyInstance == null) {
            promise.reject("NOT_CONFIGURED", "Journey SDK has not been configured. Call configureJourney() first.")
            return
        }
        
        scope.launch {
            try {
                Log.d("NativePingStorage", "Starting getting user session...")

                val user = journeyInstance.user()

                if (user == null) {
                    Log.d("NativePingStorage", "Get session failed - No user available")
                    promise.resolve(null)
                    return@launch
                }
                
                Log.d("NativePingStorage", "User object retrieved, fetching session data...")
                
                // Fetch token()  - returns Result<Token, OidcError>
                when (val tokenResult = user.token()) {
                    is Result.Success -> {
                        val token = tokenResult.value
                        
                        // Fetch userinfo() only if token was successful - returns Result<JsonObject, OidcError>
                        var userInfoMap: WritableMap? = null
                        when (val result = user.userinfo(false)) {
                            is Result.Failure -> {
                                Log.w("NativePingStorage", "Error fetching user info: ${result.value}")
                                userInfoMap = null
                            }
                            is Result.Success -> {
                                val userInfoJson = result.value
                                userInfoMap = Arguments.createMap()
                                userInfoJson.forEach { (key, value) ->
                                    when (value) {
                                        is JsonPrimitive -> {
                                            when {
                                                value.isString -> userInfoMap.putString(key, value.content)
                                                else -> userInfoMap.putString(key, value.toString())
                                            }
                                        }
                                        is JsonArray -> {
                                            userInfoMap.putString(key, value.toString()) // Convert arrays to string
                                        }
                                        is JsonObject -> {
                                            userInfoMap.putString(key, value.toString()) // Convert nested objects to string
                                        }
                                        else -> {
                                            userInfoMap.putString(key, value.toString())
                                        }
                                    }
                                }
                                Log.d("NativePingStorage", "User info fetched successfully")
                            }
                        }

                        val resultMap = Arguments.createMap()
                        resultMap.putString("accessToken", token.accessToken)
                        resultMap.putString("refreshToken", token.refreshToken ?: "")
                        resultMap.putLong("expiresIn", token.expiresIn)
                        
                        if (userInfoMap != null) {
                            resultMap.putMap("userInfo", userInfoMap)
                        }

                        promise.resolve(resultMap)
                    }
                    is Result.Failure -> {
                        Log.e("NativePingStorage", "Error fetching token: ${tokenResult.value}")
                        // No valid session - reject with clear error message
                        promise.reject("NO_SESSION", "No active session. Please authenticate first by completing the journey flow.")
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e("NativePingStorage", "Error in getSession", e)
                promise.reject("GET_SESSION_ERROR", "Failed to get session: ${e.message}", e)
            }
        }
    }

    override fun logout(promise: Promise) {
        
        val journeyInstance = this.journey
        if (journeyInstance == null) {
            promise.reject("NOT_CONFIGURED", "Journey SDK has not been configured. Call configureJourney() first.")
            return
        }
        
        scope.launch {
            try {
                val user = journeyInstance.user()
                user?.logout()
                Log.d("NativePingStorage", "User logged out successfully")
                promise.resolve(true)
            } catch (e: Exception) {
                Log.e("NativePingStorage", "Error during logout", e)
                promise.reject("LOGOUT_ERROR", "Failed to logout: ${e.message}", e)
            }
        }
    }

}
