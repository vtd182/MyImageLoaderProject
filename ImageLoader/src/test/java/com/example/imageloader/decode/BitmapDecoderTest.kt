package com.example.imageloader.decode

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BitmapDecoderTest {

    // Helper to create a simple byte array (fake, may not decode)
    private fun createFakeImageBytes(): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

    @Test(expected = RuntimeException::class)
    fun `decode should throw exception for invalid bytes`() {
        val bytes = createFakeImageBytes()
        val reqW = 100
        val reqH = 100

        BitmapDecoder.decode(bytes, reqW, reqH)
    }

    @Test
    fun `extractDominantColor should return default when decode fails`() {
        val bytes = byteArrayOf() // Invalid

        val result = BitmapDecoder.extractDominantColor(bytes)
        assertEquals(0xFFCCCCCC.toInt(), result)
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
