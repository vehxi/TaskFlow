import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { getProjects } from '../api/taskflow.ts';
import type { Page, ProjectSummary } from '../api/types.ts';
import { errorMessage, useSession } from '../app/session.tsx';
import { Icon, Notice } from '../app/ui.tsx';
import styles from '../app/App.module.css';
import projectStyles from './Projects.module.css';

export function ProjectList() {
  const { user } = useSession();
  const [params, setParams] = useSearchParams();
  const rawPage = Number(params.get('page') ?? '0');
  const page = Number.isSafeInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const canCreate = user?.roles.includes('MANAGER');
  const [result, setResult] = useState<Page<ProjectSummary> | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    setError('');
    getProjects(page, controller.signal).then(data => {
      if (!controller.signal.aborted) setResult(data);
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) setError(errorMessage(failure));
    });
    return () => controller.abort();
  }, [page, attempt]);

  return <section aria-labelledby="project-list-title">
    <div className={styles.pageHeading}>
      <h1 id="project-list-title">Проекты</h1>
      {canCreate && <Link className={styles.primary} to="/projects/new"><Icon name="plus" />Новый проект</Link>}
    </div>
    {error ? <><Notice>{error}</Notice><button onClick={() => setAttempt(current => current + 1)}>Повторить загрузку</button></>
      : !result ? <p className={styles.loading} role="status">Загружаем проекты…</p>
      : !result.items.length ? <div className={styles.empty}>
        <h2>{page ? 'На этой странице нет проектов' : 'Пока нет проектов'}</h2>
        {page ? <button onClick={() => setParams({})}>К началу списка</button>
          : <p>{canCreate ? 'Создайте проект, чтобы собрать его команду.' : 'Здесь появятся проекты, в которые вас добавит руководитель.'}</p>}
      </div>
      : <ul className={projectStyles.projectList}>
        {result.items.map(project => <li key={project.id}>
          <Link to={`/projects/${project.id}`} className={projectStyles.projectRow}>
            <span className={projectStyles.folder} aria-hidden="true"><Icon name="projects" /></span>
            <div className={projectStyles.summary}>
              <strong>{project.name}</strong>
              {project.description && <p>{project.description}</p>}
            </div>
            <Icon name="arrow" />
          </Link>
        </li>)}
      </ul>}
    {result && <ProjectPagination page={page} pages={result.totalPages} label="Страницы проектов"
      onChange={next => setParams(next ? { page: String(next) } : {})} />}
  </section>;
}

export function ProjectPagination({ page, pages, label, onChange, disabled = false }: {
  page: number; pages: number; label: string; disabled?: boolean; onChange: (page: number) => void;
}) {
  if (pages < 2 && page === 0) return null;
  return <nav className={styles.pagination} aria-label={label}>
    <span>{pages > 0 ? `Страница ${page + 1} из ${pages}` : 'Нет записей'}</span>
    <div>
      <button type="button" disabled={disabled || page === 0} onClick={() => onChange(page - 1)}>← Назад</button>
      <button type="button" disabled={disabled || page + 1 >= pages} onClick={() => onChange(page + 1)}>Далее →</button>
    </div>
  </nav>;
}
