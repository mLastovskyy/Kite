package app.kite.core.rules

/**
 * Apps a child must always be able to reach, on top of the device essentials the child app
 * resolves at runtime (dialer, default SMS, contacts, camera, clock, Settings, launcher, file
 * manager). The owner's rule (04.09.2026): «мессенджеры и звонки должны обязательно оставаться
 * и не блокироваться, камера и файлы тоже» — a limit or a schedule closes games and video,
 * never the way to call home, take a photo or open a file.
 *
 * Both apps read these lists: the child never blocks them (on top of what it resolves from
 * the system), the parent shows them as «Всегда доступно» and does not offer them for a
 * schedule or a block. Package names are the well-known ones per vendor; the runtime lookup
 * on the child covers whatever else a phone ships with.
 */
object Essentials {
    val MESSENGER_PACKAGES: Set<String> =
        setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "ru.oneme.app", // MAX
            "com.viber.voip",
            "org.thoughtcrime.securesms", // Signal
            "com.skype.raider",
            "com.google.android.apps.messaging",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.mms",
        )

    val CAMERA_PACKAGES: Set<String> =
        setOf(
            "com.android.camera",
            "com.android.camera2",
            "com.google.android.GoogleCamera",
            "com.huawei.camera",
            "com.sec.android.app.camera",
            "com.oppo.camera",
            "com.oplus.camera",
            "com.vivo.camera",
            "com.motorola.camera2",
            "com.motorola.camera3",
            "com.sonyericsson.android.camera",
            "com.asus.camera",
            "com.hmdglobal.camera2",
        )

    val FILES_PACKAGES: Set<String> =
        setOf(
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.google.android.apps.nbu.files", // Files by Google
            "com.huawei.filemanager",
            "com.huawei.hidisk",
            "com.sec.android.app.myfiles",
            "com.mi.android.globalFileexplorer",
            "com.android.fileexplorer", // Xiaomi (China ROM)
            "com.coloros.filemanager",
            "com.oplus.filemanager",
            "com.vivo.filemanager",
            "com.android.filemanager", // Vivo
            "com.motorola.filemanager",
        )

    val OWN_PACKAGES: Set<String> = setOf("app.kite.parent", "app.kite.child")

    fun isOwnApp(packageName: String): Boolean = packageName in OWN_PACKAGES

    fun isMessenger(packageName: String): Boolean = packageName in MESSENGER_PACKAGES

    fun isCamera(packageName: String): Boolean = packageName in CAMERA_PACKAGES

    fun isFiles(packageName: String): Boolean = packageName in FILES_PACKAGES

    /** Messenger, dialer, camera or file manager — never blockable, never selectable for a rule. */
    fun isEssential(packageName: String): Boolean =
        isOwnApp(packageName) || isMessenger(packageName) || isCamera(packageName) || isFiles(packageName)

    /** Short Russian tag for the parent's lists («Связь», «Камера», «Файлы»); null for ordinary apps. */
    fun essentialLabel(packageName: String): String? = when {
        isOwnApp(packageName) -> "Kite"
        isMessenger(packageName) -> "Связь"
        isCamera(packageName) -> "Камера"
        isFiles(packageName) -> "Файлы"
        else -> null
    }
}
