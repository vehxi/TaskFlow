# Подготовка окружения

## Требования

- JDK 21.
- Запущенная PostgreSQL 16; стандартный порт — `5432`.
- Git и доступ к интернету для первой загрузки Gradle и зависимостей.
- Docker для интеграционных тестов с Testcontainers.

## 1. Получение проекта

```bash
git clone https://github.com/vehxi/TaskFlow.git
cd TaskFlow
```

Все команды ниже выполняются из корня проекта. Для Windows приведены команды PowerShell; команды Git одинаковы для всех платформ.

## 2. Подготовка PostgreSQL

На macOS с установленной через Homebrew PostgreSQL 16 сервер запускается командой:

```bash
brew services start postgresql@16
```

На Windows после установки PostgreSQL 16 добавьте каталог утилит в `PATH` текущего окна PowerShell:

```powershell
$env:Path = "C:\Program Files\PostgreSQL\16\bin;$env:Path"
```

Если PostgreSQL установлен в другом каталоге, укажите соответствующий путь. Проверьте имя службы:

```powershell
Get-Service -Name "*postgres*"
```

Если служба остановлена, запустите PowerShell от имени администратора и выполните следующую команду, указав имя службы из результата проверки:

```powershell
Start-Service -Name "postgresql-x64-16"
```

Для новой базы создайте две учётные записи и БД. Команды выполняются от пользователя PostgreSQL с правами создания ролей и баз.

**macOS / Linux:**

```bash
createuser --pwprompt --no-superuser --no-createdb --no-createrole task_tracker_migrator
createuser --pwprompt --no-superuser --no-createdb --no-createrole task_tracker_app
createdb --owner=task_tracker_migrator --encoding=UTF8 task_tracker
```

**Windows (PowerShell):**

```powershell
createuser -h localhost -U postgres -W --pwprompt --no-superuser --no-createdb --no-createrole task_tracker_migrator
createuser -h localhost -U postgres -W --pwprompt --no-superuser --no-createdb --no-createrole task_tracker_app
createdb -h localhost -U postgres -W --owner=task_tracker_migrator --encoding=UTF8 task_tracker
```

`postgres` — административная учётная запись, созданная при установке сервера. Если используется другой администратор, замените имя после `-U`. В командах `createuser` задайте пароль новой роли и подтвердите его; при запросе пароля подключения введите пароль администратора PostgreSQL.

Сохраните пароли обеих учётных записей. `task_tracker_migrator` создаёт структуру и выдаёт права, `task_tracker_app` используется приложением для работы с данными. База должна поддерживать UTF-8 и регистронезависимое сравнение названий через `lower`, в том числе для кириллицы.

## 3. Настройка подключения

Создайте `.env` из шаблона. Следующие команды сохраняют существующий файл.

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
notepad .env
```

В `.env` заполните параметры:

| Параметр | Значение |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/task_tracker` |
| `DB_USERNAME` | `task_tracker_app` |
| `DB_PASSWORD` | Пароль учётной записи приложения |
| `DB_MIGRATION_USERNAME` | `task_tracker_migrator` |
| `DB_MIGRATION_PASSWORD` | Пароль учётной записи миграций |

Приложение читает `.env` автоматически из текущего каталога. Файл использует формат `ИМЯ=значение`, без `export` и обрамляющих кавычек. `.env` исключён из Git; `.env.example` содержит только шаблон.

Следующий этап — [запуск приложения](RUNNING.md).

[README](README.md) · [Тестирование и сборка](TESTING.md).
