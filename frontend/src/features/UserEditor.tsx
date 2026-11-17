import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client.ts';
import { createUser, getCurrentUser, getUser, updateUser } from '../api/taskflow.ts';
import type { RoleCode, UserAccount } from '../api/types.ts';
import { Notice } from '../app/ui.tsx';
import { errorMessage, roleNames, useSession } from '../app/session.tsx';
import styles from '../app/App.module.css';

const allRoles: RoleCode[] = ['ADMIN', 'MANAGER', 'EXECUTOR'];
const roleHelp = { ADMIN: 'Управление пользователями и справочниками', MANAGER: 'Управление своими проектами и задачами', EXECUTOR: 'Работа с назначенными задачами' };

export function UserEditor() {
  const { id } = useParams();
  // Remount on identity changes so a response from another card cannot populate this form.
  return <Editor key={id ?? 'new'} id={id} />;
}
function Editor({ id }: { id?: string }) {
  const navigate = useNavigate();
  const session = useSession();
  const [account, setAccount] = useState<UserAccount | null>(null);
  const [loading, setLoading] = useState(Boolean(id));
  const [loadError, setLoadError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [roles, setRoles] = useState<RoleCode[]>([]);
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string[]>>({});
  const [notice, setNotice] = useState('');
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController(); setLoading(true); setLoadError('');
    getUser(id, controller.signal).then(user => {
      setAccount(user); setName(user.name); setEmail(user.email); setRoles(user.roles); setActive(user.active);
      setError(''); setFields({});
    }).catch((failure: unknown) => { if (!controller.signal.aborted) setLoadError(errorMessage(failure)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [id, attempt]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (sending.current) return;
    if (roles.length === 0) { setFields({ roles: ['Выберите хотя бы одну роль.'] }); return; }
    if (!id && new TextEncoder().encode(password).length > 72) {
      setFields({ initialPassword: ['Пароль должен занимать не более 72 байт UTF-8.'] }); return;
    }
    sending.current = true; setBusy(true); setError(''); setFields({}); setNotice('');
    try {
      const result = id && account
        ? await updateUser(id, { name, email, roles, active, expectedVersion: account.version })
        : await createUser({ name, email, initialPassword: password, roles });
      setAccount(result.data);
      setName(result.data.name); setEmail(result.data.email); setRoles(result.data.roles); setActive(result.data.active);
      setNotice(id ? 'Изменения сохранены.' : 'Пользователь добавлен.');
      if (!id) navigate(`/admin/users/${result.data.id}`, { replace: true });
      if (session.user?.id === result.data.id) {
        if (!result.data.active) {
          // The follow-up request lets Spring invalidate the just-blocked session.
          try { await getCurrentUser(); } catch { /* A blocked account returns 401. */ }
          session.setUser(null);
        }
        else {
          session.setUser(await getCurrentUser());
          if (!result.data.roles.includes('ADMIN')) navigate('/account', { replace: true });
        }
      }
    } catch (failure) {
      setError(errorMessage(failure));
      if (failure instanceof ApiError) setFields(failure.fieldErrors);
    } finally { sending.current = false; setBusy(false); setPassword(''); }
  }
  const fieldError = (field: string) => fields[field]?.join(' ');
  if (loading) return <p role="status">Загружаем карточку…</p>;
  if (loadError) return <><Link to="/admin/users">← К списку пользователей</Link><Notice>{loadError}</Notice><button onClick={() => setAttempt(value => value + 1)}>Повторить загрузку</button></>;
  return <section className={styles.editor}><Link className={styles.backLink} to="/admin/users">← Пользователи</Link><p className={styles.eyebrow}>Команда / {id ? 'Пользователь' : 'Создание'}</p><h1>{id ? 'Настройка доступа' : 'Новый пользователь'}</h1><p className={styles.lead}>{id ? 'Данные учётной записи и роли в рабочем пространстве.' : 'Укажите данные для входа и выберите роли в команде.'}</p>
    {notice && <p role="status" className={styles.success}>{notice}</p>}
    {error && <Notice>{error}{id && <button type="button" className={styles.inlineButton} onClick={() => setAttempt(value => value + 1)}>Обновить карточку</button>}</Notice>}
    <form onSubmit={event => void submit(event)}><fieldset disabled={busy} className={styles.formBody}>
      <section className={styles.formSection}><div><h2>Основные данные</h2><p>Так пользователь будет представлен в команде.</p></div><div className={styles.fields}>
        <label>Имя<input name="name" value={name} onChange={event => setName(event.target.value)} required maxLength={120} autoComplete="name" aria-invalid={Boolean(fieldError('name'))} aria-describedby={fieldError('name') ? 'name-error' : undefined} />{fieldError('name') && <span id="name-error" className={styles.fieldError}>{fieldError('name')}</span>}</label>
        <label>Email<input name="email" type="email" value={email} onChange={event => setEmail(event.target.value)} required maxLength={254} autoComplete="off" aria-invalid={Boolean(fieldError('email'))} aria-describedby={fieldError('email') ? 'email-error' : undefined} />{fieldError('email') && <span id="email-error" className={styles.fieldError}>{fieldError('email')}</span>}</label>
        {!id && <label>Начальный пароль<input type="password" name="initialPassword" value={password} onChange={event => setPassword(event.target.value)} required autoComplete="new-password" aria-invalid={Boolean(fieldError('initialPassword'))} aria-describedby="password-help" /><span id="password-help" className={styles.hint}>{fieldError('initialPassword') || 'Передайте пароль пользователю лично. Не более 72 байт в UTF-8.'}</span></label>}
      </div></section>
      <section className={styles.formSection}><div><h2>Роли в команде</h2><p>Можно совместить несколько ролей.</p></div><fieldset className={styles.roleOptions} aria-describedby={fieldError('roles') ? 'roles-error' : undefined}><legend className={styles.srOnly}>Выбор ролей</legend>{allRoles.map(role => <label className={styles.roleOption} key={role}><input type="checkbox" checked={roles.includes(role)} onChange={event => setRoles(current => event.target.checked ? [...current, role] : current.filter(item => item !== role))} /><span><strong>{roleNames[role]}</strong><small>{roleHelp[role]}</small></span></label>)}{fieldError('roles') && <p id="roles-error" className={styles.fieldError}>{fieldError('roles')}</p>}</fieldset></section>
      {id && <section className={styles.formSection}><div><h2>Доступ</h2><p>Блокировка сохраняет все связанные данные.</p></div><label className={styles.roleOption}><input type="checkbox" checked={active} onChange={event => setActive(event.target.checked)} /><span><strong>Учётная запись активна</strong><small>Пользователь может входить в TaskFlow</small></span></label></section>}
      <div className={styles.formActions}><Link className={styles.secondary} to="/admin/users">Отмена</Link><button type="submit" className={styles.primary}>{busy ? 'Сохраняем…' : id ? 'Сохранить изменения' : 'Добавить пользователя'}<span aria-hidden="true">↗</span></button></div>
    </fieldset></form>
  </section>;
}
