# Подготовка окружения

## Требования

- JDK 21.
- Node.js 24 LTS и npm для React-клиента.
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

Приложение читает `.env` автоматически из текущего каталога. Сохраните файл в UTF-8. Он использует формат `ИМЯ=значение`, без `export` и обрамляющих кавычек. `.env` исключён из Git; `.env.example` содержит только шаблон.

## 4. Создание начального администратора

Для первого входа добавьте в локальный `.env` следующие параметры и укажите собственные email и пароль:

```properties
APP_BOOTSTRAP_ENABLED=true
APP_BOOTSTRAP_NAME=Администратор
APP_BOOTSTRAP_EMAIL=admin@example.test
APP_BOOTSTRAP_PASSWORD=replace-with-your-password
```

Пароль не должен быть пустым или состоять только из пробелов; максимальная длина — 72 байта в UTF-8. Данные входа в веб-приложение задаются отдельно от паролей PostgreSQL.

При запуске приложение создаст активного пользователя с ролью `ADMIN`, если администраторов ещё нет. Пароль сохраняется в виде хеша. Повторный запуск не создаёт дубликат и не меняет существующие учётные записи; занятый email другой записи приводит к отказу инициализации.

После успешного первого запуска установите `APP_BOOTSTRAP_ENABLED=false` и очистите `APP_BOOTSTRAP_PASSWORD` в `.env`. Для последующих входов используйте ранее заданный пароль: изменение bootstrap-параметров не сбрасывает пароль существующей записи.

## 5. Подготовка React-клиента

Установите Node.js 24 LTS с npm с [официального сайта](https://nodejs.org/). Проверьте команды в терминале macOS, Linux или Windows PowerShell:

```shell
node --version
npm --version
npm --prefix frontend install
```

Файл `frontend/package.json` задаёт зависимости React, TypeScript и Vite. Сформированный `frontend/package-lock.json` следует сохранять в Git; каталог `node_modules` и сборка `frontend/dist` исключены. Если lockfile уже есть, используйте `npm --prefix frontend ci` для воспроизводимой установки.

Локальный `.env` в корне относится к серверу. Пароли БД и администратора не должны попадать в переменные `VITE_*` или исходный код React: эти значения доступны браузеру.

Следующий этап — [запуск сервера и интерфейса](RUNNING.md).

[README](README.md) · [Тестирование и сборка](TESTING.md).
