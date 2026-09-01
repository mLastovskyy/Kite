> **Устарело (2026-09-02).** Письма Auth больше не шлёт GoTrue: регистрация и сброс пароля
> идут 6-значными кодами через Edge Function `supabase/functions/auth-email/index.ts`
> (Gmail SMTP). Шаблоны ниже и SMTP-настройка GoTrue не используются; оставлены для истории.

# Kite — фирменные письма Auth через Gmail SMTP

Красивые HTML-письма лежат рядом: `confirm-signup.html`, `reset-password.html`.
Чёрно-синяя шапка со змеем, iOS-типографика, одна кнопка. Переменные GoTrue:
`{{ .ConfirmationURL }}`, `{{ .Email }}`.

## Почему нельзя «просто положить пароль»

Письма подтверждения и сброса отправляет сервис Supabase Auth (GoTrue), а не приложение.
Пока в **конфиге Auth** не включён кастомный SMTP, GoTrue шлёт через встроенный почтовик
(лимит ~2 письма/час → `429 email rate limit exceeded`). Ни строка в приложении, ни запись
в таблицу это не меняют — нужен один переключатель в настройках Auth.

**App-пароль Gmail НЕ хранится в этом репозитории и не попадает в APK** (иначе его можно
вытащить из сборки и слать почту от вашего имени). Его место — только серверная SMTP-настройка
Supabase, куда он передаётся один раз.

## Вариант 1 — я включаю всё сам (нужен токен)

Дайте **Supabase Personal Access Token** (https://supabase.com/dashboard/account/tokens).
Я через Management API одним заходом:
- включу кастомный SMTP на `smtp.gmail.com:465`, user `lastovskyfictitious@gmail.com`,
  sender name «Kite»;
- вставлю оба HTML-шаблона (подтверждение + сброс) и русские темы писем;
- подниму лимиты писем.

Токен положу в `local.properties` (gitignored) как `kite.supabase.accessToken`. Пароль Gmail
уйдёт только в поле SMTP-конфига Supabase.

Готовый вызов (запускается с токеном и паролем из окружения, ничего не хардкодится):

```bash
# PROJECT_REF=vtssuebmmimvnywmhpsh
curl -s -X PATCH "https://api.supabase.com/v1/projects/$PROJECT_REF/config/auth" \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"smtp_admin_email\": \"lastovskyfictitious@gmail.com\",
    \"smtp_host\": \"smtp.gmail.com\",
    \"smtp_port\": 465,
    \"smtp_user\": \"lastovskyfictitious@gmail.com\",
    \"smtp_pass\": \"$GMAIL_APP_PASSWORD\",
    \"smtp_sender_name\": \"Kite\",
    \"mailer_subjects_confirmation\": \"Подтвердите почту в Kite\",
    \"mailer_subjects_recovery\": \"Сброс пароля в Kite\",
    \"rate_limit_email_sent\": 30
  }"
```

Темплейты (`mailer_templates_confirmation_content`, `mailer_templates_recovery_content`)
я передам тем же PATCH содержимым этих HTML-файлов.

## Вариант 2 — вы в дашборде (2 минуты)

Authentication → **SMTP Settings** → Enable custom SMTP:
- Host `smtp.gmail.com`, Port `465`
- Username / Sender email `lastovskyfictitious@gmail.com`
- Password — ваш app-пароль
- Sender name `Kite`

Authentication → **Rate Limits** → «Rate limit for sending emails» поднять (напр. 30/час).

Authentication → **Email Templates** → Confirm signup и Reset Password → вставить содержимое
`confirm-signup.html` и `reset-password.html`.
