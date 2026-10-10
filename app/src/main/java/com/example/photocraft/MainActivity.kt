package com.example.photocraft

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                PhotoCraftScreen()
            }
        }
    }
}

private enum class Panel(val label: String) {
    LIGHT("Light"),
    COLOR("Color"),
    EFFECTS("Effects"),
    CROP("Crop"),
}

private const val PREVIEW_SIDE = 1280

@Composable
fun PhotoCraftScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // fullSource is what gets saved; previewSource is a smaller copy used for live editing.
    var fullSource by remember { mutableStateOf<Bitmap?>(null) }
    var previewSource by remember { mutableStateOf<Bitmap?>(null) }
    var edited by remember { mutableStateOf<Bitmap?>(null) }
    var params by remember { mutableStateOf(EditParams()) }
    var autoOn by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.LIGHT) }
    var busy by remember { mutableStateOf(false) }

    // Re-render the preview whenever the photo or any edit changes (small delay = cheap debounce).
    LaunchedEffect(previewSource, params, autoOn) {
        val src = previewSource ?: return@LaunchedEffect
        delay(30)
        val result = withContext(Dispatchers.Default) { Pipeline.render(src, params, autoOn) }
        edited = result
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val full = loadBitmap(context, uri)
                        full to Pipeline.scaleDown(full, PREVIEW_SIDE)
                    }
                }
                result.onSuccess { (full, preview) ->
                    fullSource = full
                    previewSource = preview
                    edited = null
                    params = EditParams()
                    autoOn = false
                    showOriginal = false
                }.onFailure {
                    snackbar.showSnackbar("Couldn't open that photo")
                }
                busy = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Photo Craft", style = MaterialTheme.typography.headlineMedium)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val shown = if (showOriginal) previewSource else (edited ?: previewSource)
                if (shown != null) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = if (showOriginal) "Original photo" else "Edited photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        "Choose a photo to get started",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (busy) CircularProgressIndicator()
            }

            if (previewSource != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showOriginal,
                        onClick = { showOriginal = true },
                        label = { Text("Original") },
                    )
                    FilterChip(
                        selected = !showOriginal,
                        onClick = { showOriginal = false },
                        label = { Text("Edited") },
                    )
                    FilterChip(
                        selected = autoOn,
                        onClick = {
                            autoOn = !autoOn
                            showOriginal = false
                        },
                        label = { Text("Auto enhance") },
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (p in Panel.values()) {
                        FilterChip(
                            selected = panel == p,
                            onClick = { panel = p },
                            label = { Text(p.label) },
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    PanelContent(
                        panel = panel,
                        p = params,
                        onChange = {
                            params = it
                            showOriginal = false
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Choose photo") }

                OutlinedButton(
                    onClick = {
                        params = EditParams()
                        autoOn = false
                        showOriginal = false
                    },
                    enabled = previewSource != null && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Reset") }

                Button(
                    onClick = {
                        val full = fullSource ?: return@Button
                        val currentParams = params
                        val currentAuto = autoOn
                        scope.launch {
                            busy = true
                            val ok = withContext(Dispatchers.Default) {
                                runCatching {
                                    val out = Pipeline.render(full, currentParams, currentAuto)
                                    saveToGallery(context, out)
                                }.getOrDefault(false)
                            }
                            busy = false
                            snackbar.showSnackbar(
                                if (ok) "Saved to Pictures/PhotoCraft" else "Couldn't save the photo"
                            )
                        }
                    },
                    enabled = fullSource != null && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun PanelContent(panel: Panel, p: EditParams, onChange: (EditParams) -> Unit) {
    Column {
        when (panel) {
            Panel.LIGHT -> {
                AdjustSlider("Exposure", p.exposure, -2f..2f, { onChange(p.copy(exposure = it)) }) {
                    "%+.2f EV".format(it)
                }
                AdjustSlider("Contrast", p.contrast, -100f..100f, { onChange(p.copy(contrast = it)) })
                AdjustSlider("Highlights", p.highlights, -100f..100f, { onChange(p.copy(highlights = it)) })
                AdjustSlider("Shadows", p.shadows, -100f..100f, { onChange(p.copy(shadows = it)) })
                AdjustSlider("Whites", p.whites, -100f..100f, { onChange(p.copy(whites = it)) })
                AdjustSlider("Blacks", p.blacks, -100f..100f, { onChange(p.copy(blacks = it)) })
            }

            Panel.COLOR -> {
                AdjustSlider("Temperature", p.temperature, -100f..100f, { onChange(p.copy(temperature = it)) })
                AdjustSlider("Tint", p.tint, -100f..100f, { onChange(p.copy(tint = it)) })
                AdjustSlider("Vibrance", p.vibrance, -100f..100f, { onChange(p.copy(vibrance = it)) })
                AdjustSlider("Saturation", p.saturation, -100f..100f, { onChange(p.copy(saturation = it)) })
            }

            Panel.EFFECTS -> {
                AdjustSlider("Sharpening", p.sharpening, 0f..100f, { onChange(p.copy(sharpening = it)) })
                AdjustSlider("Vignette", p.vignette, -100f..100f, { onChange(p.copy(vignette = it)) })
            }

            Panel.CROP -> {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (a in CropAspect.values()) {
                        FilterChip(
                            selected = p.aspect == a,
                            onClick = { onChange(p.copy(aspect = a)) },
                            label = { Text(a.label) },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onChange(p.copy(rotation = (p.rotation + 1) % 4)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Rotate 90°") }
                    OutlinedButton(
                        onClick = { onChange(p.copy(flipH = !p.flipH)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Flip") }
                }
            }
        }
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String = { it.toInt().toString() },
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(format(value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

/** Decodes the picked image (EXIF rotation handled), capped at [maxSide] px on the longest edge. */
private fun loadBitmap(context: Context, uri: Uri, maxSide: Int = 3000): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = max(info.size.width, info.size.height)
        if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            decoder.setTargetSize(
                (info.size.width * s).toInt().coerceAtLeast(1),
                (info.size.height * s).toInt().coerceAtLeast(1),
            )
        }
    }
}

/** Saves a JPEG into Pictures/PhotoCraft via MediaStore (no storage permission needed on Android 10+). */
private fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "PhotoCraft_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoCraft")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        val stream = resolver.openOutputStream(uri) ?: throw IllegalStateException("No output stream")
        stream.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}
