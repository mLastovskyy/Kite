# Kids360 parity — teardown and mapping to Kite

Kids360 (parent app «Kids360», child app «Alli360», Findmykids team) is the reference product.
This file records what Kids360 does, how its screens are laid out, and what Kite does about
each point. When a control's placement or a flow's staging is in question, follow this file.
Sources: kids360.app, the Kids360 help center (115 articles + screenshots), store listings,
independent reviews (2025–2026). Reconstructed without a device; uncertain points are marked.

Colour and typography stay ours (iOS-like, `DESIGN_SYSTEM.md`); we copy structure, not skin.

---

## 1. Account model

| Kids360 | Kite |
|---|---|
| Parent starts without an account (device-bound); e-mail/phone saved later from «Ещё → Мой аккаунт», only to restore access on another device | Same. «Начать» → anonymous session → family. «Ещё → Аккаунт → Привязать email» (6-digit code, our Edge Function). «У меня есть аккаунт» on the welcome screen is the other phone |
| Child device never has an account; linked by code / install link | Same (anonymous session + `redeem_pairing`) |
| Second parent joins via invite link, free | Same via `invite_parent` code |
| Sign-out is rare; no confirmation documented | Always confirm; anonymous session warns that the family is unreachable afterwards |

## 2. Parent onboarding and adding a child

Kids360: role choice → «Настроить телефон ребёнка» checklist (✓ Kids360 installed, ○ set up
the child's phone) → pairing screen: primary **«Отправить ссылку ребёнку»** (share sheet with
the install link), secondary **«Другой способ»** → numeric code. Child's name is entered on
the child's phone; default name = device model. Paywall after connection (not for us).

Kite: profile + family → notifications → PIN offer (existing). «Добавить ребёнка» opens the
staged setup: **1** install Kite Jr on the child's phone («Отправить ссылку» shares the APK /
store link together with the code) → **2** code + QR (QR stays: CLAUDE.md pairing rule) →
**3** waiting for the device («Ждём подключения…», auto-advances when the member appears).

## 3. Child onboarding (Alli360 → Kite Jr)

Alli360: segmented progress bar from the very first screen · code entry («Введите код,
полученный в приложении родителя», «Где взять код?») · name + age · privacy card · one
permission per screen with heading, why, numbered vendor-specific steps, **estimated time
remaining** («2 мин 20 сек»), «Перейти в настройки», auto-advance · order: usage access →
accessibility → overlay → vendor extras (autostart, background, pin in recents) → device admin
(last) → location · parent PIN set on the child device · afterwards «Проверить настройки»
list and a red «Настрой телефон» banner when anything breaks.

Kite Jr (target): **Stage 1** code (or QR) → **Stage 2** name + avatar → **Stage 3** consent
(mandatory, names the parent; CLAUDE.md) → **Stage 4** permission wizard, one per screen,
progress bar + «Шаг N из M» + «≈ N мин осталось», auto-advance in onResume (existing order
from CLAUDE.md: notifications → usage → overlay → location → background location →
accessibility → battery → autostart → device admin). «Здоровье защиты» = «Проверить
настройки». Offline parent code (TOTP) plays the role of Kids360's 4-digit parent PIN.

## 4. Parent app structure

Kids360 tab bar: **Главная · История · Задания · Карта · Ещё** (red dots for pending items).
Header of Главная = child avatar + name › (switcher). Card order on Главная:

1. Hero «Лимит на развлечения»: top-3 apps today, progress «00:24 / 02:00», «Изменить лимит»,
   big **«Заблокировать сейчас»** (toggles to «Разблокировать»).
2. Promo (n/a).
3. «Лимит на приложение» → «Добавить приложение».
4. «Доступны всегда» [N] › — used today, top apps.
5. «Всегда заблокированы» → «Добавить приложения».
6. «Блокировать по расписанию» › — rows «Учёба 08:00–16:00», «Сон 21:00–07:00».
7. «Местоположение ребёнка» › — map thumbnail.

«Ещё»: Семья (Мои дети + Взрослые, «Добавить ребёнка», «Добавить взрослого»), Мой аккаунт,
ПИН-код, Подписка (n/a), Получить помощь, Предложить идею, Поделиться приложением, version.

Kite tab bar: **Главная · Статистика · Задания · Карта · Ещё**. Главная = child switcher +
the same card order (hero limit card with «Заблокировать сейчас»; pending child requests
card; «Лимит на приложение»; «Доступны всегда»; «Всегда заблокированы»; «Расписание»;
«Где ребёнок»; «Найти телефон» and «Код подтверждения» as small rows). Статистика =
existing day/week screen. Ещё = Семья, Аккаунт, Код входа, Уведомления, Внешний вид,
Обновления, Поделиться приложением, версия.

## 5. Features

