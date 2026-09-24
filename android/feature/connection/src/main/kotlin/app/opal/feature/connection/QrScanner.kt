package app.opal.feature.connection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.opal.core.designsystem.glass.GlassIconButton
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.RoundedRectangle
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zxingcpp.BarcodeReader

private fun qrReader() =
    BarcodeReader().apply { options.formats = setOf(BarcodeReader.Format.QR_CODE) }

/**
 * Full-screen camera scanner. Frames are decoded on a background thread by zxing-cpp; the first QR
 * code whose text contains bridge lines is handed to [onResult]. Nothing leaves the device.
 */
@Composable
internal fun QrScanner(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var failed by remember { mutableStateOf(false) }
    val currentOnResult by rememberUpdatedState(onResult)
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onClose,
    )

    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    LaunchedEffect(lifecycleOwner) {
        val provider =
            try {
                ProcessCameraProvider.awaitInstance(context)
            } catch (_: Exception) {
                failed = true
                return@LaunchedEffect
            }
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val reader = qrReader()
        var delivered = false
        val analysis =
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(executor) { image ->
                        image.use {
                            if (delivered) return@use
                            val text = runCatching {
                                reader.read(it)
                            }
                                .getOrNull()
                                ?.firstNotNullOfOrNull { r -> r.text?.takeIf(::containsBridge) }
                            if (text != null) {
                                delivered = true
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    currentOnResult(text)
                                }
                            }
                        }
                    }
                }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        } catch (_: Exception) {
            failed = true
        }
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            provider.unbindAll()
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        surfaceRequest?.let {
            CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.align(Alignment.Center)
                .size(240.dp)
                .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedRectangle(28.dp))
        )
        Text(
            stringResource(
                if (failed) R.string.bridges_camera_unavailable else R.string.bridges_scanner_hint
            ),
            style = OpalTheme.type.bodyStrong,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
        )
        GlassIconButton(
            OpalIcons.Close,
            contentDescription = stringResource(R.string.bridges_scanner_close),
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(16.dp),
        )
    }
}

internal fun containsBridge(text: String) =
    app.opal.core.model.bridge.BridgeLine.extractAll(text).isNotEmpty()

/** Decodes a QR code from a picked image (Photo Picker: no storage permission needed). */
internal suspend fun decodeQrFromImage(context: android.content.Context, uri: Uri): String? =
    withContext(Dispatchers.Default) {
        val bitmap = loadBitmap(context, uri) ?: return@withContext null
        try {
            qrReader()
                .apply {
                    options.tryHarder = true
                    options.tryRotate = true
                    options.tryInvert = true
                }
                .read(bitmap, android.graphics.Rect(0, 0, bitmap.width, bitmap.height), 0)
                .firstNotNullOfOrNull { it.text?.takeIf(::containsBridge) }
        } finally {
            bitmap.recycle()
        }
    }

private fun loadBitmap(context: android.content.Context, uri: Uri): Bitmap? =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) {
                decoder,
                info,
                _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_DECODE_PX) {
                    val scale = MAX_DECODE_PX.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt(),
                        (info.size.height * scale).toInt(),
                    )
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (_: Exception) {
        null
    }

private const val MAX_DECODE_PX = 2048
