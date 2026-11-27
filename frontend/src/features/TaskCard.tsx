import { useCallback, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client.ts';
import { getProject } from '../api/taskflow.ts';
import { createTask, getReferenceData, getTask, getTaskAssignees, updateTask } from '../api/tasks.ts';
import type { TaskDetail, TaskFields } from '../api/types.ts';
import { errorMessage, useSession } from '../app/session.tsx';
import { Notice } from '../app/ui.tsx';
import styles from '../app/App.module.css';
import taskStyles from './Tasks.module.css';
import { displayDueDate, isOpenTask, taskStatusNames } from './taskDisplay.ts';
import { useTaskResource } from './useTaskResource.ts';

export function TaskCard({ editing = false }: { editing?: boolean }) {
  const { id = '' } = useParams();
  const { user } = useSession();
  const load = useCallback((signal: AbortSignal) => getTask(id, signal), [id]);
  const resource = useTaskResource(load);
  const task = resource.data;
  const canEdit = task?.managerId === user?.id && user?.roles.includes('MANAGER')
    && task && isOpenTask(task.status);

  return <section className={styles.editor}>
    <Link className={styles.backLink} to={editing ? `/tasks/${id}` : '/tasks'}>
      {editing ? '← К задаче' : '← Задачи'}
    </Link>
    {resource.error ? <><Notice>{resource.error}</Notice><button onClick={resource.retry}>Повторить загрузку</button></>
      : !task ? <p role="status">Загружаем задачу…</p>
      : editing ? canEdit ? <>
        <h1>Редактирование задачи</h1>
        <TaskEditor key={task.id} projectId={task.projectId} projectName={task.projectName} task={task}
          cancelTo={`/tasks/${task.id}`} reload={resource.retry} />
      </> : <h1>Редактирование недоступно</h1>
      : <>
        <div className={taskStyles.heading}>
          <div>
            <p className={styles.eyebrow}>{task.projectName}</p>
            <h1>{task.title}</h1>
            <span className={styles.role}>{taskStatusNames[task.status]}</span>
          </div>
          {canEdit && <Link className={styles.secondary} to={`/tasks/${task.id}/edit`}>Изменить задачу</Link>}
        </div>
        {task.description && <p className={taskStyles.description}>{task.description}</p>}
        <dl className={styles.account}>
          <div><dt>Исполнитель</dt><dd>{task.assigneeName}</dd></div>
          <div><dt>Срок</dt><dd><time dateTime={task.dueDate}>{displayDueDate(task.dueDate)}</time></dd></div>
          <div><dt>Тип</dt><dd>{task.typeName}</dd></div>
          <div><dt>Приоритет</dt><dd>{task.priorityName}</dd></div>
          <div><dt>Автор</dt><dd>{task.authorName}</dd></div>
        </dl>
      </>}
  </section>;
}

export function NewTask() {
  const { id = '' } = useParams();
  const { user } = useSession();
  const load = useCallback((signal: AbortSignal) => getProject(id, signal), [id]);
  const resource = useTaskResource(load);
  const project = resource.data;

  return <section className={styles.editor}>
    <Link className={styles.backLink} to={`/projects/${id}`}>← К проекту</Link>
    {resource.error ? <><Notice>{resource.error}</Notice><button onClick={resource.retry}>Повторить загрузку</button></>
      : !project ? <p role="status">Загружаем проект…</p>
      : project.managerId !== user?.id || !user.roles.includes('MANAGER') ? <h1>Создание задачи недоступно</h1>
      : <><h1>Новая задача</h1>
        <TaskEditor key={project.id} projectId={project.id} projectName={project.name} cancelTo={`/projects/${id}`} />
      </>}
  </section>;
}

function TaskEditor({ projectId, projectName, task, cancelTo, reload }: {
  projectId: number; projectName: string; task?: TaskDetail; cancelTo: string; reload?: () => void;
}) {
  const navigate = useNavigate();
  const load = useCallback((signal: AbortSignal) => Promise.all([
    getReferenceData(signal), getTaskAssignees(projectId, signal),
  ]), [projectId]);
  const options = useTaskResource(load);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string[]>>({});
  const [busy, setBusy] = useState(false);
  const [stale, setStale] = useState(false);
  const pending = useRef(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current || stale) return;
    const form = new FormData(event.currentTarget);
    const fields: TaskFields = {
      title: String(form.get('title')), description: String(form.get('description')),
      dueDate: String(form.get('dueDate')), assigneeId: Number(form.get('assigneeId')),
      typeId: Number(form.get('typeId')), priorityId: Number(form.get('priorityId')),
    };
    pending.current = true;
    setBusy(true);
    setError('');
    setFieldErrors({});
    try {
      const result = task ? await updateTask(task.id, fields, task.version) : await createTask(projectId, fields);
      navigate(`/tasks/${result.data.id}`, { replace: true });
    } catch (failure) {
      setError(errorMessage(failure));
      if (failure instanceof ApiError) {
        setFieldErrors(failure.fieldErrors);
        setStale(failure.code === 'STALE_VERSION');
      }
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  if (options.error) return <><Notice>{options.error}</Notice><button onClick={options.retry}>Повторить загрузку</button></>;
  if (!options.data) return <p role="status">Загружаем варианты назначения…</p>;
  const [reference, assignees] = options.data;
  const types = reference.types.filter(value => value.active || value.id === task?.typeId);
  const priorities = reference.priorities.filter(value => value.active || value.id === task?.priorityId);
  const canSave = (assignees.length > 0 || task !== undefined) && types.length > 0 && priorities.length > 0;
  const errorId = (name: string) => fieldErrors[name] ? `task-${name}-error` : undefined;
  const fieldError = (name: string) => fieldErrors[name]
    && <span id={errorId(name)} className={styles.fieldError}>{fieldErrors[name].join(' ')}</span>;

  return <>
    <p className={styles.lead}>{projectName}</p>
    {error && <Notice>{error}
      {stale && reload && <button type="button" className={styles.inlineButton} onClick={reload}>Обновить карточку</button>}
    </Notice>}
    <form onSubmit={event => void submit(event)}>
      <fieldset className={styles.formBody} disabled={busy}>
        <div className={styles.formSection}>
          <h2>Содержание</h2>
          <div className={styles.fields}>
            <label>Название
              <input name="title" required maxLength={200} defaultValue={task?.title} aria-invalid={Boolean(errorId('title'))}
                aria-describedby={errorId('title')} />{fieldError('title')}
            </label>
            <label>Описание
              <textarea name="description" rows={5} maxLength={10000} defaultValue={task?.description}
                aria-invalid={Boolean(errorId('description'))}
                aria-describedby={errorId('description')} />{fieldError('description')}
            </label>
          </div>
        </div>
        <div className={styles.formSection}>
          <h2>Назначение</h2>
          <div className={styles.fields}>
            <label>Исполнитель
              <select name="assigneeId" required defaultValue={task?.assigneeId ?? ''}
                aria-invalid={Boolean(errorId('assigneeId'))} aria-describedby={errorId('assigneeId')}>
                <option value="">Выберите участника проекта</option>
                {task && !assignees.some(person => person.id === task.assigneeId)
                  && <option value={task.assigneeId}>{task.assigneeName} (текущее назначение)</option>}
                {assignees.map(person => <option key={person.id} value={person.id}>{person.name}</option>)}
              </select>
              {!task && assignees.length === 0 && <span className={styles.hint}>Сначала добавьте активного исполнителя в состав проекта.</span>}
              {fieldError('assigneeId')}
            </label>
            <label>Срок
              <input name="dueDate" required type="date" min="0001-01-01" max="9999-12-31" defaultValue={task?.dueDate}
                aria-invalid={Boolean(errorId('dueDate'))} aria-describedby={errorId('dueDate')} />{fieldError('dueDate')}
            </label>
            <label>Тип
              <select name="typeId" required defaultValue={task?.typeId ?? ''} aria-invalid={Boolean(errorId('typeId'))}
                aria-describedby={errorId('typeId')}>
                <option value="">Выберите тип</option>
                {types.map(value => <option key={value.id} value={value.id}>{value.name}{value.active ? '' : ' (неактивный)'}</option>)}
              </select>{fieldError('typeId')}
            </label>
            <label>Приоритет
              <select name="priorityId" required defaultValue={task?.priorityId ?? ''} aria-invalid={Boolean(errorId('priorityId'))}
                aria-describedby={errorId('priorityId')}>
                <option value="">Выберите приоритет</option>
                {priorities.map(value => <option key={value.id} value={value.id}>{value.name}{value.active ? '' : ' (неактивный)'}</option>)}
              </select>{fieldError('priorityId')}
            </label>
            {(!types.length || !priorities.length) && <p className={styles.hint}>Для сохранения нужен доступный тип и приоритет.</p>}
          </div>
        </div>
        <div className={styles.formActions}>
          <Link className={styles.secondary} to={cancelTo}>Отмена</Link>
          <button className={styles.primary} disabled={stale || !canSave}>
            {busy ? 'Сохраняем…' : task ? 'Сохранить задачу' : 'Создать задачу'}
          </button>
        </div>
      </fieldset>
    </form>
  </>;
}
