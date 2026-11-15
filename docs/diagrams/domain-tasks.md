# Доменные классы: задачи и записи работы

Пользователь и проект показаны как ссылки на классы из соседнего представления. Ассоциации с авторами сохраняются при изменении assignee. Модель не задаёт окончательные JPA-аннотации.

```mermaid
classDiagram
    direction TB
    class User
    class Project
    class Task {
        Long id
        String title
        String description
        LocalDate dueDate
        TaskStatus status
        long version
    }
    class TaskStatus {
        <<enumeration>>
        NEW
        IN_PROGRESS
        DONE
        CANCELLED
    }
    class TaskType {
        Long id
        String name
        boolean active
    }
    class TaskPriority {
        Long id
        String name
        boolean active
    }
    class Comment {
        Long id
        String body
        Instant createdAt
    }
    class TimeEntry {
        Long id
        LocalDate workDate
        int minutes
        Instant createdAt
    }
    Project "1" -- "0..*" Task : tasks
    User "1" -- "0..*" Task : author
    User "1" -- "0..*" Task : assignee
    TaskType "1" -- "0..*" Task : type
    TaskPriority "1" -- "0..*" Task : priority
    Task ..> TaskStatus : status
    Task "1" -- "0..*" Comment : comments
    Task "1" -- "0..*" TimeEntry : timeEntries
    User "1" -- "0..*" Comment : author
    User "1" -- "0..*" TimeEntry : author
```

[Логическая модель](../data-model.md) · [Физическая модель и ограничения](../database/physical-model.md) · [DDL](../database/schema.sql).
