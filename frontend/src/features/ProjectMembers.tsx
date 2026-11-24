import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../api/client.ts';
import { addMember, getCandidates, getMembers, removeMember } from '../api/taskflow.ts';
import type { Page, ProjectMember, ProjectMembers, ProjectSummary } from '../api/types.ts';
import { errorMessage } from '../app/session.tsx';
import { Icon, Notice } from '../app/ui.tsx';
import styles from '../app/App.module.css';
import projectStyles from './Projects.module.css';
import { ProjectPagination } from './ProjectList.tsx';

export function MembersPanel({ project, onSaved, reloadProject }: {
  project: ProjectSummary; onSaved: (version: number, message: string) => void; reloadProject: () => void;
}) {
  const [page, setPage] = useState(0);
  const [members, setMembers] = useState<ProjectMembers | null>(null);
  const [loadError, setLoadError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [stale, setStale] = useState(false);
  const pending = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    setMembers(null);
    setLoadError('');
    getMembers(project.id, page, controller.signal).then(data => {
      if (!controller.signal.aborted) setMembers(data);
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) setLoadError(errorMessage(failure));
    });
    return () => controller.abort();
  }, [project.id, project.version, page, attempt]);

  async function changeMember(userId: number, add: boolean) {
    if (pending.current || stale) return;
    pending.current = true;
    setBusy(true);
    setError('');
    try {
      const result = add
        ? await addMember(project.id, userId, project.version)
        : await removeMember(project.id, userId, project.version);
      setPage(0);
      setAdding(false);
      onSaved(result.data.projectVersion, add ? 'Участник добавлен.' : 'Участник исключён.');
    } catch (failure) {
      setError(errorMessage(failure));
      setStale(failure instanceof ApiError && failure.code === 'STALE_VERSION');
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  const changed = stale || (members !== null && members.projectVersion !== project.version);
  return <section className={projectStyles.members} aria-labelledby="members-title">
    <div className={styles.sectionBar}>
      <h2 id="members-title">Участники {members && <span className={styles.count}>{members.totalElements}</span>}</h2>
      {!adding && <button type="button" className={styles.secondary} disabled={busy || changed} onClick={() => {
        setError('');
        setAdding(true);
      }}><Icon name="plus" />Добавить участника</button>}
    </div>
    {(error || changed) && <Notice>{error || 'Состав проекта изменился.'}
      {changed && <button type="button" className={styles.inlineButton} onClick={reloadProject}>Обновить карточку</button>}
    </Notice>}
    {adding && <MemberPicker projectId={project.id} disabled={busy || changed}
      onAdd={userId => void changeMember(userId, true)}
      onCancel={() => { setAdding(false); setError(''); }} />}
    {loadError ? <><Notice>{loadError}</Notice><button type="button" onClick={() => setAttempt(value => value + 1)}>Повторить загрузку</button></>
      : !members ? <p className={projectStyles.memberEmpty} role="status">Загружаем участников…</p>
      : members.items.length === 0 ? <p className={projectStyles.memberEmpty}>{page ? 'На этой странице нет участников.' : 'В проекте пока нет участников.'}</p>
      : <ul className={projectStyles.memberList}>
        {members.items.map(member => <li key={member.id}>
          <div className={styles.person}>
            <span className={styles.personAvatar} aria-hidden="true">{member.name.slice(0, 1).toUpperCase()}</span>
            <strong>{member.name}</strong>
          </div>
          <button type="button" className={projectStyles.remove} disabled={busy || changed}
            aria-label={`Исключить участника: ${member.name}`} onClick={() => void changeMember(member.id, false)}>
            Исключить
          </button>
        </li>)}
      </ul>}
    {members && <ProjectPagination page={page} pages={members.totalPages} label="Страницы участников"
      onChange={setPage} disabled={busy} />}
  </section>;
}

function MemberPicker({ projectId, disabled, onAdd, onCancel }: {
  projectId: number; disabled: boolean; onAdd: (id: number) => void; onCancel: () => void;
}) {
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [candidates, setCandidates] = useState<Page<ProjectMember> | null>(null);
  const [selected, setSelected] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setCandidates(null);
    setSelected('');
    setError('');
    getCandidates(projectId, page, controller.signal).then(data => {
      if (!controller.signal.aborted) setCandidates(data);
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) setError(errorMessage(failure));
    });
    return () => controller.abort();
  }, [projectId, page, attempt]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!disabled && selected) onAdd(Number(selected));
  }

  return <form className={projectStyles.picker} onSubmit={submit}>
    {error ? <><Notice>{error}</Notice><button type="button" onClick={() => setAttempt(value => value + 1)}>Повторить загрузку</button></>
      : !candidates ? <p role="status">Загружаем исполнителей…</p>
      : candidates.items.length === 0 ? <p>{page ? 'На этой странице нет исполнителей.' : 'Нет доступных исполнителей.'}</p>
      : <label>Исполнитель
        <select required value={selected} onChange={event => setSelected(event.target.value)} disabled={disabled}>
          <option value="">Выберите исполнителя</option>
          {candidates.items.map(candidate => <option key={candidate.id} value={candidate.id}>{candidate.name}</option>)}
        </select>
      </label>}
    {candidates && <ProjectPagination page={page} pages={candidates.totalPages} label="Страницы исполнителей"
      onChange={setPage} disabled={disabled} />}
    <div className={projectStyles.pickerActions}>
      <button type="button" className={styles.secondary} onClick={onCancel}>Отмена</button>
      <button className={styles.primary} disabled={disabled || !selected}>Добавить</button>
    </div>
  </form>;
}
