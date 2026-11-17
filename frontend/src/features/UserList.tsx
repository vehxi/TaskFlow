import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { getUsers } from '../api/taskflow.ts';
import type { Page, UserAccount } from '../api/types.ts';
import { Notice, Icon } from '../app/ui.tsx';
import { errorMessage, roleNames } from '../app/session.tsx';
import styles from '../app/App.module.css';

export function UserList() {
  const [params, setParams] = useSearchParams();
  const raw = Number(params.get('page') ?? '0');
  const page = Number.isSafeInteger(raw) && raw >= 0 ? raw : 0;
  const [result, setResult] = useState<Page<UserAccount> | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setResult(null); setError('');
    getUsers(page, controller.signal).then(setResult).catch((failure: unknown) => {
      if (!controller.signal.aborted) setError(errorMessage(failure));
    });
    return () => controller.abort();
  }, [page, attempt]);
  return <section aria-labelledby="users-title"><div className={styles.pageHeading}><div><p className={styles.eyebrow}>Команда</p><h1 id="users-title">Пользователи</h1><p className={styles.lead}>Учётные записи, роли и доступ к рабочему пространству.</p></div><Link className={styles.primary} to="/admin/users/new"><Icon name="plus" />Добавить пользователя</Link></div>
    <div className={styles.sectionBar}><h2>Все пользователи <span className={styles.count}>{result?.totalElements ?? '—'}</span></h2><span>Учётные записи и роли</span></div>
    {error ? <><Notice>{error}</Notice><button onClick={() => setAttempt(value => value + 1)}>Повторить загрузку</button></>
      : !result ? <p className={styles.loading} role="status">Загружаем пользователей…</p>
      : !result.items.length ? <div className={styles.empty}><h3>{page ? 'На этой странице никого нет' : 'Команда начинается с первого человека'}</h3><p>{page ? 'Вернитесь к началу списка.' : 'Добавьте пользователя и выберите его роли.'}</p>{page > 0 && <button onClick={() => setParams({ page: '0' })}>К началу списка</button>}</div>
      : <><div className={styles.tableWrap}><table className={styles.table}><caption className={styles.srOnly}>Учётные записи команды</caption><thead><tr><th scope="col">Пользователь</th><th scope="col">Роли</th><th scope="col">Доступ</th><th scope="col"><span className={styles.srOnly}>Действия</span></th></tr></thead><tbody>{result.items.map(account => <tr key={account.id}><td><div className={styles.person}><span className={styles.personAvatar} aria-hidden="true">{account.name.slice(0, 1).toUpperCase()}</span><div><strong>{account.name}</strong><span>{account.email}</span></div></div></td><td><div className={styles.roles}>{account.roles.map(role => <span className={styles.role} key={role}>{roleNames[role]}</span>)}</div></td><td><span className={account.active ? styles.active : styles.blocked}><span aria-hidden="true">●</span>{account.active ? 'Активен' : 'Заблокирован'}</span></td><td><Link className={styles.editLink} to={`/admin/users/${account.id}`} aria-label={`Изменение пользователя ${account.name}`}>Изменить <Icon name="arrow" /></Link></td></tr>)}</tbody></table></div></>}
    {result && <nav className={styles.pagination} aria-label="Страницы пользователей"><span>{result.totalPages ? `Страница ${page + 1} из ${result.totalPages}` : 'Нет записей'}</span><div><button disabled={page === 0} onClick={() => setParams({ page: String(page - 1) })}>← Назад</button><button disabled={page + 1 >= result.totalPages} onClick={() => setParams({ page: String(page + 1) })}>Далее →</button></div></nav>}
  </section>;
}
