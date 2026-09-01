package app.kite.child.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.family.PairingTokens
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera scanner for the pairing QR. Shows a plain-Russian disclosure BEFORE the system
 * camera dialog (Play prominent-disclosure rule). Fires [onFound] once with the extracted
 * pairing token; QR codes that are not a kite.app/j/ link are silently ignored so the
 * child keeps aiming. Cancel falls back to manual 6-digit code entry.
 */
@Composable
fun QrScanScreen(onFound: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var refused by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            refused = !ok
        }

    if (granted) {
        ScannerPreview(onFound = onFound, onCancel = onCancel)
    } else {
        CameraDisclosure(
            refused = refused,
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onCancel = onCancel,
        )
    }
}

@Composable
private fun CameraDisclosure(refused: Boolean, onRequest: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text(text = "Сканирование QR", style = typography.title1, color = colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Камера нужна только чтобы отсканировать код с телефона родителя. Кадры никуда не сохраняются.",
            style = typography.subhead,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (refused) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Без камеры можно ввести 6-значный код вручную.",
                style = typography.subhead,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(28.dp))
        AppButton(text = "Разрешить камеру", onClick = onRequest)
        Spacer(Modifier.height(8.dp))
        AppButton(text = "Ввести код вручную", style = AppButtonStyle.Plain, onClick = onCancel)
    }
}

@Composable
private fun ScannerPreview(onFound: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val typography = LocalAppTypography.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val controller =
        remember {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            LifecycleCameraController(context).apply {
                // Preview stays enabled implicitly; we only add analysis (no image capture).
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                setImageAnalysisAnalyzer(
                    analysisExecutor,
                    QrAnalyzer { token -> mainExecutor.execute { onFound(token) } },
                )
            }
        }

    DisposableEffect(Unit) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .safeContentPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Наведи на QR-код на телефоне родителя",
                style = typography.subhead,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            AppButton(text = "Ввести код вручную", style = AppButtonStyle.Plain, onClick = onCancel)
        }
    }
}

/**
 * Decodes QR codes from the camera's Y (luminance) plane with ZXing — no GMS barcode
 * libraries. Emits at most one token, then ignores further frames.
 */
private class QrAnalyzer(private val onToken: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader =
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
    private val done = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        image.use { proxy ->
            if (done.get()) return
            val plane = proxy.planes.firstOrNull() ?: return
            // YUV_420_888 guarantees a dense Y plane on virtually every device; if a vendor
            // reports a padded pixel stride anyway, skip the frame rather than mis-decode.
            if (plane.pixelStride != 1) return
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val source =
                PlanarYUVLuminanceSource(
                    data,
                    plane.rowStride,
                    proxy.height,
                    0,
                    0,
                    proxy.width,
                    proxy.height,
                    false,
                )
            val text =
                try {
                    reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
                } catch (_: NotFoundException) {
                    null
                } finally {
                    reader.reset()
                }
            val token = text?.let(PairingTokens::tokenFromDeepLink)
            if (token != null && done.compareAndSet(false, true)) onToken(token)
        }
    }
}
