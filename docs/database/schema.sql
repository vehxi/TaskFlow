-- Проект физической модели PostgreSQL 16. Применять к пустой тестовой БД.
-- Это эталон DDL для будущей миграции, а не миграция запущенного приложения.
BEGIN;

CREATE TABLE users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(120) NOT NULL CHECK (length(btrim(name)) > 0),
    email varchar(254) NOT NULL UNIQUE
        CHECK (length(email) > 0 AND email = lower(btrim(email))),
    password_hash varchar(255) NOT NULL CHECK (length(password_hash) > 0),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    code varchar(16) PRIMARY KEY
        CHECK (code IN ('ADMIN', 'MANAGER', 'EXECUTOR'))
);
INSERT INTO roles (code) VALUES ('ADMIN'), ('MANAGER'), ('EXECUTOR');

CREATE TABLE user_roles (
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role_code varchar(16) NOT NULL REFERENCES roles(code) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_code)
);

CREATE TABLE projects (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    manager_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name varchar(200) NOT NULL CHECK (length(btrim(name)) > 0),
    description text NOT NULL DEFAULT '' CHECK (length(description) <= 10000),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE project_members (
    project_id bigint NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    joined_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE task_types (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(80) NOT NULL CHECK (length(btrim(name)) > 0 AND name = btrim(name)),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_task_types_name ON task_types (lower(name));

CREATE TABLE task_priorities (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(80) NOT NULL CHECK (length(btrim(name)) > 0 AND name = btrim(name)),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE UNIQUE INDEX uq_task_priorities_name ON task_priorities (lower(name));

CREATE TABLE tasks (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id bigint NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
    author_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assignee_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    type_id bigint NOT NULL REFERENCES task_types(id) ON DELETE RESTRICT,
    priority_id bigint NOT NULL REFERENCES task_priorities(id) ON DELETE RESTRICT,
    title varchar(200) NOT NULL CHECK (length(btrim(title)) > 0),
    description text NOT NULL DEFAULT '' CHECK (length(description) <= 10000),
    due_date date NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE comments (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES tasks(id) ON DELETE RESTRICT,
    author_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    body text NOT NULL CHECK (length(btrim(body)) > 0 AND length(body) <= 5000),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE time_entries (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES tasks(id) ON DELETE RESTRICT,
    author_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    work_date date NOT NULL,
    minutes integer NOT NULL CHECK (minutes > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_user_roles_role ON user_roles (role_code, user_id);
CREATE INDEX ix_projects_manager ON projects (manager_id, id);
CREATE INDEX ix_project_members_user ON project_members (user_id, project_id);
CREATE INDEX ix_tasks_project_status_due ON tasks (project_id, status, due_date);
CREATE INDEX ix_tasks_assignee_status_due ON tasks (assignee_id, status, due_date);
CREATE INDEX ix_tasks_author ON tasks (author_id);
CREATE INDEX ix_tasks_type ON tasks (type_id);
CREATE INDEX ix_tasks_priority ON tasks (priority_id);
CREATE INDEX ix_comments_task_created ON comments (task_id, created_at, id);
CREATE INDEX ix_comments_author ON comments (author_id);
CREATE INDEX ix_time_entries_task ON time_entries (task_id);
CREATE INDEX ix_time_entries_author_date ON time_entries (author_id, work_date);

COMMIT;
