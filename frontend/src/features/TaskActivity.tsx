import { useCallback, useRef, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { ApiError } from '../api/client.ts';
import { addTaskComment, addTaskTime, getTaskAssignees, getTaskComments, getTaskTime, setTaskStatus } from '../api/tasks.ts';
import type { TaskDetail, TaskStatus } from '../api/types.ts';
import { errorMessage } from '../app/session.tsx';
import { Notice } from '../app/ui.tsx';
import styles from '../app/App.module.css';
import taskStyles from './Tasks.module.css';
import { ProjectPagination } from './ProjectList.tsx';
import { displayDueDate, isOpenTask, taskStatusNames } from './taskDisplay.ts';
import { useTaskResource } from './useTaskResource.ts';

const dateTime = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' });
const numberFormat = new Intl.NumberFormat('ru-RU');

export function TaskActivity({ task, isManager, isExecutor, onChange, reload, children }: {
  task: TaskDetail; isManager: boolean; isExecutor: boolean; onChange: (task: TaskDetail) => void; reload: () => void;
  children: ReactNode;
}) {
  const [commentsPage, setCommentsPage] = useState(0);
  const [timePage, setTimePage] = useState(0);
  const [busy, setBusy] = useState(false);
  const [stale, setStale] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [statusFeedback, setStatusFeedback] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [reopening, setReopening] = useState(false);
  const pending = useRef(false);
  const loadComments = useCallback((signal: AbortSignal) => getTaskComments(task.id, commentsPage, signal),
    [task.id, commentsPage]);
  const loadTime = useCallback((signal: AbortSignal) => getTaskTime(task.id, timePage, signal), [task.id, timePage]);
  const comments = useTaskResource(loadComments);
  const time = useTaskResource(loadTime);
  const open = isOpenTask(task.status);
  const disabled = busy || stale;

  async function commit(action: () => Promise<void>, message: string, updatesStatus = false) {
    if (pending.current || stale) return;
    pending.current = true;
    setBusy(true);
    setError('');
    setNotice('');
    setStatusFeedback(updatesStatus);
    try {
      await action();
      setNotice(message);
    } catch (failure) {
      setError(errorMessage(failure));
      setStale(failure instanceof ApiError && failure.code === 'STALE_VERSION');
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  function transition(target: TaskStatus, replacement?: number) {
    void commit(async () => {
      const { data } = await setTaskStatus(task.id, target, task.version, replacement);
      setConfirmCancel(false);
      setReopening(false);
      onChange(data);
    }, `Статус обновлён: «${taskStatusNames[target]}».`, true);
  }

  function sendComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!comments.data) return;
    const form = event.currentTarget;
    const body = String(new FormData(form).get('body')).trim();
    if (!body) {
      setStatusFeedback(false);
      setNotice('');
      setError('Напишите комментарий.');
      return;
    }
    const lastPage = Math.floor(comments.data.totalElements / comments.data.size);
    void commit(async () => {
      const { data } = await addTaskComment(task.id, body, task.version);
      form.reset();
      onChange({ ...task, version: data.taskVersion });
      setCommentsPage(lastPage);
      comments.retry();
    }, 'Комментарий добавлен.');
  }

  function sendTime(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!time.data) return;
    const form = event.currentTarget;
    const values = new FormData(form);
    const lastPage = Math.floor(time.data.totalElements / time.data.size);
    void commit(async () => {
      const { data } = await addTaskTime(task.id, String(values.get('workDate')), Number(values.get('minutes')), task.version);
      form.reset();
      onChange({ ...task, version: data.taskVersion });
      setTimePage(lastPage);
      time.retry();
    }, 'Время записано.');
  }

  const canTransition = open ? isManager || isExecutor : isManager;
  const feedback = error ? <Notice>{error}{stale && <button type="button" className={styles.inlineButton} onClick={reload}>
    Обновить карточку
  </button>}</Notice> : notice && <p className={styles.success} role="status">{notice}</p>;

  return <div aria-busy={busy}>
    <section aria-label="Статус задачи" className={taskStyles.taskStatus}>
      <div className={taskStyles.statusRow}>
        <span className={styles.role}>{taskStatusNames[task.status]}</span>
        {canTransition && task.status === 'NEW' && <button className={styles.primary} disabled={disabled || confirmCancel}
          onClick={() => transition('IN_PROGRESS')}>Начать работу</button>}
        {canTransition && task.status === 'IN_PROGRESS' && <button className={styles.primary} disabled={disabled || confirmCancel}
          onClick={() => transition('DONE')}>Отметить выполненной</button>}
        {open && isManager && !confirmCancel && <button className={styles.secondary} disabled={disabled}
          onClick={() => setConfirmCancel(true)}>Отменить задачу</button>}
        {!open && isManager && !reopening && <button className={styles.primary} disabled={disabled}
          onClick={() => setReopening(true)}>Открыть повторно</button>}
      </div>
      {confirmCancel && <div className={taskStyles.activityPrompt}>
        <p>Отменить выполнение задачи?</p>
        <div className={taskStyles.activityActions}>
          <button className={styles.secondary} disabled={busy} onClick={() => setConfirmCancel(false)}>Назад</button>
          <button className={styles.primary} disabled={disabled} onClick={() => transition('CANCELLED')}>Подтвердить отмену</button>
        </div>
      </div>}
      {reopening && <ReopenForm task={task} disabled={disabled} close={() => setReopening(false)}
        reopen={replacement => transition('NEW', replacement)} />}
      {statusFeedback && feedback}
    </section>
    {children}
    <div className={taskStyles.activity}>
    {!statusFeedback && feedback}
    <section aria-labelledby="task-comments">
      <div className={styles.sectionBar}><h2 id="task-comments">Комментарии</h2></div>
      {comments.error ? <><Notice>{comments.error}</Notice><button onClick={comments.retry}>Повторить загрузку</button></>
        : !comments.data ? <p role="status" className={taskStyles.activityEmpty}>Загружаем комментарии…</p>
        : <>
          {comments.data.items.length ? <ul className={taskStyles.history}>
            {comments.data.items.map(comment => <li key={comment.id}>
              <header><strong>{comment.authorName}</strong>
                <time dateTime={comment.createdAt}>{dateTime.format(new Date(comment.createdAt))}</time>
              </header>
              <p>{comment.body}</p>
            </li>)}
          </ul> : <p className={taskStyles.activityEmpty}>Комментариев пока нет.</p>}
          <ProjectPagination page={comments.data.page} pages={comments.data.totalPages} label="Страницы комментариев"
            onChange={setCommentsPage} disabled={busy} />
          {open && (isManager || isExecutor) && <form className={taskStyles.activityForm} onSubmit={sendComment}>
            <fieldset className={styles.fields} disabled={disabled}>
              <label>Новый комментарий<textarea name="body" rows={3} required maxLength={5000} /></label>
              <button className={styles.primary}>Добавить комментарий</button>
            </fieldset>
          </form>}
        </>}
    </section>
    <section aria-labelledby="task-time">
      <div className={styles.sectionBar}><h2 id="task-time">Трудозатраты</h2>
        {time.data && <strong className={taskStyles.timeTotal}>Всего {numberFormat.format(time.data.totalMinutes)} мин</strong>}
      </div>
      {time.error ? <><Notice>{time.error}</Notice><button onClick={time.retry}>Повторить загрузку</button></>
        : !time.data ? <p role="status" className={taskStyles.activityEmpty}>Загружаем трудозатраты…</p>
        : <>
          {time.data.items.length ? <ul className={taskStyles.history}>
            {time.data.items.map(entry => <li key={entry.id}>
              <header><strong>{entry.authorName}</strong>
                <time dateTime={entry.workDate}>{displayDueDate(entry.workDate)}</time>
                <span>{numberFormat.format(entry.minutes)} мин</span>
              </header>
            </li>)}
          </ul> : <p className={taskStyles.activityEmpty}>Записей времени пока нет.</p>}
          <ProjectPagination page={time.data.page} pages={time.data.totalPages} label="Страницы записей времени"
            onChange={setTimePage} disabled={busy} />
          {open && isExecutor && <form className={taskStyles.activityForm} onSubmit={sendTime}>
            <fieldset disabled={disabled} className={taskStyles.timeForm}>
              <label>Дата работы
                <input type="date" name="workDate" min="0001-01-01" max={time.data.today}
                  defaultValue={time.data.today} required />
              </label>
              <label>Минуты
                <input type="number" name="minutes" min="1" max="2147483647" step="1" inputMode="numeric" required />
              </label>
              <button className={styles.primary}>Записать время</button>
            </fieldset>
          </form>}
        </>}
    </section>
    </div>
  </div>;
}

