import { useCallback, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { getTaskFilters, getTasks } from '../api/tasks.ts';
import { useSession } from '../app/session.tsx';
import { Icon, Notice } from '../app/ui.tsx';
import styles from '../app/App.module.css';
import taskStyles from './Tasks.module.css';
import { ProjectPagination } from './ProjectList.tsx';
import { displayDueDate, taskStatusNames } from './taskDisplay.ts';
import { useTaskResource } from './useTaskResource.ts';

export function TaskList() {
  const { user } = useSession();
  const [params, setParams] = useSearchParams();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [animateFilters, setAnimateFilters] = useState(false);
  const filterToggle = useRef<HTMLButtonElement>(null);
  const canManage = user?.roles.includes('MANAGER');
  const filters = new URLSearchParams(params);
  if (!canManage) filters.delete('assigneeId');
  const query = filters.toString();
  const activeFilters = new URLSearchParams(filters);
  activeFilters.delete('page');
  const filterOptions = useTaskResource(getTaskFilters);
  const load = useCallback((signal: AbortSignal) => getTasks(query, signal), [query]);
  const list = useTaskResource(load);
  const selectedProject = activeFilters.get('projectId');
  const selectedAssignee = activeFilters.get('assigneeId');

  function changePage(page: number) {
    const next = new URLSearchParams(activeFilters);
    if (page > 0) next.set('page', String(page));
    setParams(next);
  }

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = new URLSearchParams();
    for (const [name, value] of new FormData(event.currentTarget)) {
      if (typeof value === 'string' && value) next.set(name, value);
    }
    setParams(next);
    setFiltersOpen(false);
    filterToggle.current?.focus();
  }

  return <section aria-labelledby="tasks-title">
    <div className={`${styles.pageHeading} ${taskStyles.listHeading}`}>
      <h1 id="tasks-title">Задачи</h1>
      {canManage && <Link className={styles.secondary} to="/projects"><Icon name="projects" />К проектам</Link>}
      <button ref={filterToggle} type="button" className={taskStyles.filterToggle}
        aria-expanded={filtersOpen} aria-controls="task-filters" onClick={event => {
          setAnimateFilters(event.detail > 0);
          setFiltersOpen(open => !open);
        }}>
        <Icon name="filters" />Фильтры{activeFilters.size > 0 && <span className={styles.count}>{activeFilters.size}</span>}
      </button>
    </div>
    <div id="task-filters" className={taskStyles.filterPanel} data-expanded={filtersOpen} data-animate={animateFilters}>
      <div className={taskStyles.filterBody}>
        <form key={activeFilters.toString()} className={taskStyles.filters} onSubmit={apply} aria-label="Фильтры задач">
          <label>Проект
            <select name="projectId" defaultValue={selectedProject ?? ''}>
              <option value="">Все проекты</option>
              {selectedProject && <option value={selectedProject}>
                {filterOptions.data?.projects.find(project => String(project.id) === selectedProject)?.name ?? 'Выбранный проект'}
              </option>}
              {filterOptions.data?.projects.filter(project => String(project.id) !== selectedProject)
                .map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
            </select>
          </label>
          <label>Статус
            <select name="status" defaultValue={activeFilters.get('status') ?? ''}>
              <option value="">Все статусы</option>
              {Object.entries(taskStatusNames).map(([status, label]) => <option key={status} value={status}>{label}</option>)}
            </select>
          </label>
          <DateFilter label="Срок с" name="dueFrom" initialValue={activeFilters.get('dueFrom') ?? ''} />
          <DateFilter label="Срок по" name="dueTo" initialValue={activeFilters.get('dueTo') ?? ''} />
          {canManage && <label>Исполнитель
            <select name="assigneeId" defaultValue={selectedAssignee ?? ''}>
              <option value="">Все исполнители</option>
              {selectedAssignee && <option value={selectedAssignee}>
                {filterOptions.data?.assignees.find(member => String(member.id) === selectedAssignee)?.name ?? 'Выбранный исполнитель'}
              </option>}
              {filterOptions.data?.assignees.filter(member => String(member.id) !== selectedAssignee)
                .map(member => <option key={member.id} value={member.id}>{member.name}</option>)}
            </select>
          </label>}
          <div className={taskStyles.filterActions}>
            <button className={styles.primary} onClick={event => setAnimateFilters(event.detail > 0)}>Применить</button>
            {activeFilters.size > 0 && <Link className={styles.secondary} to="/tasks">Сбросить</Link>}
          </div>
        </form>
      </div>
    </div>
    {filterOptions.error && <Notice>{filterOptions.error}
      <button type="button" className={styles.inlineButton} onClick={filterOptions.retry}>Загрузить варианты фильтров</button>
    </Notice>}
    {list.error ? <><Notice>{list.error}</Notice><button onClick={list.retry}>Повторить загрузку</button></>
      : !list.data ? <p role="status" className={styles.loading}>Загружаем задачи…</p>
      : list.data.items.length === 0 ? <div className={styles.empty}>
        <h2>{activeFilters.size ? 'По этим фильтрам задач нет' : 'Пока нет задач'}</h2>
        {!activeFilters.size && list.data.page === 0 && <p>{canManage
          ? 'Откройте свой проект, чтобы создать задачу.' : 'Здесь появятся задачи, назначенные вам.'}</p>}
      </div>
      : <ul className={taskStyles.list}>{list.data.items.map(task => <li key={task.id}>
        <Link className={taskStyles.row} to={`/tasks/${task.id}`}>
          <div><strong>{task.title}</strong><small>{task.projectName}</small></div>
          <div className={taskStyles.rowMeta}>
            <span className={styles.role}>{taskStatusNames[task.status]}</span>
            <time dateTime={task.dueDate}>{displayDueDate(task.dueDate)}</time>
            {canManage && <span>{task.assigneeName}</span>}
          </div>
          <Icon name="arrow" />
        </Link>
      </li>)}</ul>}
    {list.data && <ProjectPagination page={list.data.page} pages={list.data.totalPages} label="Страницы задач" onChange={changePage} />}
  </section>;
}

function DateFilter({ label, name, initialValue }: { label: string; name: string; initialValue: string }) {
  const [value, setValue] = useState(/^\d{4}-\d{2}-\d{2}$/.test(initialValue) ? initialValue : '');
  const display = value ? value.split('-').reverse().join('.') : 'Выбрать дату';

  return <label>{label}
    <span className={taskStyles.dateControl}>
      <span className={taskStyles.dateDisplay} data-empty={!value} aria-hidden="true">
        <span>{display}</span><Icon name="calendar" />
      </span>
      <input type="date" name={name} value={value} onChange={event => setValue(event.target.value)} />
    </span>
  </label>;
}
