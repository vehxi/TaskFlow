import { useEffect, useState } from 'react';
import { errorMessage } from '../app/session.tsx';

export function useTaskResource<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [result, setResult] = useState<{ load: typeof load; data: T | null; error: string } | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).then(data => {
      if (!controller.signal.aborted) setResult({ load, data, error: '' });
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) setResult({ load, data: null, error: errorMessage(failure) });
    });
    return () => controller.abort();
  }, [load, attempt]);

  return {
    data: result?.load === load ? result.data : null,
    error: result?.load === load ? result.error : '',
    retry: () => { setResult(null); setAttempt(value => value + 1); },
  };
}
