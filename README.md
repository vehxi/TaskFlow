# TaskFlow

Курсовой проект веб-приложения «Трекер задач» для организации работы команды: проектов, назначений, сроков и трудозатрат.

## Стек

Java 21, Spring Boot 4.1.2, Spring MVC, Thymeleaf, Spring Security, Spring Data JPA, PostgreSQL 16 и Flyway. Сборка — Gradle Wrapper 9.9.1.

## Требования

- JDK 21.
- Запущенная PostgreSQL 16; стандартный порт — `5432`.
- Git и доступ к интернету для первой загрузки Gradle и зависимостей.
- Docker для интеграционных тестов с Testcontainers.

Отдельная установка Gradle не требуется: используйте wrapper из репозитория.

## Локальный запуск

### 1. Получение проекта

```shell
git clone https://github.com/vehxi/task-tracker-spring.git
cd task-tracker-spring
```

Все дальнейшие команды выполняются из корня проекта.

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

### 3. Запуск приложения

**macOS / Linux:**

```bash
./gradlew bootRun
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat bootRun
```

Flyway применит миграции автоматически. После сообщения `Started TaskTrackerSpringApplication` откройте [http://localhost:8080](http://localhost:8080). Остановка сервера — `Ctrl+C`.

В стартовой конфигурации используется стандартный вход Spring Security: логин `user`, пароль выводится в консоль после `Using generated security password`. Учётные записи PostgreSQL используются для подключения к БД.

Подробнее о запуске из IntelliJ IDEA, запуске JAR и проверке миграций — в [инструкции по запуску приложения](RUNNING.md).

## Тесты и сборка

Для интеграционных тестов запустите Docker. Testcontainers создаёт отдельную PostgreSQL; без доступного Docker эти тесты пропускаются.

| Действие | macOS / Linux | Windows PowerShell |
| --- | --- | --- |
| Тестирование | `./gradlew test` | `.\gradlew.bat test` |
| Сборка JAR | `./gradlew bootJar` | `.\gradlew.bat bootJar` |
| Генерация Javadoc | `./gradlew javadoc` | `.\gradlew.bat javadoc` |

Исполняемый файл — `build/libs/task-tracker-spring-0.1.0.jar`. Отчёт тестов — `build/reports/tests/test/index.html`, Javadoc — `build/docs/javadoc/index.html`.

Пояснения к проверкам, сборке и результатам приведены в [инструкции по тестированию и сборке](TESTING.md).

## Лицензия

[MIT](LICENSE).
