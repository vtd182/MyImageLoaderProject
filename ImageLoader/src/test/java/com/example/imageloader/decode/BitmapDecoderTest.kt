package com.example.imageloader.decode

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class BitmapDecoderTest {

    private fun createValidPngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF00FF00.toInt()) // tô xanh
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    @Test
    fun `decode should successfully decode valid bytes`() {
        val bytes = createValidPngBytes()
        val reqW = 1
        val reqH = 1

        val bmp = BitmapDecoder.decode(bytes, reqW, reqH)
        assertEquals(Bitmap.Config.ARGB_8888, bmp.config)
        assertEquals(true, bmp.width > 0)
        bmp.recycle()
    }

    @Test
    fun `extractDominantColor should return gray when bytes cannot decode`() {
        val color = BitmapDecoder.extractDominantColor(byteArrayOf()) // invalid
        assertEquals(0xFFCCCCCC.toInt(), color)
    }

    @Test(expected = RuntimeException::class)
    fun `decode should throw exception for invalid bytes`() {
        val bytes =
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) // fake jpeg
        BitmapDecoder.decode(bytes, 100, 100)
    }

    @Test
    fun `calculateInSampleSize should return 1 when reqW or reqH less or equal 0`() {
        assertEquals(1, BitmapDecoder.calculateInSampleSize(100, 100, 0, 100))
        assertEquals(1, BitmapDecoder.calculateInSampleSize(100, 100, 100, 0))
        assertEquals(1, BitmapDecoder.calculateInSampleSize(100, 100, -1, 100))
    }

    @Test
    fun `calculateInSampleSize should calculate correct sample size`() {
        assertEquals(4, BitmapDecoder.calculateInSampleSize(400, 400, 100, 100))
        assertEquals(2, BitmapDecoder.calculateInSampleSize(200, 200, 100, 100))
        assertEquals(1, BitmapDecoder.calculateInSampleSize(100, 100, 100, 100))
        assertEquals(1, BitmapDecoder.calculateInSampleSize(50, 50, 100, 100))
    }

    @Test
    fun `calculateInSampleSizeForThumb should calculate sample size`() {
        assertEquals(51, BitmapDecoder.calculateInSampleSizeForThumb(10, 10))
        assertEquals(25, BitmapDecoder.calculateInSampleSizeForThumb(20, 20))
    }
}
