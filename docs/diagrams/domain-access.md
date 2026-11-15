# Доменные классы: пользователи и проекты

Идентичность ProjectMembership определяется парой Project и User. Роли не образуют иерархию наследования пользователей; одна учётная запись может совмещать роли.

```mermaid
classDiagram
    direction LR
    class User {
        Long id
        String name
        String email
        String passwordHash
        boolean active
        Set~Role~ roles
    }
    class Role {
        <<enumeration>>
        ADMIN
        MANAGER
        EXECUTOR
    }
    class Project {
        Long id
        String name
        String description
    }
    class ProjectMembership {
        Instant joinedAt
    }
    User "0..*" -- "1..*" Role : roles
    User "1" -- "0..*" Project : manager
    User "1" -- "0..*" ProjectMembership : member
    Project "1" -- "0..*" ProjectMembership : participants
```

[Логическая модель](../data-model.md) · [Физическая модель и ограничения](../database/physical-model.md) · [DDL](../database/schema.sql).
