//
//  NativePingStorageSpec.h
//  RNSampleApp
//
//  TurboModule interface for NativePingStorage
//

#import <React/RCTBridgeModule.h>

NS_ASSUME_NONNULL_BEGIN

@protocol NativePingStorageSpec <RCTBridgeModule>

// Matches JS spec: configure(config: StorageConfig): Promise<boolean>
- (void)configure:(NSDictionary *)config
         resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject;

// save(item: Object): Promise<boolean>
- (void)save:(NSDictionary *)item
     resolve:(RCTPromiseResolveBlock)resolve
     reject:(RCTPromiseRejectBlock)reject;

// get(): Promise<Object | null>
- (void)get:(RCTPromiseResolveBlock)resolve
    reject:(RCTPromiseRejectBlock)reject;

// remove(): Promise<boolean>
- (void)remove:(RCTPromiseResolveBlock)resolve
       reject:(RCTPromiseRejectBlock)reject;

@end

NS_ASSUME_NONNULL_END
