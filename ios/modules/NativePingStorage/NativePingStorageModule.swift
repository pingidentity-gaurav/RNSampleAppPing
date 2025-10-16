//
//  NativePingStorageModule.swift
//  RNSampleApp
//
//  Created by Thais Damasceno on 2025-09-18.
//

import Foundation
import PingStorage
import React

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
  @objc(configureJourney:completion:)
  func configureJourney(_ config: NSDictionary,
                        completion: @escaping (NSNumber?, NSError?) -> Void) {
    print("NativePingStorageModule: configureJourney called with \(config)")
    
    
    // TODO: Hook into PingJourney configuration
    completion(true, nil)
  }
  
  @objc(start:options:completion:)
  func start(_ journeyName: NSString,
             options: NSDictionary,
             completion: @escaping (NSDictionary?, NSError?) -> Void) {
    print("NativePingStorageModule: start called for journey \(journeyName)")
    let node: [String: Any] = [
      "id": UUID().uuidString,
      "type": "ContinueNode",
      "message": "Mock journey started"
    ]
    completion(node as NSDictionary, nil)
  }
  
  @objc(next:input:completion:)
  func next(_ nodeId: NSString,
            input: NSDictionary,
            completion: @escaping (NSDictionary?, NSError?) -> Void) {
    print("NativePingStorageModule: next called with nodeId \(nodeId)")
    let node: [String: Any] = [
      "id": UUID().uuidString,
      "type": "SuccessNode",
      "message": "Journey complete"
    ]
    completion(node as NSDictionary, nil)
  }
  
  @objc(resume:completion:)
  func resume(_ uri: NSString,
              completion: @escaping (NSDictionary?, NSError?) -> Void) {
    print("NativePingStorageModule: resume called with uri \(uri)")
    let node: [String: Any] = [
      "id": UUID().uuidString,
      "type": "ContinueNode",
      "message": "Journey resumed"
    ]
    completion(node as NSDictionary, nil)
  }
  
  @objc(getSession:)
  func getSession(_ completion: @escaping (NSDictionary?, NSError?) -> Void) {
    print("NativePingStorageModule: getSession called")
    let session: [String: Any] = [
      "user": "mock_user",
      "token": "fake-token"
    ]
    completion(session as NSDictionary, nil)
  }
  
  @objc(logout:)
  func logout(_ completion: @escaping (NSNumber?, NSError?) -> Void) {
    print("NativePingStorageModule: logout called")
    completion(true, nil)
  }
}

