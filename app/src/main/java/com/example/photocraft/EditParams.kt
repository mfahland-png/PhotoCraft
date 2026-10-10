package com.example.photocraft

enum class CropAspect(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    SQUARE("1:1", 1f),
    FOUR_FIVE("4:5", 0.8f),
    THREE_TWO("3:2", 1.5f),
    SIXTEEN_NINE("16:9", 16f / 9f),
}

/**
 * Everything the user can change. Slider values are in the ranges noted below;
 * 0 always means "no change".
 */
data class EditParams(
    // Light
    val exposure: Float = 0f,      // -2..2 EV
    val contrast: Float = 0f,      // -100..100
    val highlights: Float = 0f,    // -100..100
    val shadows: Float = 0f,       // -100..100
    val whites: Float = 0f,        // -100..100
    val blacks: Float = 0f,        // -100..100
    // Color
    val temperature: Float = 0f,   // -100 (cool) .. 100 (warm)
    val tint: Float = 0f,          // -100 (green) .. 100 (magenta)
    val vibrance: Float = 0f,      // -100..100
    val saturation: Float = 0f,    // -100..100
    // Effects
    val sharpening: Float = 0f,    // 0..100
    val vignette: Float = 0f,      // -100 (dark edges) .. 100 (light edges)
    // Geometry
    val rotation: Int = 0,         // clockwise quarter turns
    val flipH: Boolean = false,
    val aspect: CropAspect = CropAspect.ORIGINAL,
) {
    val hasPixelEdits: Boolean
        get() = exposure != 0f || contrast != 0f || highlights != 0f || shadows != 0f ||
            whites != 0f || blacks != 0f || temperature != 0f || tint != 0f ||
            vibrance != 0f || saturation != 0f || sharpening != 0f || vignette != 0f

    val hasGeometryEdits: Boolean
        get() = rotation % 4 != 0 || flipH || aspect.ratio != null
}
