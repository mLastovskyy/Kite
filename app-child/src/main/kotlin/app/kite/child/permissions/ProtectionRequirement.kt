package app.kite.child.permissions

/**
 * Everything the child device must grant for full protection, in wizard order —
 * easiest to scariest (CLAUDE.md): starting with Device Admin loses half the users.
 * The VPN step is intentionally absent: the DNS filter (M8) is out of scope.
 *
 * Copy is deliberately terse (one short line) — the wizard leans on the icon and the
 * step layout, not paragraphs.
 */
enum class ProtectionRequirement(val title: String, val benefit: String, val settingsHint: String?) {
    NOTIFICATIONS(
        title = "Уведомления",
        benefit = "Запросы и важные сигналы.",
        settingsHint = null,
    ),
    USAGE_ACCESS(
        title = "Экранное время",
        benefit = "Читаем системную статистику приложений.",
        settingsHint = "Включите Kite Jr и вернитесь.",
    ),
    OVERLAY(
        title = "Поверх окон",
        benefit = "Экран паузы вместо игры.",
        settingsHint = "Разрешите показ поверх других окон.",
    ),
    LOCATION_FOREGROUND(
        title = "Геолокация",
        benefit = "Телефон на карте.",
        settingsHint = null,
    ),
    LOCATION_BACKGROUND(
        title = "Геолокация всегда",
        benefit = "Работает и в кармане.",
        settingsHint = null, // hint is built dynamically: the option label differs per device
    ),
    ACCESSIBILITY(
        title = "Спец. возможности",
        benefit = "Замечает открытое приложение.",
        settingsHint = "Включите «Kite Jr».",
    ),
    BATTERY(
        title = "Без энергосбережения",
        benefit = "Защита не засыпает.",
        settingsHint = null,
    ),
    VENDOR_AUTOSTART(
        title = "Автозапуск",
        benefit = "Иначе система закроет фон.",
        settingsHint = "Включите автозапуск Kite Jr.",
    ),
    DEVICE_ADMIN(
        title = "Администратор",
        benefit = "Нельзя удалить без родителя.",
        settingsHint = null,
    ),
}
