# Логическая модель: доступ и проекты

Кратность 1..* ролей — бизнес-инвариант пользователя. Его обеспечение сервисом отдельно от внешних ключей описано в физической модели.

```mermaid
erDiagram
    USER {
        identifier id PK
        text name
        text email UK
        secret passwordHash
        boolean active
    }
    ROLE {
        code code PK
    }
    USER_ROLE {
        identifier user PK, FK
        code role PK, FK
    }
    PROJECT {
        identifier id PK
        text name
        text description
    }
    MEMBERSHIP {
        identifier project PK, FK
        identifier user PK, FK
        moment joinedAt
    }
    USER ||--|{ USER_ROLE : has
    ROLE ||--o{ USER_ROLE : assigned
    USER ||--o{ PROJECT : manages
    USER ||--o{ MEMBERSHIP : participates
    PROJECT ||--o{ MEMBERSHIP : contains
```

[Логическая модель](../data-model.md) · [Физическая модель и ограничения](../database/physical-model.md) · [DDL](../database/schema.sql).
