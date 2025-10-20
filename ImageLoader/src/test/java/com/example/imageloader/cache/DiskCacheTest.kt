package com.example.imageloader.cache

import android.content.Context
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.contains
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

class DiskCacheTest {

    private lateinit var externalCacheDir: File
    private lateinit var context: Context
    private lateinit var logger: DiskCache.Logger
    private lateinit var cache: DiskCache

    @Before
    fun setup() {
        // Tạo thư mục tạm làm externalCacheDir
        externalCacheDir = createTempDir(prefix = "disk_cache_test_")
        context = mock(Context::class.java)
        `when`(context.externalCacheDir).thenReturn(externalCacheDir)

        logger = mock(DiskCache.Logger::class.java)
        cache = DiskCache(context, maxSizeBytes = 10_000, logger = logger)
    }

    @After
    fun tearDown() {
        cache.clear()
        externalCacheDir.deleteRecursively()
    }

    @Test
    fun `put should save file with extension`() {
        val data = ByteArray(1000) { 42 }
        val key = "test_image"

        val result = cache.put(key, data, "image/jpeg")

        assertTrue(result)

        // File thật sẽ có phần mở rộng .jpg
        val expectedFile = File(externalCacheDir, "image_cache/$key.jpg")
        assertTrue("File should exist after put()", expectedFile.exists())
        assertEquals(1000, expectedFile.length())
    }

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
    fun `put should trigger trimCache when over max size`() {
        val data = ByteArray(20_000) { 7 } // vượt maxSizeBytes = 10_000
        val key = "big_file"

        cache.put(key, data, "image/jpeg")

        verify(logger, atLeastOnce()).wtf(contains("DiskCache"), contains("Trim"))
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

    @Test
    fun `get should detect file with different extension`() {
        // Giả lập file với .webp
        val key = "manual_file"
        val file = File(externalCacheDir, "image_cache/$key.webp")
        file.parentFile?.mkdirs()
        file.writeBytes("1234".toByteArray())

        val result = cache.get(key)

        assertNotNull(result)
        assertArrayEquals("1234".toByteArray(), result)
    }
}
