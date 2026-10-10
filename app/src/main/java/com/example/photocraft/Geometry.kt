package com.example.photocraft

import android.graphics.Bitmap
import android.graphics.Matrix

/** Rotate / flip / center-crop. */
object Geometry {

    fun apply(src: Bitmap, p: EditParams): Bitmap {
        if (!p.hasGeometryEdits) return src

        var bmp = src
        val turns = ((p.rotation % 4) + 4) % 4
        if (turns != 0 || p.flipH) {
            val m = Matrix()
            if (p.flipH) m.postScale(-1f, 1f)
            m.postRotate(turns * 90f)
            bmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }

        val ratio = p.aspect.ratio
        if (ratio != null) {
            val w = bmp.width
            val h = bmp.height
            // Match the photo's orientation: 3:2 on a portrait photo becomes 2:3.
            val target = if ((w >= h) == (ratio >= 1f)) ratio else 1f / ratio
            val current = w.toFloat() / h
            var cw = w
            var ch = h
            if (current > target) {
                cw = (h * target).toInt().coerceIn(1, w)
            } else {
                ch = (w / target).toInt().coerceIn(1, h)
            }
            if (cw != w || ch != h) {
                bmp = Bitmap.createBitmap(bmp, (w - cw) / 2, (h - ch) / 2, cw, ch)
            }
        }
        return bmp
    }
}
