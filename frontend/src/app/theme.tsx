import { createContext, useContext, useEffect, useLayoutEffect, useState } from 'react';
import type { ReactNode } from 'react';
import styles from './App.module.css';
import { Icon } from './ui.tsx';

type Theme = 'light' | 'dark';
const storageKey = 'taskflow.theme';
const themeQuery = '(prefers-color-scheme: dark)';
const Context = createContext<{ current: Theme; toggle: () => void } | null>(null);

function savedTheme(): Theme | null {
  try {
    const saved = localStorage.getItem(storageKey);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch { /* The in-memory theme still works when storage is unavailable. */ }
  return null;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [override, setOverride] = useState(savedTheme);
  const [systemDark, setSystemDark] = useState(() => matchMedia(themeQuery).matches);
  const current = override ?? (systemDark ? 'dark' : 'light');

  useLayoutEffect(() => {
    document.documentElement.dataset.theme = current;
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', current === 'dark' ? '#141414' : '#fafafa');
  }, [current]);

  useEffect(() => {
    const media = matchMedia(themeQuery);
    const onSystemChange = () => setSystemDark(media.matches);
    const onStorageChange = (event: StorageEvent) => {
      if (event.key === storageKey || event.key === null) setOverride(savedTheme());
    };
    onSystemChange();
    media.addEventListener('change', onSystemChange);
    window.addEventListener('storage', onStorageChange);
    return () => {
      media.removeEventListener('change', onSystemChange);
      window.removeEventListener('storage', onStorageChange);
    };
  }, []);

  function toggle() {
    const next = current === 'dark' ? 'light' : 'dark';
    setOverride(next);
    try { localStorage.setItem(storageKey, next); } catch { /* Keep the current session usable. */ }
  }

  return <Context.Provider value={{ current, toggle }}>{children}</Context.Provider>;
}

export function ThemeControl() {
  const theme = useContext(Context);
  if (!theme) return null;
  const isDark = theme.current === 'dark';
  const action = isDark ? 'Включить светлую тему' : 'Включить тёмную тему';
  return <button type="button" className={styles.themeControl} aria-label={action} title={action} onClick={theme.toggle}>
    <Icon name={isDark ? 'sun' : 'moon'} />
  </button>;
}
