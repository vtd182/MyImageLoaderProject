package com.example.imageloader.transformation

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.BitmapPool
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CenterCropRoundedCornersTest {

    @Test
    fun `key should return correct id with radius`() {
        val transformation = CenterCropRoundedCorners(10f)
        assertEquals("CenterCropRoundedCorners(10.0)", transformation.key())
    }

    @Test
    fun `transform should return original bitmap if outWidth less or equal 0`() {
        val pool = mock(BitmapPool::class.java)
        val toTransform = mock(Bitmap::class.java)
        `when`(toTransform.isRecycled).thenReturn(false)

        val transformation = CenterCropRoundedCorners(10f)
        val result = transformation.transform(pool, toTransform, 0, 100)

        assertEquals(toTransform, result)
    }

    @Test
    fun `transform should return original bitmap if outHeight less or equal 0`() {
        val pool = mock(BitmapPool::class.java)
        val toTransform = mock(Bitmap::class.java)
        `when`(toTransform.isRecycled).thenReturn(false)

        val transformation = CenterCropRoundedCorners(10f)
        val result = transformation.transform(pool, toTransform, 100, 0)

        assertEquals(toTransform, result)
    }

    @Test
    fun `transform should return original bitmap if toTransform is recycled`() {
        val pool = mock(BitmapPool::class.java)
        val toTransform = mock(Bitmap::class.java)
        `when`(toTransform.isRecycled).thenReturn(true)

        val transformation = CenterCropRoundedCorners(10f)
        val result = transformation.transform(pool, toTransform, 100, 100)

        assertEquals(toTransform, result)
    }

    @Test
    fun `transform should create new bitmap for normal case`() {
        val pool = mock(BitmapPool::class.java)
        val toTransform = mock(Bitmap::class.java)
        `when`(toTransform.isRecycled).thenReturn(false)
        `when`(toTransform.isMutable).thenReturn(true)
        `when`(toTransform.width).thenReturn(200)
        `when`(toTransform.height).thenReturn(200)
        `when`(toTransform.config).thenReturn(Bitmap.Config.ARGB_8888)

        val transformation = CenterCropRoundedCorners(20f)
        val result = transformation.transform(pool, toTransform, 100, 100)

        assertNotNull(result)
        assertNotEquals(toTransform, result) // Should be a new bitmap
        assertEquals(100, result.width)
        assertEquals(100, result.height)
        assertEquals(Bitmap.Config.ARGB_8888, result.config)
    }

    @Test
    fun `transform should handle when toTransform is not mutable`() {
        val pool = mock(BitmapPool::class.java)
        val toTransform = mock(Bitmap::class.java)
        `when`(toTransform.isRecycled).thenReturn(false)
        `when`(toTransform.isMutable).thenReturn(false)
        `when`(toTransform.width).thenReturn(200)
        `when`(toTransform.height).thenReturn(200)
        `when`(toTransform.config).thenReturn(Bitmap.Config.ARGB_8888)
        val copy = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        `when`(toTransform.copy(any(Bitmap.Config::class.java), anyBoolean())).thenReturn(copy)

        val transformation = CenterCropRoundedCorners(10f)
        val result = transformation.transform(pool, toTransform, 100, 100)

        assertNotNull(result)
        assertEquals(100, result.width)
        assertEquals(100, result.height)
    }
}
