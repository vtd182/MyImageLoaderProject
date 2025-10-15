package com.example.imageloader.core

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.ResourceListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EngineResourceTest {

    private lateinit var bitmap: Bitmap
    private lateinit var listener: ResourceListener
    private lateinit var resource: EngineResource

    @Before
    fun setUp() {
        bitmap = mock(Bitmap::class.java)
        `when`(bitmap.isMutable).thenReturn(true)
        `when`(bitmap.isRecycled).thenReturn(false)
        `when`(bitmap.allocationByteCount).thenReturn(100)
        `when`(bitmap.byteCount).thenReturn(100)
        listener = mock(ResourceListener::class.java)

        resource = EngineResource("testKey", bitmap, listener)
    }

    @Test
    fun `acquire increases refCount and does not release`() {
        resource.acquire()
        assertFalse(resource.isReleased())
        verifyNoInteractions(listener)
    }

    @Test
    fun `release calls listener when refCount reaches zero`() {
        resource.acquire()
        resource.release()

        assertTrue(resource.isReleased())
        verify(listener).onResourceReleased("testKey", resource)
    }

    @Test(expected = IllegalStateException::class)
    fun `release without acquire should throw`() {
        resource.release()
    }

    @Test(expected = IllegalStateException::class)
    fun `acquire after release should throw`() {
        resource.acquire()
        resource.release()
        assertTrue(resource.isReleased())
        resource.acquire() // should throw
    }

    @Test
    fun `getBitmap should return the same bitmap`() {
        assertEquals(bitmap, resource.getBitmap())
    }

    @Test
    fun `sizeInBytes returns allocationByteCount when available`() {
        assertEquals(100, resource.sizeInBytes())
    }

    @Test
    fun `recycle should call bitmap recycle`() {
        resource.recycle()
        verify(bitmap).recycle()
    }

    @Test
    fun `isMutable should return bitmap mutable state`() {
        assertTrue(resource.isMutable())
        `when`(bitmap.isMutable).thenReturn(false)
        assertFalse(resource.isMutable())
    }
}
