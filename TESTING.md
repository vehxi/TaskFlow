# Тестирование и сборка

Используйте JDK 21 и выполняйте команды из корня проекта. Инструкция по настройке инструментов приведена в [подготовке окружения](SETUP.md).

## Интеграционные тесты

Для интеграционных тестов запустите Docker и выполните:

**macOS / Linux:**

```bash
./gradlew test
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat test
```

Testcontainers создаёт отдельную PostgreSQL для тестов. Проверяются миграции, системные роли, права подключения, ограничения данных и сохранение авторства при переназначении. Без доступного Docker эти тесты пропускаются. Отчёт находится в `build/reports/tests/test/index.html`.

## Сборка исполняемого JAR

**macOS / Linux:**

```bash
./gradlew bootJar
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat bootJar
```

Исполняемый файл — `build/libs/task-tracker-spring-0.1.0.jar`. Порядок его запуска приведён в [инструкции запуска](RUNNING.md#запуск-jar).

## Генерация Javadoc

**macOS / Linux:**

```bash
./gradlew javadoc
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat javadoc
```

Результат — `build/docs/javadoc/index.html`.

[README](README.md) · [Запуск приложения](RUNNING.md).
