package com.example.imageloader.fetcher

import org.junit.Assert.*
import org.junit.Test

class HttpResultTest {

    @Test
    fun `equals should return true for same object`() {
        val bytes = byteArrayOf(1, 2, 3)
        val result = HttpResult(bytes, "text/plain")

        assertTrue(result.equals(result))
    }

    @Test
    fun `equals should return true for equal objects`() {
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(1, 2, 3)
        val result1 = HttpResult(bytes1, "text/plain")
        val result2 = HttpResult(bytes2, "text/plain")

        assertTrue(result1.equals(result2))
        assertTrue(result2.equals(result1))
    }

    @Test
    fun `equals should return false for different bytes`() {
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(1, 2, 4)
        val result1 = HttpResult(bytes1, "text/plain")
        val result2 = HttpResult(bytes2, "text/plain")

        assertFalse(result1.equals(result2))
    }

    @Test
    fun `equals should return false for different contentType`() {
        val bytes = byteArrayOf(1, 2, 3)
        val result1 = HttpResult(bytes, "text/plain")
        val result2 = HttpResult(bytes, "image/jpeg")

        assertFalse(result1.equals(result2))
    }

    @Test
    fun `equals should handle null contentType`() {
        val bytes = byteArrayOf(1, 2, 3)
        val result1 = HttpResult(bytes, null)
        val result2 = HttpResult(bytes, null)

        assertTrue(result1.equals(result2))
    }

    @Test
    fun `equals should return false for different types`() {
        val bytes = byteArrayOf(1, 2, 3)
        val result = HttpResult(bytes, "text/plain")

        assertFalse(result.equals("string"))
        assertFalse(result.equals(null))
    }

    @Test
    fun `hashCode should be consistent with equals`() {
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(1, 2, 3)
        val result1 = HttpResult(bytes1, "text/plain")
        val result2 = HttpResult(bytes2, "text/plain")

        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun `hashCode should differ for different objects`() {
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(1, 2, 4)
        val result1 = HttpResult(bytes1, "text/plain")
        val result2 = HttpResult(bytes2, "text/plain")

        assertNotEquals(result1.hashCode(), result2.hashCode())
    }
}
