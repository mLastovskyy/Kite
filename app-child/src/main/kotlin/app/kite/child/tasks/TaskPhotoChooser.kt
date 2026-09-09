package app.kite.child.tasks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.kite.core.design.components.AppChoiceDialog
import app.kite.core.design.components.DialogChoice
import app.kite.core.tasks.TaskPhotosRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * «Сделать фото» / «Выбрать из галереи» for one task, then a downscaled JPEG in the child's
 * own files. The camera is the system camera app — an ACTION_IMAGE_CAPTURE intent needs no
 * CAMERA permission of ours, and the gallery goes through the photo picker, which needs no
 * storage permission either. Nothing here asks the child for a new permission.
 */
@Composable
fun TaskPhotoChooser(store: TasksStore, taskId: String, onPicked: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun keep(source: Uri) {
        busy = true
        scope.launch {
            val path = withContext(Dispatchers.IO) { compressInto(context, source, store.photoFile(taskId)) }
            busy = false
            if (path == null) onDismiss() else onPicked(path)
        }
    }

    val captureTarget = remember { captureUri(context) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) keep(captureTarget) else onDismiss()
        }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) keep(uri) else onDismiss()
        }

    if (busy) return
    AppChoiceDialog(
        title = "Фото к заданию",
        choices =
        listOf(
            DialogChoice(label = "Сделать фото") { camera.launch(captureTarget) },
            DialogChoice(label = "Выбрать из галереи") {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        ),
        onDismiss = onDismiss,
    )
}

/** The content URI the camera app is allowed to write its full-size frame into. */
private fun captureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "task_photos").apply { mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.updates", File(dir, "capture.jpg"))
}

/**
 * Reads [source] at a sane size, turns it the way it was taken and writes a JPEG to [target].
 * A camera frame is several megabytes and its EXIF rotation is the reason a photo of a tidy
 * room arrives sideways; both are settled here, once, before anything is uploaded.
 */
private fun compressInto(context: Context, source: Uri, target: File): String? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > TaskPhotosRemote.MAX_PX * 2) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded =
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
    val turned = decoded.turnedUpright(context, source)
    val scale = TaskPhotosRemote.MAX_PX.toFloat() / max(turned.width, turned.height)
    val out =
        if (scale >= 1f) {
            turned
        } else {
            Bitmap.createScaledBitmap(turned, (turned.width * scale).toInt(), (turned.height * scale).toInt(), true)
        }
    target.parentFile?.mkdirs()
    FileOutputStream(target).use { out.compress(Bitmap.CompressFormat.JPEG, TaskPhotosRemote.QUALITY, it) }
    target.absolutePath
}.getOrNull()

private fun Bitmap.turnedUpright(context: Context, source: Uri): Bitmap {
    val degrees =
        context.contentResolver.openInputStream(source)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    if (degrees == 0f) return this
    return Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)
}
