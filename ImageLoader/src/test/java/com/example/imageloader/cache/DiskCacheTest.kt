package com.example.imageloader.cache

import android.content.Context
import android.graphics.Bitmap
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.contains
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

class DiskCacheTest {

    private lateinit var externalCacheDir: File
    private lateinit var context: Context
    private lateinit var bitmap: Bitmap
    private lateinit var logger: DiskCache.Logger
    private lateinit var cache: DiskCache

    @Before
    fun setup() {
        // Tạo thư mục tạm cho cache
        externalCacheDir = createTempDir(prefix = "disk_cache_test_")
        context = mock(Context::class.java)
        `when`(context.externalCacheDir).thenReturn(externalCacheDir)

        bitmap = mock(Bitmap::class.java)
        `when`(bitmap.byteCount).thenReturn(1024)
        // bitmap.compress sẽ ghi vào OutputStream, nên phải trả true
        `when`(bitmap.compress(any(), anyInt(), any())).thenReturn(true)

        logger = mock(DiskCache.Logger::class.java)
        cache = DiskCache(context, maxSizeBytes = 10_000, logger = logger)
    }

    @After
    fun tearDown() {
        cache.clear()
        externalCacheDir.deleteRecursively()
    }

    @Test
    fun `put should save file`() {
        val result = cache.put("test.png", bitmap)
        assertTrue(result)

        // Đường dẫn thật là externalCacheDir/image_cache/test.png
        val file = File(externalCacheDir, "image_cache/test.png")
        assertTrue("File should exist after put()", file.exists())
    }

    @Test
    fun `get should return null when file missing`() {
        val result = cache.get("missing.png")
        assertNull(result)
    }

    @Test
    fun `put should trigger trimCache when over max size`() {
        val bigBitmap = mock(Bitmap::class.java)
        `when`(bigBitmap.byteCount).thenReturn(20_000)
        `when`(bigBitmap.compress(any(), anyInt(), any())).thenReturn(true)

        cache.put("big.png", bigBitmap)

        // Kiểm tra logger có in log "Trim"
        verify(logger, atLeastOnce()).wtf(contains("DiskCache"), contains("Trim"))
    }

    @Test
    fun `clear should delete all files`() {
        cache.put("a.png", bitmap)
        cache.put("b.png", bitmap)

        val imageDir = File(externalCacheDir, "image_cache")
        assertTrue(imageDir.listFiles()?.isNotEmpty() == true)

        cache.clear()
        assertTrue(imageDir.listFiles()?.isEmpty() == true)
    }
}
