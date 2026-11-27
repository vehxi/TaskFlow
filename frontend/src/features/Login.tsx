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
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (user) return <Navigate replace to={landing(user)} />;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      setUser(await signIn(email, password));
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
      setPassword('');
      setShowPassword(false);
    }
  }

  return <main className={styles.login}>
    <header className={styles.loginHeader}><Brand /><ThemeControl /></header>
    <aside className={styles.loginStory}>
      <div>
        <p className={styles.eyebrow}>Пространство для команды</p>
        <p className={styles.loginTagline}>Меньше шума.<br />Больше дела.</p>
        <p className={styles.loginStoryText}>Люди, задачи и общий результат.<br />Соберите работу команды в одном месте.</p>
      </div>
      <span className={styles.storyFooter}>TaskFlow / Пространство для команды</span>
    </aside>
    <section className={styles.loginPanel} aria-labelledby="login-title">
      <div className={styles.loginForm}>
        <h1 id="login-title">Вход в TaskFlow</h1>
        {error && <Notice>{error}</Notice>}
        <form onSubmit={event => void submit(event)} aria-busy={busy}>
          <fieldset disabled={busy} className={styles.fields}>
            <label>Email
              <input type="email" name="email" value={email} onChange={event => setEmail(event.target.value)}
                required maxLength={254} autoComplete="username" autoCapitalize="none" spellCheck={false} />
            </label>
            <div className={styles.passwordField}>
              <label htmlFor="login-password">Пароль</label>
              <div className={styles.passwordInput}>
                <input id="login-password" type={showPassword ? 'text' : 'password'} name="password"
                  value={password} onChange={event => setPassword(event.target.value)}
                  required autoComplete="current-password" autoCapitalize="none" spellCheck={false} />
                <button type="button" className={styles.passwordToggle}
                  aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'} aria-controls="login-password"
                  onClick={() => setShowPassword(value => !value)}>
                  <Icon name={showPassword ? 'eyeOff' : 'eye'} />
                </button>
              </div>
            </div>
            <button type="submit" className={styles.primary}>{busy ? 'Входим…' : 'Войти'}<Icon name="arrow" /></button>
          </fieldset>
        </form>
        <p className={styles.hint}>Нет аккаунта? Обратитесь к администратору.</p>
      </div>
    </section>
  </main>;
}
