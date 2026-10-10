package com.example.photocraft

import android.graphics.Bitmap
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * One-tap auto-enhance, done entirely on-device.
 *
 * Steps (all gentle, so results stay natural):
 *  1. Gray-world white balance (small, clamped per-channel gains)
 *  2. Auto-levels: stretch the tonal range between the 0.5% and 99.5% luminance percentiles
 *  3. Exposure: gamma nudges the average brightness toward mid-gray
 *  4. Contrast: a mild S-curve
 *  5. Saturation: a small boost
 */
object Enhancer {

    private const val TARGET_MEAN = 0.46f
    private const val GAMMA_STRENGTH = 0.6f
    private const val CONTRAST = 0.25f
    private const val SATURATION = 1.15f

    private class Stats(
        val low: Float,
        val high: Float,
        val gainR: Float,
        val gainG: Float,
        val gainB: Float,
        val meanLuma: Float,
    )

    fun enhance(src: Bitmap): Bitmap {
        val stats = analyze(src)
        val lutR = buildLut(stats, stats.gainR)
        val lutG = buildLut(stats, stats.gainG)
        val lutB = buildLut(stats, stats.gainB)

        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val rowsPerChunk = 64
        val buf = IntArray(w * rowsPerChunk)
        var y = 0
        while (y < h) {
            val rows = min(rowsPerChunk, h - y)
            src.getPixels(buf, 0, w, 0, y, w, rows)
            for (i in 0 until w * rows) {
                val p = buf[i]
                val a = p ushr 24
                val r = lutR[(p shr 16) and 0xFF]
                val g = lutG[(p shr 8) and 0xFF]
                val b = lutB[p and 0xFF]

                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                val r2 = clamp255(luma + (r - luma) * SATURATION)
                val g2 = clamp255(luma + (g - luma) * SATURATION)
                val b2 = clamp255(luma + (b - luma) * SATURATION)

                buf[i] = (a shl 24) or (r2 shl 16) or (g2 shl 8) or b2
            }
            out.setPixels(buf, 0, w, 0, y, w, rows)
            y += rows
        }
        return out
    }

    private fun analyze(src: Bitmap): Stats {
        // Work on a small copy so analysis is fast on big photos.
        val longest = max(src.width, src.height)
        val sample = if (longest > 256) {
            val s = 256f / longest
            Bitmap.createScaledBitmap(
                src,
                max(1, (src.width * s).toInt()),
                max(1, (src.height * s).toInt()),
                true,
            )
        } else {
            src
        }

        val n = sample.width * sample.height
        val px = IntArray(n)
        sample.getPixels(px, 0, sample.width, 0, 0, sample.width, sample.height)
        if (sample !== src) sample.recycle()

        val hist = IntArray(256)
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumL = 0.0
        for (p in px) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val l = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            hist[l]++
            sumR += r
            sumG += g
            sumB += b
            sumL += l
        }

        // Percentiles
        val cut = (n * 0.005f).toInt()
        var acc = 0
        var low = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc > cut) {
                low = i
                break
            }
        }
        acc = 0
        var high = 255
        for (i in 255 downTo 0) {
            acc += hist[i]
            if (acc > cut) {
                high = i
                break
            }
        }

        // Don't over-stretch: keep at least a modest range and cap how far we pull.
        var lowF = min(low.toFloat(), 50f)
        var highF = max(high.toFloat(), 205f)
        if (highF - lowF < 32f) highF = lowF + 32f

        // Gray-world gains, clamped to stay subtle.
        val mR = (sumR / n).toFloat().coerceAtLeast(1f)
        val mG = (sumG / n).toFloat().coerceAtLeast(1f)
        val mB = (sumB / n).toFloat().coerceAtLeast(1f)
        val avg = (mR + mG + mB) / 3f
        val gainR = (avg / mR).coerceIn(0.92f, 1.08f)
        val gainG = (avg / mG).coerceIn(0.92f, 1.08f)
        val gainB = (avg / mB).coerceIn(0.92f, 1.08f)

        val mean = (sumL / n).toFloat()
        return Stats(lowF, highF, gainR, gainG, gainB, mean)
    }

    private fun buildLut(stats: Stats, gain: Float): IntArray {
        val range = stats.high - stats.low

        // Mean brightness after levels, used to pick an exposure gamma.
        val stretchedMean = ((stats.meanLuma - stats.low) / range).coerceIn(0.05f, 0.95f)
        val rawGamma = ln(TARGET_MEAN) / ln(stretchedMean)
        val gamma = (1f + (rawGamma - 1f) * GAMMA_STRENGTH).coerceIn(0.7f, 1.3f)

        val lut = IntArray(256)
        for (v in 0..255) {
            var x = (v * gain - stats.low) / range
            x = x.coerceIn(0f, 1f)
            x = x.pow(gamma)
            val s = x * x * (3f - 2f * x)
            x = x * (1f - CONTRAST) + s * CONTRAST
            lut[v] = clamp255(x * 255f)
        }
        return lut
    }

    private fun clamp255(v: Float): Int = (v + 0.5f).toInt().coerceIn(0, 255)
}
