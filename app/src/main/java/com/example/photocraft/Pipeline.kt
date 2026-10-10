package com.example.photocraft

import android.graphics.Bitmap
import kotlin.math.max

object Pipeline {

    /** Geometry first (so the vignette follows the crop), then optional auto-enhance, then the sliders. */
    fun render(source: Bitmap, params: EditParams, auto: Boolean): Bitmap {
        val geo = Geometry.apply(source, params)
        val base = if (auto) Enhancer.enhance(geo) else geo
        return Adjuster.apply(base, params)
    }

    fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val s = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            max(1, (src.width * s).toInt()),
            max(1, (src.height * s).toInt()),
            true,
        )
    }
}
