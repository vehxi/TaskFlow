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

Testcontainers создаёт отдельную PostgreSQL для тестов. Проверяются миграции и JPA-отображение, системные роли, права подключения, ограничения данных, сохранение авторства при переназначении, вход и выход, CSRF, блокировка открытой сессии, отзыв роли и инициализация администратора. Без доступного Docker эти тесты пропускаются. Отчёт находится в `build/reports/tests/test/index.html`.

## Автономные проверки сервера

Для запуска проверок, не требующих PostgreSQL и Docker, выполните:

**macOS / Linux:**

```bash
./gradlew test --tests '*AuthenticationTests' --tests '*SessionWebTests' --tests '*LocalEnvironmentTests' --tests '*BootstrapAdminTests' --tests '*UserAdministrationTests' --tests '*UserApiTests'
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat test --tests '*AuthenticationTests' --tests '*SessionWebTests' --tests '*LocalEnvironmentTests' --tests '*BootstrapAdminTests' --tests '*UserAdministrationTests' --tests '*UserApiTests'
```

Проверяются нормализация email, пароль, блокировка пользователя, обновление ролей и завершение открытой сессии, вход без перенаправлений, CSRF и HTTP-ответы API. Проверки управления пользователями охватывают создание, версии карточек, запреты отзыва ролей, непустой набор ролей и отсутствие паролей в DTO. Отдельно проверяются чтение `.env` в UTF-8 и отсутствие учётных данных в описании bootstrap-конфигурации.

## Проверки и сборка React

Команды одинаковы для macOS, Linux и Windows PowerShell:

```shell
npm --prefix frontend install
npm --prefix frontend test
npm --prefix frontend run check
npm --prefix frontend run build
```

При наличии lockfile вместо `install` используйте `ci`. Автономные проверки HTTP-клиента используют встроенный тестовый раннер Node.js: CSRF, сессионные ошибки, отсутствие повторов команд и неожиданные ответы прокси. `check` проверяет TypeScript, `build` — типы и производственную сборку Vite. Результат — `frontend/dist`.

Для проверки экранов запустите оба процесса по [инструкции запуска](RUNNING.md): проверьте вход и выход, создание пользователя, изменение ролей и блокировку, ошибки email и версии. Проверьте ширины 375 и 1440 CSS-пикселей, переходы с клавиатуры и обновление страницы по прямому адресу карточки. Эти проверки интерфейса не заменяют серверные проверки доступа.

## Сборка исполняемого JAR

Сначала установите зависимости и соберите React командами из предыдущего раздела. Gradle включает готовый `frontend/dist` в статические ресурсы JAR; при отсутствии `index.html` сборка JAR завершится сообщением о необходимой npm-сборке.

**macOS / Linux:**

```bash
./gradlew clean bootJar
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat clean bootJar
```

Исполняемый файл — `build/libs/taskflow-0.1.0.jar`. Порядок его запуска приведён в [инструкции запуска](RUNNING.md#запуск-jar).

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
