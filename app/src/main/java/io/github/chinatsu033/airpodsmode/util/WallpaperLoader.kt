package io.github.chinatsu033.airpodsmode.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max

object WallpaperLoader {
    private const val TAG = "WallpaperLoader"
    private const val MAX_EDGE = 1080

    fun load(context: Context): Bitmap {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val drawable = try {
                wm.drawable
            } catch (t: SecurityException) {
                Log.i(TAG, "wallpaper permission denied, using fallback", t)
                null
            } catch (t: Throwable) {
                Log.w(TAG, "wallpaper load failed", t)
                null
            }
            if (drawable != null) {
                val src = when (drawable) {
                    is BitmapDrawable -> drawable.bitmap
                    else -> drawable.toBitmap(
                        max(1, drawable.intrinsicWidth.coerceAtLeast(1)),
                        max(1, drawable.intrinsicHeight.coerceAtLeast(1)),
                    )
                }
                downscale(src)
            } else {
                fallbackGradient(context)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "fallback wallpaper", t)
            fallbackGradient(context)
        }
    }

    private fun downscale(src: Bitmap): Bitmap {
        val maxDim = max(src.width, src.height)
        if (maxDim <= MAX_EDGE) return src
        val scale = MAX_EDGE.toFloat() / maxDim
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    fun fallbackGradient(context: Context): Bitmap {
        val dm = context.resources.displayMetrics
        val w = (dm.widthPixels.coerceAtMost(MAX_EDGE)).coerceAtLeast(720)
        val h = (dm.heightPixels.coerceAtMost((MAX_EDGE * 1.8f).toInt())).coerceAtLeast(1280)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val night = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val top = if (night) Color.parseColor("#2A2A28") else Color.parseColor("#D8E6DE")
        val bottom = if (night) Color.parseColor("#1B2E24") else Color.parseColor("#F5F5F3")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    /** Soft blur for wallpaper backdrop (API 31+ RenderEffect-ready path uses Compose blur). */
    fun softBlur(src: Bitmap, radius: Int = 24): Bitmap {
        if (Build.VERSION.SDK_INT < 31) {
            // Cheap downscale-upscale approximation when Toolkit blur unavailable.
            val smallW = (src.width / 8).coerceAtLeast(1)
            val smallH = (src.height / 8).coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
            return Bitmap.createScaledBitmap(small, src.width, src.height, true)
        }
        return try {
            // Prefer downscale approximation for speed/stability without RenderScript.
            val smallW = (src.width / 6).coerceAtLeast(1)
            val smallH = (src.height / 6).coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
            Bitmap.createScaledBitmap(small, src.width, src.height, true)
        } catch (_: Throwable) {
            src
        }
    }
}
