# Сохранение команды и уведомление

Координатор не удерживает транзакцию БД во время SMTP-запроса. На схеме раскрыт путь команды, требующей уведомления; остальные возвращают NOT_REQUIRED.

```mermaid
sequenceDiagram
    actor User as Руководитель
    participant Web as Контроллер
    participant Coord as Координатор
    participant Tx as Транзакционный сервис
    participant DB as PostgreSQL
    participant Mail as Почтовый сервис
    User->>Web: Команда, CSRF, ожидаемая версия
    Web->>Coord: DTO и текущий пользователь
    Coord->>Tx: Выполнить команду
    Tx->>DB: BEGIN, блокировки, проверки
    alt Ошибка доступа, правил или версии
        Tx->>DB: ROLLBACK
        Tx-->>Coord: Ошибка
        Coord-->>Web: Ошибка без уведомления
        Web-->>User: Ошибка формы или HTTP 4xx
    else Команда допустима
        Tx->>DB: Сохранение и COMMIT
        Tx-->>Coord: DTO и снимок уведомления
        Coord->>Mail: Отправить письмо после commit
        alt SMTP принял письмо
            Mail-->>Coord: SENT
        else Ошибка SMTP
            Mail-->>Coord: FAILED, ошибка записана в журнал
        end
        Coord-->>Web: Сохранённые данные и статус уведомления
        Web-->>User: Успех, при FAILED — предупреждение
    end
```

Снимок уведомления не содержит ленивых JPA-связей. Ошибка SMTP не откатывает commit; внезапная ошибка самого commit обрабатывается как неуспешная команда и не запускает отправку. [Подробнее об архитектуре](../architecture.md).
