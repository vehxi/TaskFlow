# TaskFlow

Курсовой проект веб-приложения «Трекер задач» для организации работы команды: проектов, назначений, сроков и трудозатрат.

[Краткий обзор курсовой](docs/project-overview.md) — назначение, что уже работает, сценарий демонстрации и ссылки на документацию.

## Стек

**Интерфейс:** React, TypeScript, Vite, CSS Modules.

**Сервер:** Java 21, Spring Boot 4.1.2, Spring MVC (REST API), Spring Security, Spring Data JPA, PostgreSQL 16 и Flyway. Сборка сервера — Gradle Wrapper 9.9.1; интерфейса — npm.

## Требования

- JDK 21.
- Node.js 24 LTS и npm для интерфейса.
- Запущенная PostgreSQL 16; стандартный порт — `5432`.
- Git и доступ к интернету для первой загрузки Gradle и зависимостей.
- Docker для интеграционных тестов с Testcontainers.

Отдельная установка Gradle не требуется: используйте wrapper из репозитория.

## Локальный запуск

### 1. Получение проекта

```shell
git clone https://github.com/vehxi/TaskFlow.git
cd TaskFlow
```

Все дальнейшие команды выполняются из корня проекта. React-клиент находится в `frontend/`, сервер — в `src/`.

### 2. Подготовка БД и конфигурации

Создайте БД `task_tracker` и две учётные записи PostgreSQL:

| Учётная запись | Назначение |
| --- | --- |
| `task_tracker_migrator` | Владелец БД; создание таблиц и назначение прав через Flyway. |
| `task_tracker_app` | Работа приложения с данными. |

Пошаговые команды установки и запуска PostgreSQL, создания БД и учётных записей приведены в [инструкции по подготовке окружения](SETUP.md#2-подготовка-postgresql).

Создайте `.env` на основе шаблона, сохранив существующий файл, если он уже настроен.

**macOS / Linux:**

```bash
cp -n .env.example .env
chmod 600 .env
```

**Windows (PowerShell):**

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

В `.env` укажите адрес БД, имена учётных записей и их пароли:

```properties
DB_URL=jdbc:postgresql://localhost:5432/task_tracker
DB_USERNAME=task_tracker_app
DB_PASSWORD=replace-with-application-password
DB_MIGRATION_USERNAME=task_tracker_migrator
DB_MIGRATION_PASSWORD=replace-with-migration-password
```

Приложение читает `.env` автоматически из корня проекта. Файл исключён из Git. Подробнее о формате и параметрах — в [инструкции по настройке подключения](SETUP.md#3-настройка-подключения).

### 3. Запуск сервера

**macOS / Linux:**

```bash
./gradlew bootRun
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat bootRun
```

Flyway применит миграции автоматически. После сообщения `Started TaskFlowApplication` API будет доступно на `http://localhost:8080`. Остановка сервера — `Ctrl+C`.

Для входа используйте email и пароль учётной записи приложения. Порядок создания первой записи приведён в [инструкции по созданию администратора](SETUP.md#4-создание-начального-администратора). Учётные записи PostgreSQL используются только для подключения к БД.

### 4. Запуск интерфейса

Откройте второй терминал. Команды одинаковы для macOS, Linux и Windows PowerShell:

```shell
npm --prefix frontend install
npm --prefix frontend run dev
```

Откройте [http://localhost:5173](http://localhost:5173). Vite передаёт запросы `/api` на Spring; оба процесса должны работать одновременно. Для входа используйте созданную учётную запись приложения. Первые экраны — вход, сведения о своей записи и управление пользователями для администратора.

Для подробного ознакомления с запуском из IntelliJ IDEA, настройкой двух процессов и запуском JAR обратитесь к [инструкции запуска](RUNNING.md).


## Тесты и сборка

Для интеграционных тестов запустите Docker. Testcontainers создаёт отдельную PostgreSQL; без доступного Docker эти тесты пропускаются.

| Действие | macOS / Linux | Windows PowerShell |
| --- | --- | --- |
| Тестирование | `./gradlew test` | `.\gradlew.bat test` |
| Сборка JAR после сборки интерфейса | `./gradlew clean bootJar` | `.\gradlew.bat clean bootJar` |
| Генерация Javadoc | `./gradlew javadoc` | `.\gradlew.bat javadoc` |

Для проверки и сборки интерфейса выполните на любой платформе:

```shell
npm --prefix frontend test
npm --prefix frontend run build
```

Готовый `frontend/dist` включается в JAR при сборке Gradle. В таком режиме отдельный Vite не требуется.

Исполняемый файл — `build/libs/taskflow-0.1.0.jar`. Отчёт тестов — `build/reports/tests/test/index.html`, Javadoc — `build/docs/javadoc/index.html`.

Пояснения к проверкам, сборке и результатам приведены в [инструкции по тестированию и сборке](TESTING.md).

## Лицензия

[MIT](LICENSE).
