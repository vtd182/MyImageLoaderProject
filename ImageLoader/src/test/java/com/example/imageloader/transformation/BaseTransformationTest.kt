package com.example.imageloader.transformation

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.BitmapPool
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BaseTransformationTest {

    private class TestTransformation(id: String) : BaseTransformation(id) {
        override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
            return toTransform // Dummy implementation
        }

        internal fun getOrCreateBitmapInternal(pool: BitmapPool, width: Int, height: Int, config: Bitmap.Config) =
            getOrCreateBitmap(pool, width, height, config)

        internal fun createBitmapInternal(width: Int, height: Int, config: Bitmap.Config) =
            createBitmap(width, height, config)
    }

    @Test
    fun `key should return the id`() {
        val transformation = TestTransformation("testId")
        assertEquals("testId", transformation.key())
    }

    @Test
    fun `getOrCreateBitmap should return from pool if available`() {
        val pool = mock(BitmapPool::class.java)
        val bitmapFromPool = mock(Bitmap::class.java)
        `when`(pool.get(100, 200, Bitmap.Config.ARGB_8888)).thenReturn(bitmapFromPool)

        val transformation = TestTransformation("test")
        val result = transformation.getOrCreateBitmapInternal(pool, 100, 200, Bitmap.Config.ARGB_8888)

        assertEquals(bitmapFromPool, result)
        verify(pool).get(100, 200, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun `getOrCreateBitmap should create new bitmap if pool returns null`() {
        val pool = mock(BitmapPool::class.java)
        `when`(pool.get(100, 200, Bitmap.Config.ARGB_8888)).thenReturn(null)

        val transformation = TestTransformation("test")
        val result = transformation.getOrCreateBitmapInternal(pool, 100, 200, Bitmap.Config.ARGB_8888)

        assertNotNull(result)
        assertEquals(100, result.width)
        assertEquals(200, result.height)
        assertEquals(Bitmap.Config.ARGB_8888, result.config)
        verify(pool).get(100, 200, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun `createBitmap should create bitmap with correct parameters`() {
        val transformation = TestTransformation("test")
        val result = transformation.createBitmapInternal(150, 250, Bitmap.Config.RGB_565)

        assertNotNull(result)
        assertEquals(150, result.width)
        assertEquals(250, result.height)
        assertEquals(Bitmap.Config.RGB_565, result.config)
    }
}
