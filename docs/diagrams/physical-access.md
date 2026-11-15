# Физическая модель: доступ и проекты

Показаны ключевые поля таблиц PostgreSQL. Кратность 0..* у user_roles отражает возможности DDL; минимум одна роль обеспечивается общей транзакцией сервиса.

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : assigned
    users ||--o{ projects : manages
    users ||--o{ project_members : participates
    projects ||--o{ project_members : contains
    users {
        bigint id PK
        varchar name
        varchar email UK
        varchar password_hash
        boolean active
        bigint version
    }
    roles {
        varchar code PK
    }
    user_roles {
        bigint user_id PK, FK
        varchar role_code PK, FK
    }
    projects {
        bigint id PK
        bigint manager_id FK
        varchar name
        bigint version
    }
    project_members {
        bigint project_id PK, FK
        bigint user_id PK, FK
        timestamptz joined_at
    }
```

[Логическая модель](../data-model.md) · [Физическая модель и ограничения](../database/physical-model.md) · [DDL](../database/schema.sql).
