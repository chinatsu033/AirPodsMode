package io.github.chinatsu033.airpodsmode.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max

object WallpaperLoader {
    private const val TAG = "WallpaperLoader"
    private const val MAX_EDGE = 1080

    fun loadForPrefs(context: Context, prefs: AppPreferences): Bitmap {
        return try {
            when (prefs.backgroundMode) {
                AppPreferences.BackgroundMode.CUSTOM_IMAGE -> {
                    val uri = prefs.customBackgroundUri
                    if (uri != null) loadUri(context, uri) ?: solidOrGradient(context, prefs)
                    else solidOrGradient(context, prefs)
                }
                AppPreferences.BackgroundMode.SOLID -> solidBitmap(context, prefs.solidColorArgb)
                AppPreferences.BackgroundMode.SYSTEM_WALLPAPER -> loadSystemOrFallback(context)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "loadForPrefs", t)
            fallbackGradient(context)
        }
    }

    private fun solidOrGradient(context: Context, prefs: AppPreferences): Bitmap =
        try {
            solidBitmap(context, prefs.solidColorArgb)
        } catch (_: Throwable) {
            fallbackGradient(context)
        }

    fun loadUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return null
                downscale(bmp)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "loadUri failed", t)
            null
        }
    }

    fun loadSystemOrFallback(context: Context): Bitmap {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val drawable = try {
                wm.drawable
            } catch (_: SecurityException) {
                null
            } catch (_: Throwable) {
                null
            }
            if (drawable != null) {
                val src = when (drawable) {
                    is BitmapDrawable -> drawable.bitmap ?: return fallbackGradient(context)
                    else -> {
                        val w = max(1, drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(MAX_EDGE))
                        val h = max(1, drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(MAX_EDGE * 2))
                        drawable.toBitmap(w, h)
                    }
                }
                downscale(src)
            } else {
                fallbackGradient(context)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "system wallpaper failed", t)
            fallbackGradient(context)
        }
    }

    fun solidBitmap(context: Context, argb: Int): Bitmap {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceIn(480, MAX_EDGE)
        val h = dm.heightPixels.coerceIn(800, MAX_EDGE * 2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(argb)
        return bmp
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
        val w = dm.widthPixels.coerceIn(480, MAX_EDGE)
        val h = dm.heightPixels.coerceIn(800, MAX_EDGE * 2)
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

    /** Cheap downsample blur — avoids RenderEffect / Modifier.blur crashes on Samsung. */
    fun softBlur(src: Bitmap): Bitmap {
        return try {
            val smallW = (src.width / 6).coerceAtLeast(1)
            val smallH = (src.height / 6).coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
            Bitmap.createScaledBitmap(small, src.width, src.height, true)
        } catch (_: Throwable) {
            src
        }
    }
}