| # | Kids360 | Kite decision |
|---|---|---|
| Daily limit | One *entertainment* pool; «Лимиты времени» screen with seven weekday rows (toggle + hours, 0–8 h in 1 h steps); progress «used / limit» | «Лимит на день»: seven weekday rows with a switch; tap a day → sheet with an hours/minutes drum (15-min steps, our own `WheelPicker`) and «Для всех дней». Progress = quiet 4dp capsule in the hero, the numbers roll (`RollingText`). Pool = every app not «Доступно всегда»; essentials never count |
| Schedules | Named cards «Сон» 21:00–07:00 all days, «Учёба» 08:00–16:00 Пн–Пт, toggle, day chips; «Добавить расписание» (name, start, end, days). Blocks the whole pool | Same model (`QuietInterval`); list = icon card + switch per schedule; editor = name, two clock drums «Начало»/«Конец» side by side, day chips. Presets «Сон»/«Учёба» offered on first open |
| App lists | Every app in exactly one of: «Контроль времени» / «Доступны всегда» / «Всегда заблокированы»; tap a row → sheet «Переместить в:»; search; age badge | ONE list of every app on the phone (`child_apps` inventory published by the child, ∪ usage ∪ rules), switch per row = allowed/blocked, filter Все / С лимитом / Запрещены, search; tap → sheet «Разрешено / Доступно всегда / Свой лимит (drum)». The three Kids360 lists survive as the rule model (`AppRule`), not as tabs |
| Per-app limit | «Добавить приложение» → hh:mm drum (10-min steps) → «Установить лимит»; nested inside the pool | `AppRule.dailyLimitMinutes` on a drum (15-min steps) inside the app's sheet; also reachable from Статистика → app → «Лимит на это приложение» |
| Statistics | Hourly bars + per-app list; День/Неделя/Месяц | Existing День/Неделя; month later |
| Tasks | Parent-made «Ваши задания»: title (+ suggestion chips), reward +5/+10/+15/+20/+30/+40 мин, repeat by weekday; child marks done → push → «Ребёнок выполнил задание … [Отклонить] [Подтвердить]»; «Выполнено сегодня: 1 из 2»; child can «Попросить задание». Built-in puzzles/exercises (+5 мин auto, AI rep counting) | Parent writes the task text (no suggestion chips — owner), picks only the reward; «Повторять» is one row that unfolds day chips. Confirmation = full-width «Подтвердить» + plain «Отклонить» (half-width pairs clipped Russian). **No built-in puzzles or exercise counting** — owner 2026-09-03 |
| Extra time | Mostly through tasks; newer builds have «Request 15 more minutes» | Keep «Попросить ещё время» (existing approvals) and add tasks next to it |
| Instant lock | «Заблокировать сейчас» blocks the pool only; always-available apps and phone keep working | Remote lock now spares the same essentials + «Доступны всегда» (was: dialer only). `lockNow()` still turns the screen off once |
| Block screen | Full-screen overlay «Приложения заблокированы», mascot, «Интересный факт», ×; tasks live in the child app home | Overlay: reason, time used today, **«Задания»** button (opens Kite Jr), «Попросить ещё время», «На главный экран» |
| Child home | Hero «Осталось времени на сегодня: 1 ч 54 мин / 2 ч», mascot bubble, «Задания от родителя» grid, «Попросить задание», ⋮ menu: Проверить настройки, Удалить приложение, «Данные отправлены 17:22», version | Same layout in our warm palette: remaining-time hero, tasks list with «Выполнил», «Попросить задание», rows: Здоровье защиты, Что видит родитель, Удалить приложение (parent code), sync status, version |
| Location | Map tab, pin + accuracy, places (geofence), routes, «Суперсигнал» | One calm map style (OpenFreeMap positron, no switcher); two floating round buttons over the map — open in Google / Яндекс / the phone's maps (`geo:`), refresh; address + «battery · freshness · accuracy» line. Child reports every 5 min (battery). **Places** (radius check on device, offline) and **Routes** (thinned trail, 7 days) as before. «Найти телефон» = «Суперсигнал» |
| Web filter / history | Android: browser + YouTube *history* only; blocking on iOS via MDM | **Not doing** (owner, 2026-09-03): no browser/YouTube history |
| Uninstall protection | Device Admin + accessibility watchdog; Settings sections PIN-gated for 5 min; uninstall from inside the child app after PIN | Existing tier 1 (`UninstallGuard`, parent TOTP code, 10 min). Add «Удалить приложение» row in Kite Jr → same code flow |
| Notifications | usage nudges, limit reached, task done, task requested, permission changed, PIN compromised, place enter/exit, phone not set up | Have: requests/alerts channels, protection-broken. Add: task done / task requested (push via `send-push`) |
| Other | Громкий сигнал, star counter, facts, support chat, paywall | Have «Найти телефон». No stars/facts/paywall |

## 6. Copy conventions taken from Kids360

Short, second person, calm for parents («Установите отдельный лимит для конкретного
приложения»), casual imperative for the child («Выполняй задания и зарабатывай время»).
Titles are nouns («Лимит на приложение», «Доступны всегда», «Всегда заблокированы»,
«Задания», «Расписание»). Buttons are verbs («Заблокировать сейчас», «Изменить лимит»,
«Добавить приложение», «Создать задание», «Подтвердить», «Отклонить», «Выполнил»,
«Попросить задание»).

## 7. Implementation status (M10, 2026-09-02)

1. Account optional + logout confirm + essentials never blocked — done.
2. Rules model: weekday limits, named schedules with days; enforcement tests — done.
3. Tasks: core remote, parent tab, child list, confirmation → `grant_time`, `task_request` —
   done in code. **Server migration `supabase/migrations/20260902_tasks.sql` not applied yet**
   (owner); until then task calls fail and the UI shows the empty state.
4. Child app (second session): staged pairing, wizard time estimate, new home with the
   remaining-time bar, «Моё время», block overlay with «Задания», remote lock exemptions,
   «Удалить приложение» via parent approval — done.
5. Parent app: tab bar, Главная cards with the used/limit bar, app lists with «Переместить в:»,
   weekday limits, schedules, Задания, Статистика on shared charts, «Ещё» with Семья, staged
   «Добавить ребёнка», map with avatar marker + address + style choice + «Открыть в…» — done.

Open: notifications for task done / task requested (push), places and routes on the map
(M7 remainder), month view in Статистика, biometric alternative to the PIN (new dependency —
ask the owner first).
