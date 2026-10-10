package com.example.photocraft

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Lightroom-style adjustments.
 *
 * Tone and white balance run through per-channel lookup tables (fast).
 * Vibrance, saturation and vignette run per pixel. Sharpening is a 3x3 unsharp mask.
 * Work is done in row chunks so large photos don't need extra full-size buffers.
 */
object Adjuster {

    fun apply(src: Bitmap, p: EditParams): Bitmap {
        if (!p.hasPixelEdits) return src

        val w = src.width
        val h = src.height
        val luts = buildLuts(p)
        val lutR = luts[0]
        val lutG = luts[1]
        val lutB = luts[2]

        val vib = p.vibrance / 100f
        val sat = p.saturation / 100f
        val doColor = vib != 0f || sat != 0f
        val vg = p.vignette / 100f
        val sharp = p.sharpening / 100f * 1.5f

        val cx = (w - 1) / 2f
        val cy = (h - 1) / 2f
        val invCx = 1f / max(cx, 1f)
        val invCy = 1f / max(cy, 1f)

        val chunk = 64
        val buf = IntArray(w * (chunk + 2))
        val outBuf = IntArray(w * chunk)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        var y0 = 0
        while (y0 < h) {
            val y1 = min(h, y0 + chunk)
            val top = max(0, y0 - 1)
            val bottom = min(h, y1 + 1)
            val rows = bottom - top
            src.getPixels(buf, 0, w, 0, top, w, rows)

            // Tone, color and vignette for every loaded row (including the halo rows).
            for (ry in 0 until rows) {
                val y = top + ry
                val dy = (y - cy) * invCy
                for (x in 0 until w) {
                    val idx = ry * w + x
                    val px = buf[idx]
                    val a = px ushr 24
                    var r = lutR[(px shr 16) and 0xFF].toFloat()
                    var g = lutG[(px shr 8) and 0xFF].toFloat()
                    var b = lutB[px and 0xFF].toFloat()

                    if (doColor) {
                        val mx = max(r, max(g, b))
                        val mn = min(r, min(g, b))
                        val s0 = if (mx <= 0f) 0f else (mx - mn) / mx
                        val f = max(0f, (1f + vib * (1f - s0)) * (1f + sat))
                        val l = 0.299f * r + 0.587f * g + 0.114f * b
                        r = l + (r - l) * f
                        g = l + (g - l) * f
                        b = l + (b - l) * f
                    }

                    if (vg != 0f) {
                        val dx = (x - cx) * invCx
                        val d = sqrt(dx * dx + dy * dy) / 1.4142f
                        val t = ((d - 0.3f) / 0.7f).coerceIn(0f, 1f)
                        val amount = vg * (t * t * (3f - 2f * t)) * 0.85f
                        if (amount < 0f) {
                            val m = 1f + amount
                            r *= m
                            g *= m
                            b *= m
                        } else {
                            r += (255f - r) * amount
                            g += (255f - g) * amount
                            b += (255f - b) * amount
                        }
                    }

                    buf[idx] = (a shl 24) or (c255(r) shl 16) or (c255(g) shl 8) or c255(b)
                }
            }

            val outRows = y1 - y0
            if (sharp <= 0f) {
                System.arraycopy(buf, (y0 - top) * w, outBuf, 0, w * outRows)
            } else {
                for (oy in 0 until outRows) {
                    val y = y0 + oy
                    val ry = y - top
                    val up = if (y > 0) ry - 1 else ry
                    val down = if (y < h - 1) ry + 1 else ry
                    for (x in 0 until w) {
                        val xl = if (x > 0) x - 1 else x
                        val xr = if (x < w - 1) x + 1 else x
                        val c = buf[ry * w + x]
                        var sr = 0
                        var sg = 0
                        var sb = 0
                        for (rr in intArrayOf(up, ry, down)) {
                            val base = rr * w
                            for (xx in intArrayOf(xl, x, xr)) {
                                val q = buf[base + xx]
                                sr += (q shr 16) and 0xFF
                                sg += (q shr 8) and 0xFF
                                sb += q and 0xFF
                            }
                        }
                        val cr = (c shr 16) and 0xFF
                        val cg = (c shr 8) and 0xFF
                        val cb = c and 0xFF
                        val nr = cr + sharp * (cr - sr / 9f)
                        val ng = cg + sharp * (cg - sg / 9f)
                        val nb = cb + sharp * (cb - sb / 9f)
                        outBuf[oy * w + x] =
                            (c ushr 24 shl 24) or (c255(nr) shl 16) or (c255(ng) shl 8) or c255(nb)
                    }
                }
            }
            out.setPixels(outBuf, 0, w, 0, y0, w, outRows)
            y0 = y1
        }
        return out
    }

    private fun buildLuts(p: EditParams): Array<IntArray> {
        val t = p.temperature / 100f
        val tint = p.tint / 100f
        val gainR = 1f + 0.25f * t
        val gainB = 1f - 0.25f * t
        val gainG = 1f - 0.15f * tint // positive tint = more magenta = less green
        return arrayOf(buildLut(p, gainR), buildLut(p, gainG), buildLut(p, gainB))
    }

    private fun buildLut(p: EditParams, gain: Float): IntArray {
        val expMul = 2.0.pow(p.exposure.toDouble()).toFloat()
        val bl = p.blacks / 100f
        val wh = p.whites / 100f
        val sh = p.shadows / 100f
        val hi = p.highlights / 100f
        val contrastFactor = 1f + (p.contrast / 100f) * 0.6f

        val lut = IntArray(256)
        for (v in 0..255) {
            var x = (v / 255f * gain).coerceIn(0f, 1f)

            if (p.exposure != 0f) {
                x = (x.pow(2.2f) * expMul).pow(1f / 2.2f).coerceIn(0f, 1f)
            }

            val inv = 1f - x
            val wB = inv * inv * inv * inv
            val wW = x * x * x * x
            val wS = 3f * x * inv * inv
            val wH = 3f * x * x * inv
            x = (x + bl * 0.15f * wB + wh * 0.15f * wW + sh * 0.5f * wS + hi * 0.4f * wH)
                .coerceIn(0f, 1f)

            x = (0.5f + (x - 0.5f) * contrastFactor).coerceIn(0f, 1f)

            lut[v] = (x * 255f + 0.5f).toInt().coerceIn(0, 255)
        }
        return lut
    }

    private fun c255(v: Float): Int = (v + 0.5f).toInt().coerceIn(0, 255)
}
