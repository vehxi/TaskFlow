# Вход и администрирование

Общий вход и выход доступны всем трём ролям. Управление пользователями и справочниками доступно только администратору.

```mermaid
usecase-beta
    direction LR
    actor Admin("Администратор")
    actor Manager("Руководитель")
    actor Executor("Исполнитель")
    systemBoundary Tracker["Трекер задач"]
        UC01("UC-01. Вход и выход")
        UC02("UC-02. Управление пользователями и ролями")
        UC03("UC-03. Управление справочниками")
    end
    Admin -- UC01
    Manager -- UC01
    Executor -- UC01
    Admin -- UC02
    Admin -- UC03
```

Идентификаторы и ограничения доступа раскрыты в [текстовых сценариях](../use-cases.md).

Для просмотра требуется Mermaid 12+ с поддержкой `usecase-beta`. Тип диаграмм находится в бете: [справочник синтаксиса Mermaid](https://mermaid.js.org/syntax/usecase.html).
