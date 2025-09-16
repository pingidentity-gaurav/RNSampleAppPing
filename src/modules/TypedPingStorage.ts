import { StorageConfig } from "../specs/NativePingStorage";
import Bridge from '../specs/NativePingStorage'

export class TypedStorage<T extends object> {
  async configure(config: StorageConfig) {
    return Bridge.configure(config);
  }

  async save(item: T): Promise<boolean> {
    return Bridge.save(item as unknown as Object);
  }

  async get(): Promise<T | null> {
    return (await Bridge.get()) as T | null;
  }

  async delete(): Promise<boolean> {
    return Bridge.remove();
  }
}
