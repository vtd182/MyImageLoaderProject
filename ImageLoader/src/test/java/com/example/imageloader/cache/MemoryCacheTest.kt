package com.example.imageloader.cache

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.BitmapPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemoryCacheTest {

    private lateinit var bitmapPool: BitmapPool
    private lateinit var cache: MemoryCache
    private lateinit var bitmap1: Bitmap
    private lateinit var bitmap2: Bitmap
    private lateinit var bitmap3: Bitmap

    @Before
    fun setup() {
        bitmapPool = mock(BitmapPool::class.java)

        bitmap1 = mock(Bitmap::class.java)
        `when`(bitmap1.byteCount).thenReturn(100)
        `when`(bitmap1.allocationByteCount).thenReturn(100)
        `when`(bitmap1.isMutable).thenReturn(true)

        bitmap2 = mock(Bitmap::class.java)
        `when`(bitmap2.byteCount).thenReturn(100)
        `when`(bitmap2.allocationByteCount).thenReturn(100)
        `when`(bitmap2.isMutable).thenReturn(true)

        bitmap3 = mock(Bitmap::class.java)
        `when`(bitmap3.byteCount).thenReturn(100)
        `when`(bitmap3.allocationByteCount).thenReturn(100)
        `when`(bitmap3.isMutable).thenReturn(true)

        // Giới hạn maxBytes = 200 -> khi thêm 3 bitmap sẽ evict 1 cái
        cache = MemoryCache(maxBytes = 200, bitmapPool = bitmapPool)
    }

    @Test
    fun `put and get should work correctly`() {
        cache.put("key1", bitmap1)
        val result = cache.get("key1")
        assertEquals(bitmap1, result)
    }

    @Test
    fun `evicted bitmap should be returned to pool`() {
        // Khi thêm 3 bitmap -> vượt quá 200 bytes => bitmap1 bị evict
        cache.put("key1", bitmap1)
        cache.put("key2", bitmap2)
        cache.put("key3", bitmap3)

        // verify rằng pool nhận lại bitmap1
        verify(bitmapPool, atLeastOnce()).put(bitmap1)
    }

    @Test
    fun `should not put immutable bitmap into pool`() {
        val immutableBitmap = mock(Bitmap::class.java)
        `when`(immutableBitmap.byteCount).thenReturn(100)
        `when`(immutableBitmap.allocationByteCount).thenReturn(100)
        `when`(immutableBitmap.isMutable).thenReturn(false)

        val smallCache = MemoryCache(maxBytes = 100, bitmapPool = bitmapPool)
        smallCache.put("key1", bitmap1)
        smallCache.put("key2", immutableBitmap)

        // bitmap không mutable thì không được thêm vào pool
        verify(bitmapPool, never()).put(immutableBitmap)
    }

    @Test
    fun `get should return null for missing key`() {
        val result = cache.get("missing")
        assertNull(result)
    }
}
