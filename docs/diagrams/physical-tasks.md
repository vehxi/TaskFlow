# Физическая модель: задачи и учёт работы

Показаны ключевые поля PostgreSQL. Внешние ключи исполнителя, авторов и проекта обязательны. Пользователь и проект раскрыты в соседнем представлении доступа. Отметка UK у имени справочника соответствует уникальному индексу lower(name).

```mermaid
erDiagram
    users ||--o{ tasks : author_id
    users ||--o{ tasks : assignee_id
    projects ||--o{ tasks : project_id
    task_types ||--o{ tasks : type_id
    task_priorities ||--o{ tasks : priority_id
    tasks ||--o{ comments : task_id
    tasks ||--o{ time_entries : task_id
    users ||--o{ comments : author_id
    users ||--o{ time_entries : author_id
    tasks {
        bigint id PK
        bigint project_id FK
        bigint author_id FK
        bigint assignee_id FK
        bigint type_id FK
        bigint priority_id FK
        varchar title
        date due_date
        varchar status
        bigint version
    }
    task_types {
        bigint id PK
        varchar name UK
        boolean active
        bigint version
    }
    task_priorities {
        bigint id PK
        varchar name UK
        boolean active
        bigint version
    }
    comments {
        bigint id PK
        bigint task_id FK
        bigint author_id FK
        text body
    }
    time_entries {
        bigint id PK
        bigint task_id FK
        bigint author_id FK
        date work_date
        integer minutes
    }
```

[Логическая модель](../data-model.md) · [Физическая модель и ограничения](../database/physical-model.md) · [DDL](../database/schema.sql).
