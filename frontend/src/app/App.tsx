import { Navigate, NavLink, Outlet, Route, Routes, useLocation } from 'react-router';
import { useState } from 'react';
import { signOut } from '../api/taskflow.ts';
import { errorMessage, landing, roleNames, useSession } from './session.tsx';
import { Login } from '../features/Login.tsx';
import { UserList } from '../features/UserList.tsx';
import { UserEditor } from '../features/UserEditor.tsx';
import styles from './App.module.css';
import { ThemeControl } from './theme.tsx';
import { Brand, Notice, Icon } from './ui.tsx';

export function App() {
  const session = useSession();
  if (session.loading) return <main className={styles.boot} aria-busy="true"><ThemeControl /><Brand /><p role="status">Открываем рабочее пространство…</p></main>;
  if (session.error) return <main className={styles.boot}><ThemeControl /><Brand /><Notice>{session.error}</Notice><button onClick={session.retry}>Повторить подключение</button></main>;
  return <Routes>
    <Route path="/login" element={<Login />} />
    <Route element={<Workspace />}>
      <Route index element={session.user ? <Navigate replace to={landing(session.user)} /> : null} />
      <Route path="account" element={<Account />} />
      <Route element={<Administrator />}>
        <Route path="admin/users" element={<UserList />} />
        <Route path="admin/users/new" element={<UserEditor key="new" />} />
        <Route path="admin/users/:id" element={<UserEditor />} />
      </Route>
      <Route path="*" element={<section><p className={styles.eyebrow}>404 / СТРАНИЦА НЕ НАЙДЕНА</p><h1>Здесь пока ничего нет</h1><NavLink to="/">В рабочее пространство</NavLink></section>} />
    </Route>
  </Routes>;
}
function Workspace() {
  const { user, setUser } = useSession();
  const [leaving, setLeaving] = useState(false);
  const [error, setError] = useState('');
  const location = useLocation();
  if (!user) return <Navigate replace to="/login" />;
  async function logout() {
    setLeaving(true); setError('');
    try { await signOut(); setUser(null); } catch (failure) { setError(errorMessage(failure)); }
    finally { setLeaving(false); }
  }
  return <div className={styles.workspace}>
    <a href="#workspace-content" className={styles.skip}>К содержимому</a>
    <aside className={styles.sidebar}>
      <NavLink to="/" className={styles.brandLink} aria-label="TaskFlow — главная"><Brand /></NavLink>
      <p className={styles.navLabel}>Рабочее пространство</p>
      <nav aria-label="Основная навигация">
        {user.roles.includes('ADMIN') && <NavLink className={({ isActive }) => isActive ? styles.selected : styles.navItem} to="/admin/users"><Icon name="users" />Пользователи</NavLink>}
        <NavLink className={({ isActive }) => isActive ? styles.selected : styles.navItem} to="/account"><Icon name="account" />Моя учётная запись</NavLink>
      </nav>
      <div className={styles.sidebarSpacer} />
      <div className={styles.profile}><span className={styles.avatar} aria-hidden="true">{user.name.slice(0, 1).toUpperCase()}</span><div><strong>{user.name}</strong><small>{user.roles.map(role => roleNames[role]).join(', ')}</small></div></div>
      <button className={styles.logout} disabled={leaving} onClick={() => void logout()}><Icon name="logout" />{leaving ? 'Выход…' : 'Выйти'}</button>
    </aside>
    <div className={styles.main}>
      <header className={styles.topbar}><span>Команда / {location.pathname.startsWith('/admin/users') ? 'Пользователи' : 'Учётная запись'}</span><ThemeControl /></header>
      <main id="workspace-content" className={styles.content} tabIndex={-1}>{error && <Notice>{error}</Notice>}<Outlet /></main>
      <footer className={styles.footer}><span>TaskFlow</span><span>Рабочее пространство команды</span></footer>
    </div>
  </div>;
}
function Administrator() {
  const { user } = useSession();
  return user?.roles.includes('ADMIN') ? <Outlet /> : <section><p className={styles.eyebrow}>ДОСТУП ОГРАНИЧЕН</p><h1>Этот раздел для администратора</h1><p>Текущая учётная запись не имеет доступа к управлению пользователями.</p></section>;
}
function Account() {
  const { user } = useSession();
  if (!user) return null;
  return <section><p className={styles.eyebrow}>Профиль</p><h1>Моя учётная запись</h1><p className={styles.lead}>{user.name}</p><dl className={styles.account}><div><dt>Email</dt><dd>{user.email}</dd></div><div><dt>Роли в команде</dt><dd>{user.roles.map(role => roleNames[role]).join(' · ')}</dd></div></dl></section>;
}
