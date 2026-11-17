import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import styles from './App.module.css';
import { Icon } from './ui.tsx';

type Preference = 'light' | 'dark' | 'system';
const storageKey = 'taskflow.theme';
const Context = createContext<{ preference: Preference; choose: (value: Preference) => void } | null>(null);
function savedPreference(): Preference {
  try {
    const saved = localStorage.getItem(storageKey);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch { /* A blocked browser store still permits an in-memory preference. */ }
  return 'system';
}
function apply(preference: Preference) {
  const dark = preference === 'dark' || (preference === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#141414' : '#fafafa');
}
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreference] = useState<Preference>(savedPreference);
  useEffect(() => {
    const media = matchMedia('(prefers-color-scheme: dark)');
    const change = () => apply(preference);
    change(); media.addEventListener('change', change);
    return () => media.removeEventListener('change', change);
  }, [preference]);
  useEffect(() => {
    const sync = (event: StorageEvent) => {
      if (event.key === storageKey || event.key === null) setPreference(savedPreference());
    };
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, []);
  function choose(value: Preference) {
    apply(value); setPreference(value);
    try { localStorage.setItem(storageKey, value); } catch { /* Keep the current session usable. */ }
  }
  return <Context.Provider value={{ preference, choose }}>{children}</Context.Provider>;
}
export function ThemeControl() {
  const theme = useContext(Context);
  if (!theme) return null;
  return <label className={styles.themeControl}>
    <Icon name={theme.preference === 'system' ? 'monitor' : theme.preference === 'dark' ? 'moon' : 'sun'} />
    <span className={styles.srOnly}>Тема оформления</span>
    <select value={theme.preference} onChange={event => theme.choose(event.target.value as Preference)}>
      <option value="system">Как в системе</option><option value="light">Светлая тема</option><option value="dark">Тёмная тема</option>
    </select>
  </label>;
}
