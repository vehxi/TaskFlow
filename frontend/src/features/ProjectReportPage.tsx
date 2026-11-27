import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { downloadProjectReport, getOverdueTasks, getProjectReport } from '../api/reports.ts';
import type { ProjectReport } from '../api/reports.ts';
import type { TaskStatus } from '../api/types.ts';
import { errorMessage, useSession } from '../app/session.tsx';
import { Notice } from '../app/ui.tsx';
import styles from '../app/App.module.css';
import reportStyles from './ProjectReportPage.module.css';
import { displayDueDate, taskStatusNames } from './taskDisplay.ts';
import { useTaskResource } from './useTaskResource.ts';

const integer = new Intl.NumberFormat('ru-RU');
const percent = new Intl.NumberFormat('ru-RU', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 });
const statuses = Object.keys(taskStatusNames) as TaskStatus[];

export function ProjectReportPage() {
  const { id } = useParams();
  const { user } = useSession();
  if (!user?.roles.includes('MANAGER')) {
    return <section><Link className={styles.backLink} to="/projects">← Проекты</Link>
      <h1>Отчёт доступен руководителю проекта</h1>
    </section>;
  }
  return id ? <Report key={id} id={id} /> : null;
}

function Report({ id }: { id: string }) {
  const load = useCallback((signal: AbortSignal) => getProjectReport(id, signal), [id]);
  const { data, error, retry } = useTaskResource(load);

  return <section className={reportStyles.report}>
    <Link className={styles.backLink} to={`/projects/${encodeURIComponent(id)}`}>← К проекту</Link>
    {!data && <h1>Отчёт по проекту</h1>}
    {error && <><Notice>{error}</Notice><button className={styles.secondary} onClick={retry}>Повторить загрузку</button></>}
    {!error && !data && <p className={styles.loading} role="status">Формируем отчёт…</p>}
    {data && <>
      <div className={reportStyles.heading}>
        <div>
          <p className={reportStyles.caption}>Отчёт по проекту</p>
          <h1>{data.project.name}</h1>
        </div>
        <div className={reportStyles.controls}>
          <button className={styles.secondary} onClick={retry}>Обновить</button>
          <ExportButton id={id} />
        </div>
      </div>
      <p className={reportStyles.timestamp}>
        Сформирован <time dateTime={data.generatedAt}>{displayDueDate(data.generatedAt.slice(0, 10))}, {data.generatedAt.slice(11, 16)}</time>
      </p>
      <ReportSummary data={data} />
      {data.overdueCount > 0 && <OverdueTasks id={id} />}
      <div className={reportStyles.details}>
        <Breakdown title="Задачи по исполнителям" nameLabel="Исполнитель" valueLabel="Задачи"
          rows={data.tasksByAssignee.map(item => ({ ...item, value: item.taskCount }))} empty="В проекте ещё нет задач." />
        <Breakdown title="Время по авторам записей" nameLabel="Автор записи" valueLabel="Минуты"
          rows={data.minutesByAuthor.map(item => ({ ...item, value: item.minutes }))} empty="В проекте ещё нет записей времени." />
      </div>
    </>}
  </section>;
}

function ExportButton({ id }: { id: string }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);

  async function save() {
    if (request.current) return;
    const controller = new AbortController();
    request.current = controller;
    setError('');
    setBusy(true);
    try {
      const file = await downloadProjectReport(id, controller.signal);
      if (controller.signal.aborted) return;
      const url = URL.createObjectURL(file);
      const link = document.createElement('a');
      link.href = url;
      link.download = `taskflow-project-${id}.xlsx`;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (failure) {
      if (!controller.signal.aborted) setError(errorMessage(failure));
    } finally {
      request.current = null;
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  return <div className={reportStyles.export}>
    <button className={styles.primary} disabled={busy} onClick={() => void save()}>
      {busy ? 'Готовим файл…' : 'Скачать Excel'}
    </button>
    {error && <Notice>{error}</Notice>}
  </div>;
}

function ReportSummary({ data }: { data: ProjectReport }) {
  return <div className={reportStyles.summary}>
    <dl className={reportStyles.metrics}>
      <div className={reportStyles.progressMetric}>
        <dt>Прогресс</dt>
        <dd>
          {percent.format(data.progressPercent / 100)}
          <progress aria-label="Прогресс проекта" value={data.progressPercent} max={100} />
        </dd>
      </div>
      <div>
        <dt>Просрочено</dt>
        <dd>{data.overdueCount > 0
          ? <a href="#overdue-tasks" aria-label={`Просроченные задачи: ${integer.format(data.overdueCount)}`}>{integer.format(data.overdueCount)}</a>
          : integer.format(0)}</dd>
      </div>
      <div><dt>Время, мин</dt><dd>{integer.format(data.totalMinutes)}</dd></div>
    </dl>
    <section className={reportStyles.statuses}>
      <h2>Задачи по статусам</h2>
      <dl>{statuses.map(status => <div key={status}>
        <dt>{taskStatusNames[status]}</dt><dd>{integer.format(data.byStatus[status])}</dd>
      </div>)}</dl>
    </section>
  </div>;
}

function OverdueTasks({ id }: { id: string }) {
  const [page, setPage] = useState(0);
  const load = useCallback((signal: AbortSignal) => getOverdueTasks(id, page, signal), [id, page]);
  const { data, error, retry } = useTaskResource(load);

  return <section id="overdue-tasks" className={reportStyles.overdue}>
    <h2>Просроченные задачи</h2>
    {error && <><Notice>{error}</Notice><button className={styles.secondary} onClick={retry}>Повторить загрузку</button></>}
    {!error && !data && <p role="status">Загружаем задачи…</p>}
    {data && <>
      {data.items.length === 0 ? <p>На этой странице нет просроченных задач.</p>
        : <div className={reportStyles.tableWrap} tabIndex={0} role="region" aria-label="Просроченные задачи">
          <table className={reportStyles.overdueTable}>
            <thead><tr><th scope="col">Задача</th><th scope="col">Срок</th><th scope="col">Статус</th><th scope="col">Исполнитель</th></tr></thead>
            <tbody>{data.items.map(task => <tr key={task.id}>
              <th scope="row"><Link to={`/tasks/${task.id}`}>{task.title}</Link></th>
              <td>{displayDueDate(task.dueDate)}</td>
              <td>{taskStatusNames[task.status]}</td>
              <td>{task.assigneeName}</td>
            </tr>)}</tbody>
          </table>
        </div>}
      {(data.totalPages > 1 || page > 0) && <div className={styles.pagination}>
        <span>Страница {page + 1}{data.totalPages >= page + 1 ? ` из ${data.totalPages}` : ''}</span>
        <div>
          <button disabled={page === 0} onClick={() => setPage(page - 1)}>Назад</button>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Дальше</button>
        </div>
      </div>}
    </>}
  </section>;
}

function Breakdown({ title, nameLabel, valueLabel, rows, empty }: {
  title: string; nameLabel: string; valueLabel: string;
  rows: { userId: number; name: string; value: number }[];
  empty: string;
}) {
  return <section>
    <h2>{title}</h2>
    {rows.length === 0 ? <p>{empty}</p>
      : <table aria-label={title} className={reportStyles.breakdown}>
        <thead><tr><th scope="col">{nameLabel}</th><th scope="col">{valueLabel}</th></tr></thead>
        <tbody>{rows.map(row => <tr key={row.userId}>
          <th scope="row">{row.name}</th><td>{integer.format(row.value)}</td>
        </tr>)}</tbody>
      </table>}
  </section>;
}
