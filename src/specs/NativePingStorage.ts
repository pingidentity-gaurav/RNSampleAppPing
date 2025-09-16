import { TurboModule, TurboModuleRegistry } from 'react-native';

export type StorageConfig = {
  type?: 'memory' | 'datastore' | 'encrypted';
  fileName?: string;
  keyAlias?: string;
  strongBoxPreferred?: boolean;
  cacheStrategy?: 'NO_CACHE' | 'CACHE_ON_FAILURE' | 'CACHE';
};

export interface Spec extends TurboModule {
  configure(config: StorageConfig): Promise<boolean>;
  save(item: Object): Promise<boolean>;
  get(): Promise<Object | null>;
  remove(): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('NativePingStorage');
