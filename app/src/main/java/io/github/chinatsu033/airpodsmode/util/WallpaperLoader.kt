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
    private const val MAX_EDGE = 1440

    fun loadForPrefs(context: Context, prefs: AppPreferences): Bitmap {
        return when (prefs.backgroundMode) {
            AppPreferences.BackgroundMode.CUSTOM_IMAGE -> {
                val uri = prefs.customBackgroundUri
                if (uri != null) loadUri(context, uri) ?: loadSystemOrFallback(context)
                else loadSystemOrFallback(context)
            }
            AppPreferences.BackgroundMode.SOLID -> solidBitmap(context, prefs.solidColorArgb)
            AppPreferences.BackgroundMode.SYSTEM_WALLPAPER -> loadSystemOrFallback(context)
        }
    }

    fun loadUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(boundsWidth = 0, boundsHeight = 0, hint = MAX_EDGE)
                }
                // Re-decode with sampling based on a second pass
                null
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return null
                downscale(bmp)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "loadUri failed", t)
            null
        }
    }

    private fun sampleSize(boundsWidth: Int, boundsHeight: Int, hint: Int): Int {
        var sample = 1
        var w = boundsWidth
        var h = boundsHeight
        while (max(w, h) / sample > hint) sample *= 2
        return sample.coerceAtLeast(1)
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
            Log.w(TAG, "system wallpaper failed", t)
            fallbackGradient(context)
        }
    }

    fun solidBitmap(context: Context, argb: Int): Bitmap {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceIn(720, MAX_EDGE)
        val h = dm.heightPixels.coerceIn(1280, (MAX_EDGE * 2))
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
        val w = dm.widthPixels.coerceIn(720, MAX_EDGE)
        val h = dm.heightPixels.coerceIn(1280, (MAX_EDGE * 2))
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

    fun softBlur(src: Bitmap): Bitmap {
        val smallW = (src.width / 5).coerceAtLeast(1)
        val smallH = (src.height / 5).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        return Bitmap.createScaledBitmap(small, src.width, src.height, true)
    }
}
