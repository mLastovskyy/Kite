package app.kite.child.permissions

/**
 * Everything the child device must grant for full protection, in wizard order —
 * easiest to scariest (CLAUDE.md): starting with Device Admin loses half the users.
 * The VPN step is intentionally absent: the DNS filter (M8) is out of scope.
 */
enum class ProtectionRequirement(val title: String, val benefit: String, val settingsHint: String?) {
    NOTIFICATIONS(
        title = "Уведомления",
        benefit = "Запросы и предупреждения: например, просьба о дополнительном времени или сигнал, что защита нарушена.",
        settingsHint = null,
    ),
    USAGE_ACCESS(
        title = "Статистика экрана",
        benefit =
        "Телефон уже сам считает время в приложениях. Это разрешение позволяет Kite Jr " +
            "читать ту же системную статистику — ничего нового не измеряется.",
        settingsHint = "Откроется экран «Доступ к данным». Включите Kite Jr и вернитесь — приложение продолжит само.",
    ),
    OVERLAY(
        title = "Поверх других окон",
        benefit = "Спокойный экран паузы вместо игры, когда время закончилось.",
        settingsHint = "Откроется список приложений. Разрешите Kite Jr показ поверх других окон.",
    ),
    LOCATION_FOREGROUND(
        title = "Геолокация",
        benefit = "Родитель видит на карте, где телефон.",
        settingsHint = null,
    ),
    LOCATION_BACKGROUND(
        title = "Геолокация всегда",
        benefit = "Точка на карте обновляется и когда телефон в кармане, а не только с открытым приложением.",
        settingsHint = null, // hint is built dynamically: the option label differs per device
    ),
    ACCESSIBILITY(
        title = "Специальные возможности",
        benefit = "Kite Jr замечает, какое приложение сейчас открыто, и вовремя показывает экран паузы.",
        settingsHint = "Откроется список служб. Найдите «Kite Jr — родительский контроль» и включите.",
    ),
    BATTERY(
        title = "Без ограничений батареи",
        benefit = "Защита не засыпает, даже когда телефон экономит заряд.",
        settingsHint = null,
    ),
    VENDOR_AUTOSTART(
        title = "Автозапуск",
        benefit =
        "Производитель телефона агрессивно закрывает фоновые приложения. " +
            "Без автозапуска защита умирает через несколько часов.",
        settingsHint =
        "Найдите Kite Jr в списке и включите автозапуск " +
            "(на EMUI: Батарея → Запуск приложений → Kite Jr → все три переключателя).",
    ),
    DEVICE_ADMIN(
        title = "Администратор устройства",
        benefit = "Приложение нельзя удалить без разрешения родителя.",
        settingsHint = null,
    ),
}
