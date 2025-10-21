package com.example.imageloader.core

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class LruBitmapPoolTest {

    private lateinit var pool: LruBitmapPool
    private lateinit var bmp1: Bitmap
    private lateinit var bmp2: Bitmap
    private lateinit var bmpLarge: Bitmap

    @Before
    fun setup() {
        pool = LruBitmapPool(maxSizeBytes = 10_000)

        bmp1 = mock(Bitmap::class.java)
        `when`(bmp1.width).thenReturn(100)
        `when`(bmp1.height).thenReturn(100)
        `when`(bmp1.config).thenReturn(Config.ARGB_8888)
        `when`(bmp1.isMutable).thenReturn(true)
        `when`(bmp1.isRecycled).thenReturn(false)
        `when`(bmp1.allocationByteCount).thenReturn(1000)
        `when`(bmp1.byteCount).thenReturn(1000)

        bmp2 = mock(Bitmap::class.java)
        `when`(bmp2.width).thenReturn(200)
        `when`(bmp2.height).thenReturn(200)
        `when`(bmp2.config).thenReturn(Config.ARGB_8888)
        `when`(bmp2.isMutable).thenReturn(true)
        `when`(bmp2.isRecycled).thenReturn(false)
        `when`(bmp2.allocationByteCount).thenReturn(2000)
        `when`(bmp2.byteCount).thenReturn(2000)

        bmpLarge = mock(Bitmap::class.java)
        `when`(bmpLarge.width).thenReturn(500)
        `when`(bmpLarge.height).thenReturn(500)
        `when`(bmpLarge.config).thenReturn(Config.ARGB_8888)
        `when`(bmpLarge.isMutable).thenReturn(true)
        `when`(bmpLarge.isRecycled).thenReturn(false)
        `when`(bmpLarge.allocationByteCount).thenReturn(6000)
        `when`(bmpLarge.byteCount).thenReturn(6000)
    }

    @Test
    fun `put should add reusable bitmap`() {
        pool.put(bmp1)
        assertEquals(1000, pool.size())
    }

    @Test
    fun `put should ignore non-reusable bitmap`() {
        `when`(bmp1.isMutable).thenReturn(false)
        pool.put(bmp1)
        assertEquals(0, pool.size())
    }

    @Test
    fun `put should ignore bitmap too large`() {
        // vượt quá nửa maxSize (10_000 / 2 = 5000)
        pool.put(bmpLarge)
        assertEquals(0, pool.size())
    }

    @Test
    fun `get should return exact match bitmap`() {
        pool.put(bmp1)
        val result = pool.get(100, 100, Config.ARGB_8888)
        assertEquals(bmp1, result)
        assertEquals(0, pool.size())
    }

    @Test
    fun `get should reuse compatible bitmap and reconfigure if needed`() {
        pool.put(bmp2)

        val result = pool.get(150, 150, Config.ARGB_8888)
        assertNotNull(result)
        verify(bmp2, atLeastOnce()).reconfigure(150, 150, Config.ARGB_8888)
    }

    @Test
    fun `get should skip unreusable bitmap`() {
        pool.put(bmp1)
        `when`(bmp1.isRecycled).thenReturn(true)

        val result = pool.get(100, 100, Config.ARGB_8888)
        assertNull(result)
    }

    @Test
    fun `trimToSize should remove excess bitmaps`() {
        val poolSpy = spy(pool)

        poolSpy.put(bmp1)
        poolSpy.put(bmp2)
        poolSpy.put(bmp2)

        poolSpy.put(bmp2)

        assertTrue(poolSpy.size() <= 10_000)
    }
}
