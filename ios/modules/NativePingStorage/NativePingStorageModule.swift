//
//  NativePingStorageModule.swift
//  RNSampleApp
//
//  Created by Thais Damasceno on 2025-09-18.
//

import Foundation
import PingStorage
import React
import PingJourney
import PingLogger
import PingOrchestrate

@objc(NativePingStorageModule)
class NativePingStorageModule: NSObject {
  private var storage: (any Storage<String>)?
  
  enum CacheStrategy {
    case noCache
    case cache
    case cacheOnFailure
  }
  
  
  private func parseCacheStrategy(from value: String?) -> CacheStrategy {
    print("NativePingStorageModule: Parsing cache strategy from value: \(String(describing: value))")
    switch value?.uppercased() {
    case "CACHE":
      return .cache
    case "CACHE_ON_FAILURE":
      return .cacheOnFailure
    default:
      return .noCache
    }
  }
  
  @objc
  func configure(_ config: [String: Any]) {
    print("NativePingStorageModule: configure called with config: \(config)")
    do {
      let type = config["type"] as? String ?? "encrypted"
      let keyAlias = (config["keyAlias"] as? String) ?? "com.pingidentity.rnsampleapp.keyalias"
      let cacheStrategy = parseCacheStrategy(from: config["cacheStrategy"] as? String)
      print("NativePingStorageModule: type=\(type), keyAlias=\(keyAlias), cacheStrategy=\(cacheStrategy)")
      let base: any Storage<String>
      switch type {
      case "memory":
        print("NativePingStorageModule: Using MemoryStorage")
        base = MemoryStorage<String>()
      case "encrypted", "datastore":
        print("NativePingStorageModule: Using KeychainStorage")
        base = KeychainStorage<String>(
          account: keyAlias,
          encryptor: NoEncryptor()
        )
      default:
        print("NativePingStorageModule: Unknown type, defaulting to MemoryStorage")
        base = MemoryStorage<String>()
      }
      
      switch cacheStrategy {
      case .noCache:
        print("NativePingStorageModule: No cache strategy")
        storage = base
      case .cache, .cacheOnFailure:
        print("NativePingStorageModule: Using StorageDelegate with cacheable true")
        storage = StorageDelegate(delegate: base, cacheable: true)
      }
      print("NativePingStorageModule: Storage configured successfully")
    } catch {
      print("NativePingStorageModule: Error configuring storage: \(error)")
    }
  }
  
  @objc
  func save(_ item: [String: Any], completion: @escaping(NSNumber?, NSError?) -> Void) {
    print("NativePingStorageModule: save called with item: \(item)")
    Task {
      do {
        let data = try JSONSerialization.data(withJSONObject: item, options: [])
        let jsonString = String(data: data, encoding: .utf8) ?? "{}"
        print("NativePingStorageModule: Saving jsonString: \(jsonString)")
        try await storage?.save(item: jsonString)
        print("NativePingStorageModule: Save successful")
        completion(NSNumber(value: true), nil)
      } catch {
        print("NativePingStorageModule: Error saving item: \(error)")
        completion(nil, error as NSError)
      }
    }
  }
  
  @objc
  func get(_ completion: @escaping(Any?, NSError?) -> Void) {
    print("NativePingStorageModule: get called")
    Task {
      do {
        if let json = try await storage?.get() {
          print("NativePingStorageModule: Retrieved json: \(json)")
          if let data = json.data(using: .utf8) {
            let item = try JSONSerialization.jsonObject(with: data, options: [])
            print("NativePingStorageModule: Returning item: \(item)")
            completion(item, nil)
          } else {
            print("NativePingStorageModule: Could not convert json to data")
            completion(nil, nil)
          }
        } else {
          print("NativePingStorageModule: No data found in storage")
          completion(nil, nil)
        }
      } catch {
        print("NativePingStorageModule: Error getting item: \(error)")
        completion(nil, error as NSError)
      }
    }
  }
  
  @objc
  func remove(_ completion: @escaping(NSNumber?, NSError?) -> Void) {
    print("NativePingStorageModule: remove called")
    Task {
      do {
        try await storage?.delete()
        print("NativePingStorageModule: Remove successful")
        completion(NSNumber(value: true), nil)
      } catch {
        print("NativePingStorageModule: Error removing item: \(error)")
        completion(nil, error as NSError)
      }
    }
  }
  
  // MARK: - Journey Methods
  
