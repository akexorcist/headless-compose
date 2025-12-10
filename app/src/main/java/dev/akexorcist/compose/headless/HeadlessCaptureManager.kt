package dev.akexorcist.compose.headless

import android.app.Presentation
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

suspend fun headlessCapture(
    context: Context,
    size: DpSize,
    density: Density = Density(density = 2f),
    content: @Composable () -> Unit,
): ImageBitmap = useVirtualDisplay(context) { display ->
    captureComposable(
        context = context,
        size = size,
        density = density,
        display = display
    ) {
        LaunchedEffect(Unit) {
            capture()
        }
        content()
    }
}

private suspend fun <T> useVirtualDisplay(context: Context, callback: suspend (display: Display) -> T): T {
    val texture = SurfaceTexture(false)
    val surface = Surface(texture)
    // Size of virtual display doesn't matter, because images are captured from compose, not the display surface.
    val virtualDisplay = context.getDisplayManager().createVirtualDisplay(
        "virtualDisplay", 1, 1, 72, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
    )

    val result = callback(virtualDisplay.display)

    virtualDisplay.release()
    surface.release()
    texture.release()

    return result
}

private data class CaptureComposableScope(val capture: () -> Unit)

/** Captures composable content, by default using a hidden window on the default display.
 *
 *  Be sure to invoke capture() within the composable content (e.g. in a LaunchedEffect) to perform the capture.
 *  This gives some level of control over when the capture occurs, so it's possible to wait for async resources */
private suspend fun captureComposable(
    context: Context,
    size: DpSize,
    density: Density,
    display: Display,
    content: @Composable CaptureComposableScope.() -> Unit,
): ImageBitmap {
    val presentation = Presentation(context.applicationContext, display).apply {
        window?.decorView?.let { view ->
            view.setViewTreeLifecycleOwner(ProcessLifecycleOwner.get())
            view.setViewTreeSavedStateRegistryOwner(EmptySavedStateRegistryOwner.shared)
            view.alpha = 0f
        }
    }

    val composeView = ComposeView(context).apply {
        val intSize = with(density) { size.toSize().roundedToIntSize() }
        require(intSize.width > 0 && intSize.height > 0) { "pixel size must not have zero dimension" }

        layoutParams = ViewGroup.LayoutParams(intSize.width, intSize.height)
    }

    presentation.setContentView(composeView, composeView.layoutParams)
    presentation.show()

    val imageBitmap = suspendCoroutine { continuation ->
        composeView.setContent {
            var shouldCapture by remember { mutableStateOf(false) }
            val graphicsLayer = rememberGraphicsLayer()

            // Handle the suspend call to toImageBitmap() in a coroutine
            LaunchedEffect(shouldCapture) {
                if (shouldCapture) {
                    val bitmap = graphicsLayer.toImageBitmap()
                    continuation.resumeWith(Result.success(bitmap))
                }
            }

            Box(
                modifier = Modifier
                    .size(size)
                    .thenIf(shouldCapture) {
                        drawWithContent {
                            // Record the content into the graphics layer
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            // Draw the graphics layer
                            drawLayer(graphicsLayer)
                        }
                    },
            ) {
                CaptureComposableScope(capture = { shouldCapture = true }).run {
                    content()
                }
            }
        }
    }

    presentation.dismiss()
    return imageBitmap
}

private inline fun Modifier.thenIf(
    condition: Boolean,
    crossinline other: Modifier.() -> Modifier,
) = if (condition) other() else this

private fun Context.getDisplayManager(): DisplayManager =
    getSystemService(Context.DISPLAY_SERVICE) as DisplayManager


private class EmptySavedStateRegistryOwner : SavedStateRegistryOwner {
    private val controller = SavedStateRegistryController.create(this).apply {
        performRestore(null)
    }

    private val lifecycleOwner: LifecycleOwner = ProcessLifecycleOwner.get()

    @Suppress("UNNECESSARY_SAFE_CALL")
    override val lifecycle: Lifecycle
        get() =
            object : Lifecycle() {
                override fun addObserver(observer: LifecycleObserver) {
                    lifecycleOwner?.lifecycle?.addObserver(observer)
                }

                override fun removeObserver(observer: LifecycleObserver) {
                    lifecycleOwner?.lifecycle?.removeObserver(observer)
                }

                override val currentState = State.INITIALIZED
            }

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    companion object {
        val shared = EmptySavedStateRegistryOwner()
    }
}

private fun Size.roundedToIntSize(): IntSize = IntSize(width.roundToInt(), height.roundToInt())
