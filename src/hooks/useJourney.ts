import { useCallback, useState } from 'react';
import NativePingStorage from '../specs/NativePingStorage';

/**
 * useJourney
 * A React hook that provides a declarative API to configure,
 * start, continue, resume, and manage the user session
 * for the Ping Journey SDK.
 */
export function useJourney(journeyConfig: Record<string, any>) {
  const [node, setNode] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  // Configure + Start a Journey
  const start = useCallback(async (journeyName: string) => {
    try {
      setLoading(true);
      setError(null);

      await NativePingStorage.configureJourney(journeyConfig);
      const result = await NativePingStorage.start(journeyName, {
        forceAuth: false,
        noSession: false,
      });

      setNode(result);
      return result;
    } catch (err: any) {
      console.error('❌ useJourney.start error:', err);
      setError(err);
      throw err;
    } finally {
      setLoading(false);
    }
  }, [journeyConfig]);

  // Continue a Journey (submit callbacks)
  const next = useCallback(async (input: Record<string, any>) => {
    if (!node?.id) return;
    try {
      setLoading(true);
      const nextNode = await NativePingStorage.next(node.id, input);
      setNode(nextNode);
      return nextNode;
    } catch (err: any) {
      setError(err);
      throw err;
    } finally {
      setLoading(false);
    }
  }, [node]);

  // Resume a suspended Journey
  const resume = useCallback(async (uri: string) => {
    try {
      setLoading(true);
      const resumedNode = await NativePingStorage.resume(uri);
      setNode(resumedNode);
      return resumedNode;
    } catch (err: any) {
      setError(err);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  // Retrieve current user/session info
  const user = useCallback(async () => {
    try {
      const userT = await NativePingStorage.getSession();
      return userT;
    } catch (err: any) {
      console.error('⚠️ useJourney.user error:', err);
      return null;
    }
  }, []);

  const logout = useCallback(async () => {
    try {
     await NativePingStorage.logout();
     setNode(null);
     }  catch (err) {
         console.error('⚠️ logout failed:', err);
      }
    }, []);

  return [node, { start, next, resume, user, logout, loading, error }] as const;
}
