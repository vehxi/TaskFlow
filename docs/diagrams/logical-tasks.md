# Логическая модель: задачи и учёт работы

Каждая линия от USER к TASK обозначает отдельную обязательную связь: автора или текущего исполнителя. Пользователь и проект раскрыты в соседнем представлении доступа.

```mermaid
erDiagram
    USER ||--o{ TASK : authors
    USER ||--o{ TASK : executes
    PROJECT ||--o{ TASK : contains
    TASK_TYPE ||--o{ TASK : classifies
    TASK_PRIORITY ||--o{ TASK : prioritizes
    TASK ||--o{ COMMENT : has
    TASK ||--o{ TIME_ENTRY : has
    USER ||--o{ COMMENT : writes
    USER ||--o{ TIME_ENTRY : records
    TASK {
        identifier id PK
        text title
        text description
        date dueDate
        status status
    }
    TASK_TYPE {
        identifier id PK
        text name UK
        boolean active
    }
    TASK_PRIORITY {
        identifier id PK
        text name UK
        boolean active
    }
    COMMENT {
        identifier id PK
        text body
        moment createdAt
    }
    TIME_ENTRY {
        identifier id PK
        date workDate
        integer minutes
        moment createdAt
    }
```

[Логическая модель](../data-model.md) · [Физическая модель и ограничения](../database/physical-model.md) · [DDL](../database/schema.sql).
