# Контракт HTTP API

Проект API для [архитектуры](architecture.md), [сценариев](use-cases.md) и [модели данных](database/physical-model.md). Базовый префикс — /api. Клиент — React SPA. Формат ответов — JSON, кроме Excel и пустых успешных ответов входа/выхода. Бизнес-операции выполняются через API; серверных HTML-форм нет.

## Сеанс и общие соглашения

Маршрут /login принадлежит React. POST /api/session принимает application/x-www-form-urlencoded с email и password, CSRF передаётся в заголовке X-CSRF-TOKEN. Успех — 204 без перенаправления; неверные реквизиты или недоступная учётная запись — JSON 401 INVALID_CREDENTIALS с одинаковым безопасным сообщением. POST /api/session/logout с CSRF завершает сессию и отвечает 204. Маршруты обслуживает Spring Security; JSON с паролем при входе не используется.

В разработке браузер обращается к Vite на localhost:5173, который проксирует /api на localhost:8080. Клиент использует относительные URL и credentials=same-origin. В поставляемом JAR React и API доступны с одного origin. Статический index.html не является защищённым ресурсом; пользовательские данные проверяются API. JWT и хранение токенов авторизации в localStorage не используются.

GET /api/csrf доступен до входа, возвращает token и headerName=X-CSRF-TOKEN, Cache-Control: no-store. Клиент сохраняет сессионную cookie и передаёт токен в изменяющих запросах, в том числе при входе; после входа и выхода получает новый. GET /api/me требует входа и возвращает id, name, email и актуальные roles. Изменение сессионной cookie или обход проверки токена не предусмотрены.

- Идентификаторы — положительные целые; версии — неотрицательные целые. Обязательная версия передаётся явно, отсутствие версии — 400.
- Даты — YYYY-MM-DD, моменты — ISO 8601 с UTC-смещением. Перечисления используют коды физической модели.
- Списки: page от 0, size от 1 до 100, исходно 20; ответ items, page, size, totalElements, totalPages. Итоги относятся только к разрешённой выборке. Сортировка — по явному белому списку, со стабильным id как вторичным ключом.
- Для tasks разрешены фильтры projectId, status, dueFrom, dueTo; assigneeId — руководителю в области своих проектов. Неизвестный статус и обратный диапазон дат дают 400. Исполнитель фильтрует только свои задачи; назначение себе фильтром не даёт права на чужие задачи.
- Командные DTO не принимают автора, руководителя, createdAt, passwordHash или произвольное изменение projectId уже созданной задачи. Неизвестные поля отклоняются.
- Нет публичной регистрации, физического удаления проектов/задач, редактирования комментариев/времени или массовой записи без описанного контракта.

## Чтение

| Метод и путь | Участник / область | Результат |
| --- | --- | --- |
| GET /api/me | Любой вошедший | CurrentUserDto. |
| GET /api/users | Администратор | Страница UserDto без пароля и хеша. |
| GET /api/users/{id} | Администратор | UserDto с roles, active, version. |
| GET /api/assignable-users | Руководитель | Страница активных пользователей с ролью EXECUTOR: только id, name. При передаче projectId своего проекта уже включённые участники исключаются из списка. |
| GET /api/reference-data | Любой вошедший | Типы и приоритеты с id, name, active, version; позволяет отобразить прежнее неактивное значение. |
| GET /api/task-types/{id}, GET /api/task-priorities/{id} | Администратор | Текущие id, name, active и version для обновления формы. |
| GET /api/projects | Руководитель: свои; исполнитель: участие | Страница ProjectSummaryDto: id, name, description, managerId, version; без чужих задач и сводных показателей. |
| GET /api/projects/{id} | Руководитель своего проекта / участник | ProjectSummaryDto с теми же ограничениями. |
| GET /api/projects/{id}/members | Руководитель своего проекта | Страница участников с id пользователя и name. Поле projectVersion соответствует тому же снимку данных, что и состав. При assignableOnly=true возвращает только активных исполнителей. |
| GET /api/tasks | Руководитель / исполнитель | Страница TaskSummaryDto: id, projectId, projectName, title, dueDate, status, assigneeId, assigneeName, version. Порядок по сроку, затем по id. |
| GET /api/tasks/filter-options | Руководитель / исполнитель | Проекты доступных задач и, для руководителя, исполнители задач его проектов. Варианты содержат только id и name. |
| GET /api/tasks/{id} | Руководитель проекта / текущий исполнитель | TaskDetailDto: данные задачи, доступные имена ссылочных объектов, version; passwordHash и административные данные отсутствуют. |
| GET /api/tasks/{id}/comments | Те же права, что у карточки | Страница CommentDto: id, authorId, authorName, body, createdAt; порядок createdAt, id. |
| GET /api/tasks/{id}/comments/{commentId} | Те же права; комментарий относится к указанной задаче | CommentDto. |
| GET /api/tasks/{id}/time-entries | Те же права, что у карточки | Страница TimeEntryDto: id, authorId, authorName, workDate, minutes, createdAt. |
| GET /api/tasks/{id}/time-entries/{entryId} | Те же права; запись относится к указанной задаче | TimeEntryDto. |
| GET /api/projects/{id}/report | Руководитель своего проекта | ReportDto: generatedAt, project, byStatus, overdueCount, progressPercent, totalMinutes, tasksByAssignee, minutesByAuthor. |
| GET /api/projects/{id}/report.xlsx | Руководитель своего проекта | Файл .xlsx с теми же показателями по согласованному снимку. |