function ReopenForm({ task, disabled, close, reopen }: {
  task: TaskDetail; disabled: boolean; close: () => void; reopen: (replacement?: number) => void;
}) {
  const load = useCallback((signal: AbortSignal) => getTaskAssignees(task.projectId, signal), [task.projectId]);
  const candidates = useTaskResource(load);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const id = new FormData(event.currentTarget).get('assigneeId');
    reopen(id ? Number(id) : undefined);
  }

  return <form className={taskStyles.activityPrompt} onSubmit={submit}>
    <fieldset className={styles.fields} disabled={disabled}>
      <label>Исполнитель при повторном открытии
        <select name="assigneeId" defaultValue="">
          <option value="">Оставить текущего ({task.assigneeName})</option>
          {candidates.data?.filter(user => user.id !== task.assigneeId)
            .map(user => <option key={user.id} value={user.id}>{user.name}</option>)}
        </select>
      </label>
      {!candidates.data && !candidates.error && <p role="status">Загружаем участников…</p>}
      {candidates.error && <Notice>{candidates.error}
        <button type="button" className={styles.inlineButton} onClick={candidates.retry}>Повторить загрузку</button>
      </Notice>}
      <div className={taskStyles.activityActions}>
        <button className={styles.secondary} type="button" onClick={close}>Назад</button>
        <button className={styles.primary}>Открыть задачу</button>
      </div>
    </fieldset>
  </form>;
}
