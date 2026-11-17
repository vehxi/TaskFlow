# Компоненты приложения

React отображает интерфейс и обращается к Spring API. Все проверки данных и полномочий выполняются сервером.

```mermaid
flowchart TD
    Browser["Браузер / React SPA<br/>TypeScript, CSS Modules"] --> Origin["Один origin<br/>Vite proxy в разработке / Spring static в JAR"]
    Origin --> Security["Spring Security<br/>Сессия, CSRF, актуальные роли"]
    Security --> API["REST-контроллеры и DTO"]
    API --> Services["Прикладные сервисы и координаторы<br/>Доступ, правила, транзакции"]
    Services --> Repositories["JPA-репозитории"]
    Repositories --> DB[(PostgreSQL)]
    Services --> Excel["ExcelExportService<br/>ReportDto → файл .xlsx"]
    Services --> Mail["MailNotificationService<br/>После commit"]
    Mail --> SMTP["Внешний SMTP-сервис"]
```

[Архитектура](../architecture.md) · [Контракт API](../api-contract.md) · [Экраны](../screens.md).