Карточка доступна для чтения после закрытия. Исполнитель видит исходные комментарии и записи времени назначенной ему задачи, в том числе созданные прежним исполнителем. После переназначения доступ прежнего исполнителя прекращается, если нет других оснований по матрице прав.

## Команды

Создание самостоятельного объекта, комментария или записи времени возвращает 201 и Location созданного ресурса; изменение, изменение состава проекта или отсутствие фактического изменения — 200. Ответ команды — MutationResponse с data и notificationStatus: NOT_REQUIRED, SENT или FAILED; при FAILED присутствует warning. В data находятся версия изменённой карточки и, для дочерних записей, taskVersion либо projectVersion. Пароли в data не возвращаются.

| Метод и путь | Входной DTO | Полномочия и результат |
| --- | --- | --- |
| POST /api/users | name, email, initialPassword, roles | Администратор; создаёт активную запись с непустыми ролями. |
| PUT /api/users/{id} | name, email, roles, active, expectedVersion | Администратор; изменяет данные, роли или блокировку с проверкой BR-03. Пароль через этот DTO не меняется. |
| POST /api/task-types | name | Администратор; новое активное значение. |
| PUT /api/task-types/{id} | name, active, expectedVersion | Администратор; переименование, деактивация или повторная активация. |
| POST /api/task-priorities | name | Администратор; новое активное значение. |
| PUT /api/task-priorities/{id} | name, active, expectedVersion | Администратор; переименование, деактивация или повторная активация. |
| POST /api/projects | name, description | Руководитель; managerId определяется по текущему пользователю. |
| PUT /api/projects/{id} | name, description, expectedVersion | Руководитель своего проекта; обновляет сведения. |
| POST /api/projects/{id}/members | userId, expectedProjectVersion | Руководитель; активный исполнитель, уникальное участие; увеличивается версия проекта; 200 с новой projectVersion. |
| DELETE /api/projects/{id}/members/{userId}?expectedProjectVersion=N | Версия проекта в query | Руководитель; проверка отсутствия незавершённых задач; 200 с обновлённой версией проекта. |
| POST /api/projects/{id}/tasks | title, description, assigneeId, dueDate, typeId, priorityId | Руководитель проекта; автор и NEW задаются сервером; после commit уведомление. |
| PUT /api/tasks/{id} | title, description, assigneeId, dueDate, typeId, priorityId, expectedVersion | Руководитель проекта; только незакрытая задача. Статус и проект этим DTO не меняются. |
| POST /api/tasks/{id}/status | targetStatus, expectedVersion; assigneeId только при повторном открытии | Переходы по разделу 1.4 ПЗ; при открытии назначение и NEW атомарны. |
| POST /api/tasks/{id}/comments | body, expectedTaskVersion | Руководитель проекта или текущий исполнитель; незакрытая задача, автор определяется сервером; data содержит CommentDto и новую taskVersion. |
| POST /api/tasks/{id}/time-entries | workDate, minutes, expectedTaskVersion | Текущий исполнитель с ролью EXECUTOR; data содержит TimeEntryDto и новую taskVersion. |

