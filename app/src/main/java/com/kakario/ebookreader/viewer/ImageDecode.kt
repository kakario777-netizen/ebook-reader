package com.kakario.ebookreader.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import java.nio.ByteBuffer

/**
 * 그림 바이트 → Bitmap. 긴 변이 maxSide 아래가 되도록 줄여서 읽는다.
 * JPG·PNG·WEBP·GIF·BMP는 기본 해독기로, 그걸로 안 되는 HEIC·HEIF(안드로이드 9+)와
 * AVIF(안드로이드 12+)는 ImageDecoder로 한 번 더 시도한다.
 */
object ImageDecode {
    fun decode(bytes: ByteArray, maxSide: Int, config: Bitmap.Config? = null): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val sample = sampleFor(maxOf(bounds.outWidth, bounds.outHeight), maxSide)
            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    if (config != null) inPreferredConfig = config
                },
            )?.let { return it }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                d.setTargetSampleSize(sampleFor(maxOf(info.size.width, info.size.height), maxSide))
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun sampleFor(longest: Int, maxSide: Int): Int {
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
