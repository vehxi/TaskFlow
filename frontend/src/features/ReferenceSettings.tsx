import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../api/client.ts';
import { createReferenceValue, getReferenceData, getReferenceValue, updateReferenceValue } from '../api/references.ts';
import type { ReferenceCatalog } from '../api/references.ts';
import type { ReferenceValue } from '../api/types.ts';
import { errorMessage } from '../app/session.tsx';
import { Icon, Notice } from '../app/ui.tsx';
import styles from '../app/App.module.css';
import catalogStyles from './ReferenceSettings.module.css';
import { useTaskResource } from './useTaskResource.ts';

export function ReferenceSettings() {
  const { data, error, retry } = useTaskResource(getReferenceData);
  return <section>
    <h1>Справочники</h1>
    <p className={styles.lead}>Неактивные значения сохраняются в существующих задачах.</p>
    {error && <><Notice>{error}</Notice><button className={styles.secondary} onClick={retry}>Повторить загрузку</button></>}
    {!error && !data && <p className={styles.loading} role="status">Загружаем справочники…</p>}
    {data && <div className={catalogStyles.catalogs}>
      <Catalog kind="types" title="Типы задач" addLabel="Добавить тип" initialValues={data.types} />
      <Catalog kind="priorities" title="Приоритеты" addLabel="Добавить приоритет" initialValues={data.priorities} />
    </div>}
  </section>;
}

function Catalog({ kind, title, addLabel, initialValues }: {
  kind: ReferenceCatalog;
  title: string;
  addLabel: string;
  initialValues: ReferenceValue[];
}) {
  const [values, setValues] = useState(initialValues);
  const [editor, setEditor] = useState<'new' | ReferenceValue | null>(null);
  const [notice, setNotice] = useState('');
  const editTrigger = useRef<HTMLButtonElement | null>(null);

  function showEditor(value: 'new' | ReferenceValue, trigger: HTMLButtonElement) {
    editTrigger.current = trigger;
    setNotice('');
    setEditor(value);
  }

  function closeEditor() {
    setEditor(null);
    editTrigger.current?.focus();
  }

  function applyValue(value: ReferenceValue) {
    setValues(current => current.some(item => item.id === value.id)
      ? current.map(item => item.id === value.id ? value : item)
      : [...current, value]);
  }

  function saved(value: ReferenceValue) {
    applyValue(value);
    setNotice(editor === 'new' ? 'Значение добавлено.' : 'Изменения сохранены.');
    closeEditor();
  }

  return <section className={catalogStyles.catalog} aria-labelledby={`${kind}-heading`}>
    <header className={catalogStyles.heading}>
      <h2 id={`${kind}-heading`}>{title}</h2>
      <button className={styles.secondary} type="button" aria-expanded={editor === 'new'}
        aria-disabled={editor !== null} onClick={event => {
          if (editor === null) showEditor('new', event.currentTarget);
        }}><Icon name="plus" />{addLabel}</button>
    </header>
    {notice && <p className={styles.success} role="status">{notice}</p>}
    {editor === 'new' && <ValueEditor kind={kind} onSaved={saved} onReloaded={applyValue} onCancel={closeEditor} />}
    {values.length === 0 && <p className={catalogStyles.empty}>В справочнике пока нет значений.</p>}
    <ul className={catalogStyles.values}>
      {values.map(value => {
        const editing = editor !== null && editor !== 'new' && editor.id === value.id;
        return <li key={value.id}>
          <div className={catalogStyles.row}>
            <strong>{value.name}</strong>
            <span className={value.active ? styles.active : `${styles.blocked} ${catalogStyles.inactive}`}>
              <span aria-hidden="true">{value.active ? '●' : '○'}</span>
              {value.active ? 'Активно' : 'Неактивно'}
            </span>
            <button className={styles.secondary} type="button" aria-expanded={editing}
              aria-label={`Изменить «${value.name}»`} aria-disabled={editor !== null} onClick={event => {
                if (editor === null) showEditor(value, event.currentTarget);
              }}>Изменить</button>
          </div>
          {editing && <ValueEditor kind={kind} value={editor} onSaved={saved} onReloaded={applyValue} onCancel={closeEditor} />}
        </li>;
      })}
    </ul>
  </section>;
}

function ValueEditor({ kind, value, onSaved, onReloaded, onCancel }: {
  kind: ReferenceCatalog;
  value?: ReferenceValue;
  onSaved: (value: ReferenceValue) => void;
  onReloaded: (value: ReferenceValue) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(value?.name ?? '');
  const [active, setActive] = useState(value?.active ?? true);
  const [version, setVersion] = useState(value?.version ?? 0);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);
  const [reloadError, setReloadError] = useState('');
  const sending = useRef(false);
  const fieldError = failure instanceof ApiError ? failure.fieldErrors.name?.join(' ') : undefined;
  const stale = failure instanceof ApiError && failure.code === 'STALE_VERSION';
  const errorId = `${kind}-name-error`;

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current || stale) return;
    sending.current = true;
    setBusy(true);
    setFailure(null);
    try {
      const result = value
        ? await updateReferenceValue(kind, value.id, name, active, version)
        : await createReferenceValue(kind, name);
      onSaved(result.data);
    } catch (error) {
      setFailure(error);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  async function reload(id: number) {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setReloadError('');
    try {
      const current = await getReferenceValue(kind, id);
      setName(current.name);
      setActive(current.active);
      setVersion(current.version);
      onReloaded(current);
      setFailure(null);
    } catch (error) {
      setReloadError(errorMessage(error));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return <form className={catalogStyles.editor} onSubmit={event => void save(event)}>
    {failure != null && <Notice>{reloadError || errorMessage(failure)}
      {stale && value && <button type="button" disabled={busy} className={styles.inlineButton}
        onClick={() => void reload(value.id)}>Загрузить актуальное значение</button>}
    </Notice>}
    <fieldset className={styles.fields} disabled={busy}>
      <label>Название
        <input name="name" required maxLength={80} value={name} autoFocus
          onChange={event => setName(event.target.value)} aria-invalid={Boolean(fieldError)}
          aria-describedby={fieldError ? errorId : undefined} />
        {fieldError && <span className={styles.fieldError} id={errorId}>{fieldError}</span>}
      </label>
      {value && <label className={catalogStyles.availability}>
        <input type="checkbox" checked={active} onChange={event => setActive(event.target.checked)} />
        <span>Доступно для выбора в задачах</span>
      </label>}
      <div className={catalogStyles.actions}>
        <button type="button" className={styles.secondary} onClick={onCancel}>Отмена</button>
        <button type="submit" className={styles.primary} disabled={stale || !name.trim()}>
          {busy ? 'Подождите…' : value ? 'Сохранить' : 'Добавить'}
        </button>
      </div>
    </fieldset>
  </form>;
}
