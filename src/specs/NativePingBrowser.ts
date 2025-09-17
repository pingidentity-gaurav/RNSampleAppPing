import { TurboModule, TurboModuleRegistry } from "react-native";

export interface Spec extends TurboModule {
  /**
   * Launch a browser session for authentication.
   * @param url The URL to open.
   * @param redirectUri Optional redirect URI (default is manifest placeholder).
   * @returns Promise resolving to redirect URI string.
   */
  launch(url: string, redirectUri?: string): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>("NativePingBrowser");
