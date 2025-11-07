package com.example.imageloader.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import com.example.imageloader.core.abstract.BitmapPool

/**
 * BitmapDecoder - Singleton object chịu trách nhiệm decode raw bytes thành Bitmap.
 *
 * ## Trách nhiệm chính:
 * 1. **Decode với downsampling**: Giảm kích thước khi decode để tiết kiệm RAM
 * 2. **BitmapPool integration**: Reuse bitmap từ pool thông qua `inBitmap`
 * 3. **Error handling**: Retry khi reuse bitmap fail
 * 4. **Color extraction**: Extract dominant color cho placeholder
 *
 * ## Downsampling (inSampleSize):
 * ```
 * Image gốc: 4000x3000 (48MB uncompressed)
 * Request: 1000x750
 * → inSampleSize = 4
 * → Decode thành: 1000x750 (3MB)
 * → Tiết kiệm 45MB RAM!
 * ```
 *
 * ## BitmapPool Integration:
 * ```
 * 1. Get bitmap từ pool (nếu enabled)
 * 2. Set options.inBitmap = poolBitmap
 * 3. BitmapFactory decode vào poolBitmap
 * 4. Nếu fail (IllegalArgumentException) → retry không inBitmap
 * 5. Return decoded bitmap
 * ```
 *
 * ## Performance:
 * - **Với pool**: 50-70% giảm memory allocation
 * - **Với downsampling**: 75-90% giảm memory footprint
 * - **Combined**: Massive performance improvement
 *
 * ## Configuration:
 * Được config bởi ImageLoader khi init:
 * ```kotlin
 * BitmapDecoder.setBitmapPool(lruBitmapPool)
 * BitmapDecoder.setUseBitmapPool(true/false)
 * ```
 *
 * @see com.example.imageloader.core.LruBitmapPool
 * @see android.graphics.BitmapFactory.Options
 */
object BitmapDecoder {
    /** BitmapPool để lấy bitmap reuse */
    private var bitmapPool: BitmapPool? = null
    
    /** Flag enable/disable bitmap pool */
    private var useBitmapPool: Boolean = false

    /**
     * Set BitmapPool instance.
     * Được gọi bởi ImageLoader khi init.
     *
     * @param pool BitmapPool instance
     */
    fun setBitmapPool(pool: BitmapPool) {
        bitmapPool = pool
    }

    /**
     * Enable/disable bitmap pool usage.
     * Được gọi bởi ImageLoader khi init.
     *
     * @param enabled true để enable pool, false để disable
     */
    fun setUseBitmapPool(enabled: Boolean) {
        useBitmapPool = enabled
    }

    /**
     * Decode raw bytes thành Bitmap với downsampling và pool reuse.
     *
     * ## Process:
     * 1. **inJustDecodeBounds = true**: Đọc dimensions không load bitmap
     * 2. **Calculate inSampleSize**: Tính sample rate dựa trên reqW/H
     * 3. **Get inBitmap từ pool** (nếu enabled)
     * 4. **Decode với inSampleSize và inBitmap**
     * 5. **Retry nếu inBitmap fail** (bitmap pool không compatible)
     *
     * ## inSampleSize calculation:
     * ```
     * Image: 2000x2000, Request: 500x500
     * → inSampleSize = 4
     * → Decoded: 500x500 (1/16 memory usage)
     * ```
     *
     * ## Error handling:
     * - IllegalArgumentException (inBitmap fail) → retry without inBitmap
     * - Decode return null → throw IllegalStateException
     *
     * @param bytes Raw image data (JPEG, PNG, WebP, etc.)
     * @param reqW Requested width (0 = no downsampling)
     * @param reqH Requested height (0 = no downsampling)
     * @return Decoded Bitmap
     * @throws IllegalStateException nếu decode fail
     */
    fun decode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
        val decodedWidth = bounds.outWidth / sample
        val decodedHeight = bounds.outHeight / sample

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true

            // Try to reuse bitmap from pool if enabled
            if (useBitmapPool) {
                bitmapPool?.get(decodedWidth, decodedHeight, Bitmap.Config.ARGB_8888)
                    ?.let { poolBitmap ->
                        inBitmap = poolBitmap
                    }
            }
        }

        val bmp = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: IllegalArgumentException) {
            // inBitmap failed, retry without it
            opts.inBitmap = null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } ?: throw IllegalStateException("Decode returned null")

        return bmp
    }

    fun extractDominantColor(bytes: ByteArray, thumbSize: Int = 10): Int {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSizeForThumb(thumbSize, thumbSize)
        }

        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: return 0xFFCCCCCC.toInt() // xám nhạt

        val palette = Palette.from(bmp).generate()
        bmp.recycle()

        return palette.getVibrantColor(
            palette.getMutedColor(
                palette.getDominantColor(0xFFCCCCCC.toInt())
            )
        )
    }

    internal fun calculateInSampleSize(outW: Int, outH: Int, reqW: Int, reqH: Int): Int {
        if (reqW <= 0 || reqH <= 0) return 1
        var inSampleSize = 1
        if (outH > reqH || outW > reqW) {
            val halfH = outH / 2
            val halfW = outW / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    internal fun calculateInSampleSizeForThumb(reqW: Int, reqH: Int): Int {
        return 512 / reqW
    }
}
