import { useState } from 'react';
import type { FormEvent } from 'react';
import { Navigate } from 'react-router';
import { signIn } from '../api/taskflow.ts';
import { ThemeControl } from '../app/theme.tsx';
import { Brand, Notice, Icon } from '../app/ui.tsx';
import { errorMessage, landing, useSession } from '../app/session.tsx';
import styles from '../app/App.module.css';

export function Login() {
  const { user, setUser } = useSession();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (user) return <Navigate replace to={landing(user)} />;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError('');
    try { setUser(await signIn(email, password)); }
    catch (failure) { setError(errorMessage(failure)); }
    finally { setBusy(false); setPassword(''); }
  }
  return <main className={styles.login}>
    <section className={styles.loginStory}><Brand /><div><p className={styles.eyebrow}>Пространство для команды</p><h1>Меньше шума.<br />Больше дела.</h1><p>Люди, задачи и общий результат.<br />Соберите работу команды в одном месте.</p></div><span className={styles.storyFooter}>TaskFlow / Пространство для команды</span></section>
    <section className={styles.loginPanel} aria-labelledby="login-title"><div className={styles.loginTheme}><ThemeControl /></div><div className={styles.loginForm}><p className={styles.eyebrow}>Добро пожаловать</p><h2 id="login-title">Вход в TaskFlow</h2><p className={styles.lead}>Войдите в рабочее пространство команды.</p>{error && <Notice>{error}</Notice>}
      <form onSubmit={event => void submit(event)}><fieldset disabled={busy} className={styles.fields}>
        <label>Email<input type="email" name="email" value={email} onChange={event => setEmail(event.target.value)} required maxLength={254} autoComplete="username" autoFocus /></label>
        <label>Пароль<input type="password" name="password" value={password} onChange={event => setPassword(event.target.value)} required autoComplete="current-password" /></label>
        <button type="submit" className={styles.primary}>{busy ? 'Входим…' : 'Войти в рабочее пространство'}<Icon name="arrow" /></button>
      </fieldset></form><p className={styles.hint}>Нет учётной записи? Обратитесь к администратору команды.</p>
    </div></section>
  </main>;
}
