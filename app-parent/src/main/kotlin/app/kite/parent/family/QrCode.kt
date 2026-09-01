package app.kite.parent.family

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR for the pairing deep link, encoded with ZXing (pure Java, GMS-free) and drawn module
 * by module on a Compose Canvas — no android Bitmap, so the same code runs on Huawei. The
 * QR carries ONLY the one-time token URL; nothing meaningful is embedded.
 */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = 220.dp, foreground: Color = Color.Black) {
    val matrix =
        remember(content) {
            runCatching {
                QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    0,
                    0,
                    mapOf(
                        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                        EncodeHintType.MARGIN to 1,
                    ),
                )
            }.getOrNull()
        }

    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        if (matrix != null) {
            Canvas(Modifier.size(size - 32.dp)) {
                val modules = matrix.width
                val cell = this.size.width / modules
                for (y in 0 until modules) {
                    for (x in 0 until modules) {
                        if (matrix.get(x, y)) {
                            drawRect(
                                color = foreground,
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell + 0.5f, cell + 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}
