package app.kite.core.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kite.core.R
import app.kite.core.design.LocalAppColors

/**
 * The product icon set: Lucide (ISC), shipped as VectorDrawables in `res/drawable/ic_lucide_*`
 * — no icon font, no dependency, identical on Huawei. Stroke icons only, 2px on a 24 grid,
 * so glyphs never cross or overlap the way hand-drawn Canvas shapes did. Tint at use.
 */
object KiteIcons {
    val House = R.drawable.ic_lucide_house
    val ChartColumn = R.drawable.ic_lucide_chart_column
    val ListChecks = R.drawable.ic_lucide_list_checks
    val MapPin = R.drawable.ic_lucide_map_pin
    val Map = R.drawable.ic_lucide_map
    val Ellipsis = R.drawable.ic_lucide_ellipsis
    val Users = R.drawable.ic_lucide_users
    val User = R.drawable.ic_lucide_user
    val Lock = R.drawable.ic_lucide_lock
    val LockOpen = R.drawable.ic_lucide_lock_open
    val Ban = R.drawable.ic_lucide_ban
    val Hourglass = R.drawable.ic_lucide_hourglass
    val CalendarClock = R.drawable.ic_lucide_calendar_clock
    val Clock = R.drawable.ic_lucide_clock
    val Timer = R.drawable.ic_lucide_timer
    val AlarmClock = R.drawable.ic_lucide_alarm_clock
    val Bell = R.drawable.ic_lucide_bell
    val BellRing = R.drawable.ic_lucide_bell_ring
    val Shield = R.drawable.ic_lucide_shield
    val ShieldCheck = R.drawable.ic_lucide_shield_check
    val Mail = R.drawable.ic_lucide_mail
    val KeyRound = R.drawable.ic_lucide_key_round
    val Moon = R.drawable.ic_lucide_moon
    val SunMoon = R.drawable.ic_lucide_sun_moon
    val BookOpen = R.drawable.ic_lucide_book_open
    val Plus = R.drawable.ic_lucide_plus
    val ChevronRight = R.drawable.ic_lucide_chevron_right
    val Camera = R.drawable.ic_lucide_camera
    val Image = R.drawable.ic_lucide_image
    val Smartphone = R.drawable.ic_lucide_smartphone
    val Share = R.drawable.ic_lucide_share_2
    val Refresh = R.drawable.ic_lucide_refresh_cw
    val Trash = R.drawable.ic_lucide_trash_2
    val Pencil = R.drawable.ic_lucide_pencil
    val X = R.drawable.ic_lucide_x
    val Check = R.drawable.ic_lucide_check
    val CircleCheck = R.drawable.ic_lucide_circle_check
    val CircleX = R.drawable.ic_lucide_circle_x
    val Search = R.drawable.ic_lucide_search
    val Settings = R.drawable.ic_lucide_settings
    val Eye = R.drawable.ic_lucide_eye
    val EyeOff = R.drawable.ic_lucide_eye_off
    val Battery = R.drawable.ic_lucide_battery
    val Phone = R.drawable.ic_lucide_phone
    val MessageSquare = R.drawable.ic_lucide_message_square
    val Palette = R.drawable.ic_lucide_palette
    val Download = R.drawable.ic_lucide_download
    val LogOut = R.drawable.ic_lucide_log_out
    val Info = R.drawable.ic_lucide_info
    val Gift = R.drawable.ic_lucide_gift
    val Sparkles = R.drawable.ic_lucide_sparkles
    val Send = R.drawable.ic_lucide_send
    val QrCode = R.drawable.ic_lucide_qr_code
    val Link = R.drawable.ic_lucide_link_2
    val Copy = R.drawable.ic_lucide_copy
    val Pause = R.drawable.ic_lucide_pause
    val Play = R.drawable.ic_lucide_play
}

/** A tinted Lucide glyph. Default size 22dp reads well next to 17sp body text. */
@Composable
fun AppIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, tint: Color = LocalAppColors.current.textSecondary, size: Dp = 22.dp) {
    Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/**
 * The 29dp rounded-square icon slot used in inset-list rows and cards (DESIGN_SYSTEM.md row
 * anatomy): a solid [background] with a white glyph.
 */
@Composable
fun IconTile(@DrawableRes icon: Int, background: Color, modifier: Modifier = Modifier, size: Dp = 29.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon = icon, tint = Color.White, size = size * 0.62f)
    }
}

/** [RowIcon] for [InsetGroupScope.row] built from a Lucide glyph. */
fun rowIcon(@DrawableRes icon: Int, background: Color): RowIcon =
    RowIcon(background) { AppIcon(icon = icon, tint = Color.White, size = 18.dp) }
