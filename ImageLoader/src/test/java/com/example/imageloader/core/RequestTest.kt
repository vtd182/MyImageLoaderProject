package com.example.imageloader.core

import com.example.imageloader.transformation.Transformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestTest {

    @Test
    fun `Request with all defaults`() {
        val request = Request(url = "testUrl")
        assertEquals("testUrl", request.url)
        assertNull(request.resizeWidth)
        assertNull(request.resizeHeight)
        assertTrue(request.useMemoryCache)
        assertTrue(request.useDiskCache)
        assertTrue(request.transformations.isEmpty())
        assertNull(request.outWidth)
        assertNull(request.outHeight)
    }

    @Test
    fun `Request with custom values`() {
        val request = Request(
            url = "testUrl",
            resizeWidth = 100,
            resizeHeight = 200,
            useMemoryCache = false,
            useDiskCache = false,
            transformations = emptyList(),
            outWidth = 50,
            outHeight = 75
        )
        assertEquals("testUrl", request.url)
        assertEquals(100, request.resizeWidth)
        assertEquals(200, request.resizeHeight)
        assertFalse(request.useMemoryCache)
        assertFalse(request.useDiskCache)
        assertEquals(emptyList<Transformation>(), request.transformations)
        assertEquals(50, request.outWidth)
        assertEquals(75, request.outHeight)
    }

    @Test
    fun `Request equality`() {
        val r1 = Request("url")
        val r2 = Request("url")
        assertEquals(r1, r2)
        val r3 = Request("url", resizeWidth = 10)
        assertNotEquals(r1, r3)
    }

    @Test
    fun `Request copy method`() {
        val original = Request("url", resizeWidth = 10)
        val copied = original.copy(resizeWidth = 20)
        assertEquals(10, original.resizeWidth)
        assertEquals(20, copied.resizeWidth)
        assertEquals(original.url, copied.url)
    }

    @Test
    fun `Request toString contains url`() {
        val request = Request("testUrl")
        assertTrue(request.toString().contains("testUrl"))
    }
}
