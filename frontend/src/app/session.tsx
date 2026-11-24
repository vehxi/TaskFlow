import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError } from '../api/client.ts';
import { getCurrentUser } from '../api/taskflow.ts';
import type { CurrentUser } from '../api/types.ts';

interface Session {
  user: CurrentUser | null;
  loading: boolean;
  error: string;
  setUser: (user: CurrentUser | null) => void;
  retry: () => void;
}
const Context = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    getCurrentUser().then(account => { if (active) setUser(account); })
      .catch((failure: unknown) => {
        if (!active) return;
        setUser(null);
        if (!(failure instanceof ApiError && failure.status === 401)) setError(errorMessage(failure));
      }).finally(() => { if (active) setLoading(false); });
    const expired = () => setUser(null);
    const permissionsChanged = () => {
      getCurrentUser().then(account => { if (active) setUser(account); }).catch(() => {});
    };
    window.addEventListener('taskflow:session-expired', expired);
    window.addEventListener('taskflow:permissions-changed', permissionsChanged);
    return () => {
      active = false;
      window.removeEventListener('taskflow:session-expired', expired);
      window.removeEventListener('taskflow:permissions-changed', permissionsChanged);
    };
  }, [attempt]);
  return <Context.Provider value={{ user, loading, error, setUser, retry: () => setAttempt(value => value + 1) }}>{children}</Context.Provider>;
}

export function useSession() {
  const value = useContext(Context);
  if (!value) throw new Error('SessionProvider is required.');
  return value;
}
export const canViewProjects = (user: CurrentUser) => user.roles.includes('MANAGER') || user.roles.includes('EXECUTOR');
export const landing = (user: CurrentUser) => user.roles.includes('ADMIN') ? '/admin/users'
  : canViewProjects(user) ? '/projects' : '/account';
export const errorMessage = (error: unknown) => error instanceof Error ? error.message : 'Не удалось выполнить действие.';
export const roleNames = { ADMIN: 'Администратор', MANAGER: 'Руководитель', EXECUTOR: 'Исполнитель' } as const;