У assigneeId в команде статуса нет значения «снять назначение»: null означает отсутствие нового выбора, тогда используется и проверяется прежний исполнитель. В иных переходах переданный assigneeId отклоняется с 400, а не молча игнорируется. Полное редактирование задачи сохраняет прежнее неактивное значение справочника по BR-08, но новое назначение такого значения запрещает.

Пустое description допустимо; name/title и body после проверки не должны быть пустыми. Значения нормализуются до проверки уникальности: email — trim и lower, названия справочников — trim с регистронезависимой уникальностью. Полные ограничения длин определены физической моделью. minutes принимается как строго целое положительное число, без округления дробного JSON. workDate не позже текущей даты в зоне приложения; просроченная задача сохраняется и не удаляется автоматически.

### Пример ответа после сбоя SMTP

POST /api/projects/7/tasks успешно сохранил задачу, но письмо не отправлено. HTTP 201 и Location: /api/tasks/42:

```json
{
  "data": { "id": 42, "status": "NEW", "version": 0 },
  "notificationStatus": "FAILED",
  "warning": "Задача сохранена. Не удалось отправить уведомление исполнителю."
}
```

Это сокращённый пример TaskDetailDto. Клиент не повторяет создание задачи при notificationStatus=FAILED. SENT означает принятие письма SMTP-сервером; фактическая доставка подтверждается отдельно.

## Ошибки

ErrorResponse: code, message, fieldErrors (объект поле → список сообщений), requestId. Значения паролей, SQL, стек и содержимое чужих объектов не включаются. Ответы ошибок безопасности фильтров используют тот же формат.

| HTTP | Код / ситуация |
| --- | --- |
| 400 | VALIDATION_ERROR: некорректный JSON, поля, формат, enum, версия, дробные минуты, неизвестное поле. |
| 401 | AUTHENTICATION_REQUIRED: отсутствует действующая сессия либо запись заблокирована; INVALID_CREDENTIALS: отказ входа. |
| 403 | ACCESS_DENIED: действие запрещено ролью; CSRF_INVALID: отсутствует или неверен токен. |
| 404 | NOT_FOUND: объект отсутствует или недоступен текущему пользователю; его существование не раскрывается. |
| 409 | STALE_VERSION: карточка устарела; RULE_CONFLICT: запрещённый переход, закрытая задача, недействующее назначение, отзыв роли с задачами; DUPLICATE_VALUE: email, название или участие уже существуют; RETRY_REQUIRED: конфликт блокировок. |
| 500 | INTERNAL_ERROR: безопасное сообщение и requestId для поиска в журнале. |

Если запрос одновременно нарушает несколько условий, ответ зависит от первого защитного этапа: например, неверный CSRF может дать 403 до проверки контроллера. API не перенаправляет неаутентифицированный JSON-запрос на HTML-страницу. При неверном пароле POST /api/session отвечает 401 INVALID_CREDENTIALS. React показывает сообщение и очищает поле пароля. Успешные команды повторно автоматически не отправляются.

```json
{
  "code": "STALE_VERSION",
  "message": "Данные изменились. Обновите карточку и повторите действие.",
  "fieldErrors": {},
  "requestId": "example-request-id"
}
```

## Связь маршрутов со сценариями

| Сценарии | Маршруты |
| --- | --- |
| UC-01 | /login (React), /api/session, /api/session/logout, /api/csrf, /api/me |
| UC-02 | /api/users |
| UC-03 | /api/reference-data, /api/task-types, /api/task-priorities |
| UC-04, UC-05 | /api/projects, /api/projects/{id}/members, /api/assignable-users |
| UC-06, UC-07 | /api/projects/{id}/tasks, /api/tasks/{id} |
| UC-08 | /api/tasks и дочерние списки комментариев/времени |
| UC-09 | /api/tasks/{id}/status |
| UC-10, UC-11 | /api/tasks/{id}/comments, /api/tasks/{id}/time-entries |
| UC-12, UC-13 | /api/projects/{id}/report и /report.xlsx |
| UC-14 | Результат команды назначения / смены срока / открытия с переназначением; отдельной кнопки отправки или публичного маршрута нет. |
