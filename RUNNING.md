# Запуск приложения

Перед запуском выполните [подготовку окружения](SETUP.md): настройте PostgreSQL и файл `.env`. Все команды выполняются из корня проекта.

## Запуск через Gradle

**macOS / Linux:**

```bash
./gradlew bootRun
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat bootRun
```

Flyway создаст таблицы и назначит права подключения приложения. При следующих запусках он проверит применённые миграции и выполнит новые.

После сообщения `Started TaskFlowApplication` откройте [http://localhost:8080](http://localhost:8080). В стартовой конфигурации доступна стандартная форма Spring Security: имя пользователя — `user`, пароль выводится при запуске после `Using generated security password`. Эти данные относятся к веб-входу; учётные записи PostgreSQL используются только для подключения к БД.

Сервер работает, пока команда `bootRun` выполняется. Остановка — `Ctrl+C`.

Для проверки созданных системных ролей выполните команду в терминале macOS, Linux или Windows PowerShell:

```shell
psql -h localhost -U task_tracker_app -d task_tracker -W -c "SELECT code FROM roles ORDER BY code;"
```

Ожидаются три значения: `ADMIN`, `EXECUTOR`, `MANAGER`.

## Запуск из IntelliJ IDEA

1. Откройте `build.gradle` как Gradle-проект.
2. Выберите JDK 21 для проекта и Gradle JVM.
3. Дождитесь импорта зависимостей.
4. Запустите `TaskFlowApplication` или Gradle-задачу `bootRun`.

Рабочий каталог конфигурации запуска — корень проекта, где находится `.env`.

## Запуск JAR

Сначала выполните [сборку исполняемого JAR](TESTING.md#сборка-исполняемого-jar).

Выполните команду из корня проекта с JDK 21:

**macOS / Linux:**

```bash
java -jar build/libs/taskflow-0.1.0.jar
```

**Windows (PowerShell):**

```powershell
java -jar .\build\libs\taskflow-0.1.0.jar
```

Следующий этап — [тестирование и сборка](TESTING.md).

[README](README.md) · [Подготовка окружения](SETUP.md).
