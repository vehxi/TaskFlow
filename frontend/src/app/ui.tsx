import type { ReactNode } from 'react';
import styles from './App.module.css';

type IconName = 'users' | 'projects' | 'account' | 'logout' | 'plus' | 'arrow' | 'sun' | 'moon' | 'monitor';
export function Icon({ name }: { name: IconName }) {
  const paths: Record<IconName, ReactNode> = {
    users: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /><circle cx="9" cy="7" r="4" /></>,
    projects: <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" />,
    account: <><circle cx="12" cy="12" r="9" /><circle cx="12" cy="9" r="3" /><path d="M6 19a6 6 0 0 1 12 0" /></>,
    logout: <><path d="M9 4H4v16h5m5-12 4 4-4 4m-6-4h12" /></>,
    plus: <path d="M12 5v14M5 12h14" />,
    arrow: <path d="M5 12h14m-6-6 6 6-6 6" />,
    sun: <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" /></>,
    moon: <path d="M20 14a8.5 8.5 0 0 1-10-10A8.5 8.5 0 1 0 20 14Z" />,
    monitor: <><rect x="3" y="4" width="18" height="13" rx="2" /><path d="M12 17v4m-4 0h8" /></>,
  };
  return <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}
export function Brand() {
  return <span className={styles.brand}><span className={styles.brandMark} aria-hidden="true"><svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M3 4h18v4H3zm0 6h13v4H3zm0 6h7v4H3z" /></svg></span>TaskFlow</span>;
}
export function Notice({ children }: { children: ReactNode }) {
  return <div className={styles.error} role="alert"><strong>Не удалось выполнить действие</strong><div>{children}</div></div>;
}
