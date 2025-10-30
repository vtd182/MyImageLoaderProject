package com.example.imageloader.cache

import android.content.Context
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DiskCacheTest {

    private lateinit var externalCacheDir: File
    private lateinit var context: Context
    private lateinit var cache: DiskCache

    @Before
    fun setup() {
        // Tạo thư mục tạm làm externalCacheDir
        externalCacheDir = createTempDir(prefix = "disk_cache_test_")
        context = mock(Context::class.java)
        `when`(context.externalCacheDir).thenReturn(externalCacheDir)

        cache = DiskCache(context, maxSizeBytes = 10_000)
    }

    @After
    fun tearDown() {
        cache.clear()
        externalCacheDir.deleteRecursively()
    }

    // =====================
    // ====== get() ========
    // =====================

    @Test
    fun `get should return same data`() {
        val data = "hello world".toByteArray()
        val key = "sample"

        cache.put(key, data, "image/png")
        val loaded = cache.get(key)

        assertNotNull(loaded)
        assertArrayEquals(data, loaded)
    }

    @Test
    fun `get should return null if file missing`() {
        val result = cache.get("not_exists")
        assertNull(result)
    }

    @Test
    fun `get should detect file with different extension`() {
        val key = "manual_file"
        val file = File(externalCacheDir, "image_cache/$key.webp")
        file.parentFile?.mkdirs()
        file.writeBytes("1234".toByteArray())

        val result = cache.get(key)

        assertNotNull(result)
        assertArrayEquals("1234".toByteArray(), result)
    }

    @Test
    fun `get should handle exception when readBytes fails`() {
        val key = "broken_file"
        val file = File(externalCacheDir, "image_cache/$key.jpg")
        file.parentFile?.mkdirs()
        file.writeBytes("oops".toByteArray())
        file.setReadable(false)

        val result = cache.get(key)

        assertNull(result)
        file.setReadable(true)
    }

    // =====================
    // ====== put() ========
    // =====================

    @Test
    fun `put should save file with extension`() {
        val data = ByteArray(1000) { 42 }
        val key = "test_image"

        val result = cache.put(key, data, "image/jpeg")

        assertTrue(result)

        val expectedFile = File(externalCacheDir, "image_cache/$key.jpg")
        assertTrue(expectedFile.exists())
        assertEquals(1000, expectedFile.length())
    }

    @Test
    fun `put should return true if file already exists`() {
        val key = "exists_test"
        val existing = File(externalCacheDir, "image_cache/$key.jpg")
        existing.parentFile?.mkdirs()
        existing.writeBytes(ByteArray(10)) // file có sẵn

        val result = cache.put(key, ByteArray(5), "image/jpeg")

        assertTrue(result)
        assertEquals(10, existing.length()) // không bị ghi đè
    }

    @Test
    fun `put should fallback to dat extension when content type unknown`() {
        val key = "unknown_type"
        val data = ByteArray(50) { 1 }

        val result = cache.put(key, data, "image/xyz")

        assertTrue(result)
        val expectedFile = File(externalCacheDir, "image_cache/$key.dat")
        assertTrue(expectedFile.exists())
    }

    @Test
    fun `put should return false if writing file fails`() {
        val key = "fail_test"

        val imageDir = File(externalCacheDir, "image_cache")
        imageDir.mkdirs()
        imageDir.setReadOnly()

        val result = cache.put(key, ByteArray(100), "image/jpeg")

        assertFalse(result)
        imageDir.setWritable(true)
    }

    @Test
    fun `put should increase currentSize after writing`() {
        val key = "size_test"
        val data = ByteArray(200)

        val field =
            cache.javaClass.getDeclaredField("currentSize").apply { isAccessible = true }
        val before = field.getLong(cache)

        val result = cache.put(key, data, "image/png")

        val after = field.getLong(cache)
        assertTrue(result)
        assertTrue(after >= before + data.size)
    }

    @Test
    fun `put should trigger trimCache when over max size`() {
        val data = ByteArray(20_000) { 7 } // vượt maxSizeBytes = 10_000
        val key = "big_file"

        val result = cache.put(key, data, "image/jpeg")

        assertTrue(result)

        val expectedFile = File(externalCacheDir, "image_cache/$key.jpg")
        assertTrue(expectedFile.exists())
    }

    @Test
    fun `clear should delete all files`() {
        cache.put("a", ByteArray(100), "image/jpeg")
        cache.put("b", ByteArray(100), "image/png")

        val imageDir = File(externalCacheDir, "image_cache")
        assertTrue(imageDir.listFiles()?.isNotEmpty() == true)

        cache.clear()
        assertTrue(imageDir.listFiles()?.isEmpty() == true)
    }
}
