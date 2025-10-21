import { TurboModule, TurboModuleRegistry } from 'react-native';

export type StorageConfig = {
  type?: 'memory' | 'datastore' | 'encrypted';
  fileName?: string;
  keyAlias?: string;
  strongBoxPreferred?: boolean;
  cacheStrategy?: 'NO_CACHE' | 'CACHE_ON_FAILURE' | 'CACHE';
};

export type Node = {
  id: string;
  type: 'ContinueNode' | 'ErrorNode' | 'FailureNode' | 'SuccessNode';
  message?: string;
  cause?: string;
  session?: Object;
  callbacks?: Array<{
    type: string;
    prompt?: string;
    value?: Object;
  }>;
};

export type JourneyConfig = {
  serverUrl: string;
  realm?: string;
  cookie?: string;
  clientId?: string;
  discoveryEndpoint?: string;
  redirectUri?: string;
  scopes?: string[];
};

// Represents user claims from OIDC `userinfo()` endpoint
export type JourneyUserInfo = Record<string, string | number | boolean | null>;

// Represents the combined session + tokens structure returned from Swift
export interface JourneyUserSession {
  accessToken: string;
  refreshToken?: string;
  expiresIn?: number;
  userInfo?: JourneyUserInfo;
}


/**
 * Optional flags when starting a Journey.
 */
export type JourneyOptions = {
  forceAuth?: boolean;
  noSession?: boolean;
};

export interface Spec extends TurboModule {
  configure(config: StorageConfig): Promise<boolean>;
  save(item: Object): Promise<boolean>;
  get(): Promise<Object | null>;
  remove(): Promise<boolean>;

  /**
   * Configure the Journey SDK.
   */
  configureJourney(config: Object): Promise<boolean>;

  /**
   * Start a Journey by name.
   */
  start(journeyName: string, options?: JourneyOptions): Promise<Node>;

  /**
   * Advance to the next node.
   */
  next(nodeId: string, input: Object): Promise<Node>;

  /**
   * Resume a suspended Journey (e.g., magic link).
   */
  resume(uri: string): Promise<Node>;

  /**
   * Get an existing session if available.
   */
  getSession(): Promise<Object | null>;

  /**
   * Logout and clear session.
   */
  logout(): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('NativePingStorage');
