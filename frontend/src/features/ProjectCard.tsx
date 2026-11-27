import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client.ts';
import { createProject, getProject, updateProject } from '../api/taskflow.ts';
import type { ProjectDetails, ProjectSummary } from '../api/types.ts';
import { errorMessage, useSession } from '../app/session.tsx';
import { Notice } from '../app/ui.tsx';
import styles from '../app/App.module.css';
import projectStyles from './Projects.module.css';
import { MembersPanel } from './ProjectMembers.tsx';
import taskStyles from './Tasks.module.css';

export function NewProject() {
  const { user } = useSession();
  const navigate = useNavigate();
  if (!user?.roles.includes('MANAGER')) {
    return <section><BackToProjects /><h1>Создать проект может руководитель</h1></section>;
  }

  return <section className={styles.editor}>
    <BackToProjects />
    <h1>Новый проект</h1>
    <ProjectForm submit={async details => {
      const { data } = await createProject(details);
      navigate(`/projects/${data.id}`, { replace: true });
    }} cancel={<Link to="/projects" className={styles.secondary}>Отмена</Link>} />
  </section>;
}

export function ProjectCard() {
  const { id } = useParams();
  return id ? <Card key={id} id={id} /> : null;
}

function Card({ id }: { id: string }) {
  const { user } = useSession();
  const [project, setProject] = useState<ProjectSummary | null>(null);
  const [error, setError] = useState('');
  const [status, setStatus] = useState('');
  const [editing, setEditing] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getProject(id, controller.signal).then(data => {
      if (!controller.signal.aborted) setProject(data);
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) setError(errorMessage(failure));
    });
    return () => controller.abort();
  }, [id, attempt]);

  function reload() {
    setEditing(false);
    setProject(null);
    setStatus('');
    setError('');
    setAttempt(current => current + 1);
  }

  const canManage = project?.managerId === user?.id && user?.roles.includes('MANAGER');
  return <section className={styles.editor}>
    <BackToProjects />
    {error ? <><Notice>{error}</Notice><button onClick={reload}>Повторить загрузку</button></>
      : !project ? <p role="status">Загружаем проект…</p>
      : <>
        <div className={projectStyles.cardHeading}>
          <h1>{project.name}</h1>
          {canManage && !editing && <button className={styles.secondary} onClick={() => {
            setStatus('');
            setEditing(true);
          }}>Изменить проект</button>}
        </div>
        {status && <p className={styles.success} role="status">{status}</p>}
        {canManage && editing ? <ProjectForm project={project} reload={reload} submit={async details => {
          const { data } = await updateProject(project.id, details, project.version);
          setProject(data);
          setEditing(false);
          setStatus('Проект сохранён.');
        }} cancel={<button type="button" className={styles.secondary} onClick={() => setEditing(false)}>Отмена</button>} />
          : <>
            {project.description && <p className={projectStyles.description}>{project.description}</p>}
            <div className={taskStyles.projectLinks}>
              <Link className={styles.secondary} to={`/tasks?projectId=${project.id}`}>Задачи проекта</Link>
              {canManage && <Link className={styles.primary} to={`/projects/${project.id}/tasks/new`}>Новая задача</Link>}
            </div>
            {canManage && <MembersPanel project={project} reloadProject={reload}
              onSaved={(version, message) => {
                setProject({ ...project, version });
                setStatus(message);
              }} />}
          </>}
      </>}
  </section>;
}

function BackToProjects() {
  return <Link className={styles.backLink} to="/projects">← Проекты</Link>;
}

function ProjectForm({ project, submit, cancel, reload }: {
  project?: ProjectSummary; submit: (details: ProjectDetails) => Promise<void>;
  cancel: React.ReactNode; reload?: () => void;
}) {
  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string[]>>({});
  const [stale, setStale] = useState(false);
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving.current || stale) return;
    const data = new FormData(event.currentTarget);
    saving.current = true;
    setBusy(true);
    setError('');
    setFields({});
    try {
      await submit({ name: String(data.get('name')), description: String(data.get('description')) });
    } catch (failure) {
      setError(errorMessage(failure));
      if (failure instanceof ApiError) {
        setFields(failure.fieldErrors);
        setStale(failure.code === 'STALE_VERSION');
      }
    } finally {
      saving.current = false;
      setBusy(false);
    }
  }

  return <form onSubmit={event => void save(event)}>
    {error && <Notice>{error}
      {stale && reload && <button type="button" className={styles.inlineButton} onClick={reload}>Обновить карточку</button>}
    </Notice>}
    <fieldset className={styles.formBody} disabled={busy}>
      <div className={styles.formSection}>
        <h2>Описание проекта</h2>
        <div className={styles.fields}>
          <label>Название
            <input name="name" defaultValue={project?.name} required maxLength={200}
              aria-invalid={Boolean(fields.name)} aria-describedby={fields.name ? 'project-name-error' : undefined} />
            {fields.name && <span className={styles.fieldError} id="project-name-error">{fields.name.join(' ')}</span>}
          </label>
          <label>Описание
            <textarea name="description" defaultValue={project?.description}
              rows={5} maxLength={10000} aria-invalid={Boolean(fields.description)}
              aria-describedby={fields.description ? 'project-description-error' : undefined} />
            {fields.description && <span className={styles.fieldError} id="project-description-error">{fields.description.join(' ')}</span>}
          </label>
        </div>
      </div>
      <div className={styles.formActions}>
        {cancel}
        <button className={styles.primary} disabled={stale}>
          {busy ? 'Сохраняем…' : project ? 'Сохранить проект' : 'Создать проект'}
        </button>
      </div>
    </fieldset>
  </form>;
}
