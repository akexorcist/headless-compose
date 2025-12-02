package dev.akexorcist.compose.headless

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.akexorcist.compose.headless.ui.theme.HeadlessComposeCaptureTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeadlessComposeCaptureTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) {
                    HomeScreen(
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isCapturing by remember { mutableStateOf(false) }
    var savedImagePath by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CaptureButton(
            loading = isCapturing,
            onClick = {
                coroutineScope.launch {
                    savedImagePath = null
                    isCapturing = true
                    savedImagePath = captureAndSave(context).fold(
                        onSuccess = {
                            launch {
                                snackbarHostState.showSnackbar(
                                    message = "Image captured",
                                    withDismissAction = true,
                                )
                            }
                            it
                        },
                        onFailure = { error ->
                            launch {
                                snackbarHostState.showSnackbar(
                                    message = error.message ?: "Something wrong",
                                    withDismissAction = true,
                                )
                            }
                            null
                        },
                    )
                    isCapturing = false
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        CapturedImagePreview(
            imagePath = savedImagePath,
        )
        Spacer(Modifier.height(16.dp))
        ClearImagePreviewButton(
            imagePathAvailable = !savedImagePath.isNullOrBlank(),
            onClick = { savedImagePath = null },
        )
    }
}

@Composable
private fun ClearImagePreviewButton(
    imagePathAvailable: Boolean,
    onClick: () -> Unit,
) {
    Box {
        AnimatedVisibility(
            visible = imagePathAvailable,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Button(
                modifier = Modifier.height(40.dp),
                onClick = onClick,
            ) {
                Text("Clear")
            }
        }
        AnimatedVisibility(
            visible = !imagePathAvailable,
        ) {
            Box(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CapturedImagePreview(
    imagePath: String?,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(300.dp)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
            )
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imagePath != null) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = ImageRequest.Builder(context)
                    .data(File(imagePath))
                    .build(),
                contentDescription = "Captured image",
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                modifier = Modifier.padding(24.dp),
                text = "Cached image will be here",
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private suspend fun captureAndSave(context: Context): Result<String> {
    return try {
        val bitmap: Bitmap = headlessCapture(
            context = context.applicationContext,
            size = DpSize(600.dp, 800.dp),
            content = { ToCaptureContent() },
        ).asAndroidBitmap()

        val cacheDir = context.cacheDir
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "captured_$timestamp.png"
        val file = File(cacheDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        bitmap.recycle()
        Result.success(file.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(e)
    }
}

@Composable
private fun CaptureButton(
    loading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.width(200.dp),
        onClick = onClick,
        enabled = !loading
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text("Capture")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HeadlessComposeCaptureTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        HomeScreen(
            snackbarHostState = snackbarHostState,
        )
    }
}