  private var journey: Journey?
  private var lastNode: Node?
  
  // Helper to convert Node → JS object
  private func serializeNode(_ node: Node) -> NSDictionary {
    var dict: [String: Any] = ["id": UUID().uuidString]
    
    switch node {
    case let continueNode as ContinueNode:
      dict["type"] = "ContinueNode"
      dict["callbacks"] = continueNode.callbacks.map { cb in
        [
          "type": String(describing: type(of: cb)),
          "prompt": (cb as? TextOutputCallback)?.message ?? "",
          "value": NSNull()
        ]
      }
    case let errorNode as ErrorNode:
      dict["type"] = "ErrorNode"
      dict["message"] = errorNode.message
    case let failureNode as FailureNode:
      dict["type"] = "FailureNode"
      dict["cause"] = failureNode.cause.localizedDescription
    case let successNode as SuccessNode:
      dict["type"] = "SuccessNode"
      dict["session"] = successNode.session
    default:
      dict["type"] = "Unknown"
    }
    
    return dict as NSDictionary
  }
  
  
  // MARK: - Configure Journey (already working)
  @objc(configureJourney:completion:)
  func configureJourney(_ config: NSDictionary,
                        completion: @escaping (NSNumber?, NSError?) -> Void) {
    print("NativePingStorageModule: configureJourney called with \(config)")
    
    guard let serverUrl = config["serverUrl"] as? String else {
      let error = NSError(domain: "NativePingStorageModule",
                          code: 400,
                          userInfo: [NSLocalizedDescriptionKey: "Missing serverUrl"])
      completion(nil, error)
      return
    }
    
    let realm = config["realm"] as? String
    let cookieName = config["cookie"] as? String
    let clientId = config["clientId"] as? String
    let discoveryEndpoint = config["discoveryEndpoint"] as? String
    let redirectUri = config["redirectUri"] as? String
    let scopes = config["scopes"] as? [String] ?? ["openid", "email", "profile"]
    
    // Create and configure Journey
    self.journey = Journey.createJourney { journeyConfig in
      journeyConfig.serverUrl = serverUrl
      if let realm = realm { journeyConfig.realm = realm }
      if let cookie = cookieName { journeyConfig.cookie = cookie }
      journeyConfig.timeout = 30
      journeyConfig.logger = LogManager.standard
      
      if let clientId = clientId,
         let discoveryEndpoint = discoveryEndpoint,
         let redirectUri = redirectUri {
        journeyConfig.module(PingJourney.OidcModule.config) { oidcValue in
          oidcValue.clientId = clientId
          oidcValue.discoveryEndpoint = discoveryEndpoint
          oidcValue.redirectUri = redirectUri
          oidcValue.scopes = Set(scopes)
        }
      }
    }
    
    if self.journey != nil {
      print("✅ Journey configured successfully for \(serverUrl)")
      completion(true, nil)
    } else {
      let error = NSError(domain: "NativePingStorageModule",
                          code: 500,
                          userInfo: [NSLocalizedDescriptionKey: "Failed to configure Journey"])
      completion(nil, error)
    }
  }
  
  
  // MARK: - Start Journey
  @objc(start:options:completion:)
  func start(_ journeyName: String,
             options: NSDictionary?,
             completion: @escaping (NSDictionary?, NSError?) -> Void) {
    print("NativePingStorageModule: start called for \(journeyName) with options \(String(describing: options))")
    
    guard let journey = self.journey else {
      let error = NSError(domain: "NativePingStorageModule",
                          code: 500,
                          userInfo: [NSLocalizedDescriptionKey: "Journey not configured"])
      completion(nil, error)
      return
    }
    
    let forceAuth = options?["forceAuth"] as? Bool ?? false
    let noSession = options?["noSession"] as? Bool ?? false
    
    Task {
      let node = await journey.start(journeyName) { opts in
        opts.forceAuth = forceAuth
        opts.noSession = noSession
      }
      self.lastNode = node
      completion(self.serializeNode(node), nil)
    }
  }
  
  
  // MARK: - Next
  @objc(next:input:completion:)
  func next(_ nodeId: NSString, // TODO : Revisit if we need nodeId or keep rely on internal lastNode
            input: NSDictionary,
            completion: @escaping (NSDictionary?, NSError?) -> Void) {
    print("NativePingStorageModule: next called with nodeId \(nodeId) and input \(input)")
    
    guard let current = self.lastNode as? ContinueNode else {
      let error = NSError(domain: "NativePingStorageModule",
                          code: 404,
                          userInfo: [NSLocalizedDescriptionKey: "No active ContinueNode"])
      completion(nil, error)
      return
    }
    
    if let callbacks = input["callbacks"] as? [[String: Any]] {
      for cb in callbacks {
        if let type = cb["type"] as? String {
          switch type {
          case "NameCallback":
            if let nameCB = current.callbacks.first(where: { $0 is NameCallback }) as? NameCallback {
              nameCB.name = cb["value"] as? String ?? ""
            }
          case "PasswordCallback":
            if let passCB = current.callbacks.first(where: { $0 is PasswordCallback }) as? PasswordCallback {
              passCB.password = cb["value"] as? String ?? ""
            }
          default:
            break
          }
        }
      }
    }
    
    Task {
      let nextNode = await current.next()
      self.lastNode = nextNode
      completion(self.serializeNode(nextNode), nil)
    }
  }
  
  
  // MARK: - Resume
  @objc(resume:completion:)
  func resume(_ uri: NSString,
              completion: @escaping (NSDictionary?, NSError?) -> Void) {
    print("NativePingStorageModule: resume called with uri \(uri)")
    
    guard let journey = self.journey else {
      let error = NSError(
        domain: "NativePingStorageModule",
        code: 400,
        userInfo: [NSLocalizedDescriptionKey: "Journey not configured"]
      )
      completion(nil, error)
      return
    }
    
    // Convert string → URL
    guard let resumeUrl = URL(string: uri as String) else {
      let error = NSError(
        domain: "NativePingStorageModule",
        code: 422,
        userInfo: [NSLocalizedDescriptionKey: "Invalid resume URI format"]
      )
      completion(nil, error)
      return
    }
    
    // Execute the resume call
    Task {
      let resumedNode = await journey.resume(resumeUrl)
      self.lastNode = resumedNode
      completion(self.serializeNode(resumedNode), nil)
    }
  }
  
  
  // MARK: - Get Session
  @objc(getSession:)
  func getSession(_ completion: @escaping (NSDictionary?, NSError?) -> Void) {
    print("NativePingStorageModule: getSession called")
    
    guard let journey = self.journey else {
      let error = NSError(
        domain: "NativePingStorageModule",
        code: 400,
        userInfo: [NSLocalizedDescriptionKey: "Journey not configured"]
      )
      completion(nil, error)
      return
    }
    
    Task {
      let user = await journey.journeyUser()
      
      // Fetch OIDC user info (Result<UserInfo, OidcError>)
      let userInfoResult = await user?.userinfo(cache: false)
      var userInfoDict: [String: Any] = [:]
      
      if let result = userInfoResult {
        switch result {
        case .success(let info):
          // Convert Swift dictionary to [String: Any]
          userInfoDict = info.mapValues { $0 }
          print("✅ UserInfo received: \(userInfoDict)")
        case .failure(let error):
          print("⚠️ UserInfo fetch failed: \(error.localizedDescription)")
        }
      }
      
      // Fetch token
      let tokenResult = await user?.token()
      
      switch tokenResult {
      case .success(let token):
        var dict: [String: Any] = [
          "accessToken": token.accessToken,
          "refreshToken": token.refreshToken ?? "",
          "expiresIn": token.expiresIn
        ]
        
        if !userInfoDict.isEmpty {
          dict["userInfo"] = userInfoDict
        }
        
        print("✅ Returning session object with user info")
        completion(dict as NSDictionary, nil)
        
      case .failure(let error):
        let nsError = NSError(
          domain: "NativePingStorageModule",
          code: 401,
          userInfo: [NSLocalizedDescriptionKey: error.localizedDescription]
        )
        completion(nil, nsError)
        
      case .none:
        print("⚠️ No token available")
        completion(nil, nil)
      }
    }
  }
  
  
  // MARK: - Logout
  @objc(logout:)
  func logout(_ completion: @escaping (NSNumber?, NSError?) -> Void) {
    print("NativePingStorageModule: logout called")
    
    guard let journey = self.journey else {
      completion(false, NSError(domain: "NativePingStorageModule",
                                code: 400,
                                userInfo: [NSLocalizedDescriptionKey: "Journey not configured"]))
      return
    }
    
    Task {
      let user = await journey.journeyUser()
      await user?.logout()
      completion(true, nil)
    }
  }
  
}

