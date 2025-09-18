//
//  RCTNativePingStorageBridge.swift
//  RNSampleApp
//
//  Created by Copilot on 2025-09-17.
//

import Foundation
import PingStorage

@objc public class RCTNativePingStorageBridge: NSObject {
    private var storage: (any Storage<Data>)?
    
    @objc public override init() {
        super.init()
    }
    
    @objc(configure:resolve:reject:)
    public func configure(_ config: NSDictionary, resolve: @escaping (Bool) -> Void, reject: @escaping (String, String, NSError?) -> Void) {
        guard let type = config["type"] as? String else {
            reject("STORAGE_CONFIG_ERROR", "Missing storage type in configuration", nil)
            return
        }
        
        Task {
            do {
                switch type.lowercased() {
                case "memory":
                    let cacheable = (config["cacheable"] as? Bool) ?? false
                    storage = MemoryStorage<Data>(cacheable: cacheable)
                    
                case "keychain":
                    guard let keyAlias = config["keyAlias"] as? String else {
                        reject("STORAGE_CONFIG_ERROR", "keyAlias is required for keychain storage", nil)
                        return
                    }
                    storage = KeychainStorage<Data>(account: keyAlias)
                    
                default:
                    reject("STORAGE_CONFIG_ERROR", "Unsupported storage type: \(type). Supported types: memory, keychain", nil)
                    return
                }
                
                resolve(true)
            } catch {
                reject("STORAGE_CONFIG_ERROR", "Failed to configure storage: \(error.localizedDescription)", error as NSError)
            }
        }
    }
    
    private func ensureStorageConfigured(rejecter: @escaping (String, String, NSError?) -> Void) -> Bool {
      if #available(iOS 16.0.0, *) {
        guard storage != nil else {
          return false
        }
      } else {
        // Fallback on earlier versions
      }
        return true
    }
    
    @objc(save:resolve:reject:)
    public func save(_ item: NSDictionary, resolve: @escaping (Bool) -> Void, reject: @escaping (String, String, NSError?) -> Void) {
        NSLog("RCTNativePingStorageBridge: save called with item: %@", item)
        guard ensureStorageConfigured(rejecter: reject) else { return }

        Task {
            do {
                let jsonData = try JSONSerialization.data(withJSONObject: item, options: [])
                NSLog("RCTNativePingStorageBridge: saving JSON data of length %d", jsonData.count)
                try await storage!.save(item: jsonData)
                NSLog("RCTNativePingStorageBridge: save completed")
                resolve(true)
            } catch {
                NSLog("RCTNativePingStorageBridge: save failed: %@", error.localizedDescription)
                reject("STORAGE_SAVE_ERROR", "Failed to save item: \(error.localizedDescription)", error as NSError)
            }
        }
    }

    @objc(get:reject:) public func get(_ resolve: @escaping (NSDictionary?) -> Void, reject: @escaping (String, String, NSError?) -> Void) {
        guard ensureStorageConfigured(rejecter: reject) else { return }
        
        Task {
            do {
                guard let data = try await storage!.get() else {
                    resolve(nil)
                    return
                }
                
                let jsonObject = try JSONSerialization.jsonObject(with: data, options: [])
                guard let dictionary = jsonObject as? NSDictionary else {
                    reject("STORAGE_GET_ERROR", "Stored data is not a valid dictionary", nil)
                    return
                }
                
                resolve(dictionary)
            } catch {
                reject("STORAGE_GET_ERROR", "Failed to get item: \(error.localizedDescription)", error as NSError)
            }
        }
    }

    @objc(remove:reject:) public func remove(_ resolve: @escaping (Bool) -> Void, reject: @escaping (String, String, NSError?) -> Void) {
        guard ensureStorageConfigured(rejecter: reject) else { return }
        
        Task {
            do {
                try await storage!.delete()
                resolve(true)
            } catch {
                reject("STORAGE_REMOVE_ERROR", "Failed to remove item: \(error.localizedDescription)", error as NSError)
            }
        }
    }
}
