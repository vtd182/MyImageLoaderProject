package com.example.imageloader.core

import android.graphics.Bitmap
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.core.abstract.BitmapPool
import com.example.imageloader.core.enums.RequestPriority
import com.example.imageloader.fetcher.DataFetcher
import com.example.imageloader.target.Target
import com.example.imageloader.transformation.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@ExperimentalCoroutinesApi
class EngineTest {

    private lateinit var activeResources: ActiveResources
    private lateinit var memoryCache: MemoryCache
    private lateinit var diskCache: DiskCache
    private lateinit var fetcher: DataFetcher
    private lateinit var bitmapPool: BitmapPool
    private lateinit var target: Target

    private lateinit var engine: Engine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Create mocks manually to avoid annotation issues
        activeResources = mock(ActiveResources::class.java)
        memoryCache = mock(MemoryCache::class.java)
        diskCache = mock(DiskCache::class.java)
        fetcher = mock(DataFetcher::class.java)
        bitmapPool = mock(BitmapPool::class.java)
        target = mock(Target::class.java)

        engine = Engine(activeResources, memoryCache, diskCache, fetcher, bitmapPool)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `buildKey with basic request returns valid key`() {
        val request = Request(url = "http://example.com/image.jpg")
        val key = engine.buildKey(request)
        assertNotNull(key)
        assertTrue(key.isNotEmpty())
        assertEquals(32, key.length) // MD5 hash length
    }

    @Test
    fun `buildKey with transformations returns valid key`() {
        val transformation = mock(Transformation::class.java)
        `when`(transformation.key()).thenReturn("testTransform")
        val request = Request(
            url = "http://example.com/image.jpg",
            transformations = listOf(transformation)
        )
        val key = engine.buildKey(request)
        assertNotNull(key)
        assertEquals(32, key.length)
    }

    @Test
    fun `buildDataKey excludes transformations`() {
        val transformation = mock(Transformation::class.java)
        `when`(transformation.key()).thenReturn("testTransform")
        val request = Request(
            url = "http://example.com/image.jpg",
            transformations = listOf(transformation)
        )
        val dataKey = engine.buildDataKey(request)
        val fullKey = engine.buildKey(request)
        assertNotNull(dataKey)
        assertNotNull(fullKey)
        assertEquals(32, dataKey.length)
        assertEquals(32, fullKey.length)
    }

    @Test
    fun `checkMemoryCache cleans up released active resource`() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val releasedResource = mock(EngineResource::class.java)
        `when`(releasedResource.isReleased()).thenReturn(true)
        `when`(releasedResource.getBitmap()).thenReturn(bitmap)
        `when`(activeResources.get(anyString())).thenReturn(releasedResource)

        val req = Request(url = "https://example.com")
        val result = engine.checkMemoryCache(req, target)

        assertFalse(result)
        verify(activeResources).remove(anyString())
    }

    @Test
    fun `checkMemoryCache removes recycled memory bitmap`() {
        val bitmap = mock(Bitmap::class.java)
        `when`(bitmap.isRecycled).thenReturn(true)
        `when`(activeResources.get(anyString())).thenReturn(null)
        `when`(memoryCache.get(anyString())).thenReturn(bitmap)

        val req = Request(url = "https://example.com")
        val result = engine.checkMemoryCache(req, target)

        assertFalse(result)
        verify(memoryCache).remove(anyString())
    }


    @Test
    fun `md5 helper should produce deterministic output`() {
        fun String.testMd5(): String {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val bytes = digest.digest(toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        val text = "abc"
        val hash1 = text.testMd5()
        val hash2 = text.testMd5()

        assertEquals(hash1, hash2)
        assertTrue(hash1.matches(Regex("[0-9a-f]+")))
    }

    // ============ checkMemoryCache() tests ============

    @Test
    fun `checkMemoryCache returns true when active resource is valid`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val resource = mock(EngineResource::class.java)
        `when`(resource.isReleased()).thenReturn(false)
        `when`(resource.getBitmap()).thenReturn(bitmap)
        `when`(activeResources.get(anyString())).thenReturn(resource)

        val req = Request(url = "https://example.com/image.jpg")
        val result = engine.checkMemoryCache(req, target)

        assertTrue(result)
        verify(target).onResourceReady(resource)
        verify(activeResources, never()).remove(anyString())
    }

    @Test
    fun `checkMemoryCache returns false when both caches miss`() {
        `when`(activeResources.get(anyString())).thenReturn(null)
        `when`(memoryCache.get(anyString())).thenReturn(null)

        val req = Request(url = "https://example.com/image.jpg")
        val result = engine.checkMemoryCache(req, target)

        assertFalse(result)
    }

    @Test
    fun `checkMemoryCache removes recycled active resource bitmap`() {
        val bitmap = mock(Bitmap::class.java)
        `when`(bitmap.isRecycled).thenReturn(true)
        val resource = mock(EngineResource::class.java)
        `when`(resource.isReleased()).thenReturn(false)
        `when`(resource.getBitmap()).thenReturn(bitmap)
        `when`(activeResources.get(anyString())).thenReturn(resource)

        val req = Request(url = "https://example.com")
        val result = engine.checkMemoryCache(req, target)

        assertFalse(result)
        verify(activeResources).remove(anyString())
    }

    // ============ buildKey() and buildDataKey() tests ============

    @Test
    fun `buildKey includes resize dimensions`() {
        val req1 = Request(url = "https://example.com/image.jpg")
        val req2 =
            Request(url = "https://example.com/image.jpg", resizeWidth = 500, resizeHeight = 500)

        val key1 = engine.buildKey(req1)
        val key2 = engine.buildKey(req2)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `buildKey includes output dimensions`() {
        val req1 = Request(url = "https://example.com/image.jpg")
        val req2 = Request(url = "https://example.com/image.jpg", outWidth = 300, outHeight = 300)

        val key1 = engine.buildKey(req1)
        val key2 = engine.buildKey(req2)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `buildKey different for different transformations`() {
        val transform1 = mock(Transformation::class.java)
        val transform2 = mock(Transformation::class.java)
        `when`(transform1.key()).thenReturn("crop")
        `when`(transform2.key()).thenReturn("blur")

        val req1 =
            Request(url = "https://example.com/image.jpg", transformations = listOf(transform1))
        val req2 =
            Request(url = "https://example.com/image.jpg", transformations = listOf(transform2))

        val key1 = engine.buildKey(req1)
        val key2 = engine.buildKey(req2)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `buildDataKey same for different transformations`() {
        val transform1 = mock(Transformation::class.java)
        val transform2 = mock(Transformation::class.java)
        `when`(transform1.key()).thenReturn("crop")
        `when`(transform2.key()).thenReturn("blur")

        val req1 =
            Request(url = "https://example.com/image.jpg", transformations = listOf(transform1))
        val req2 =
            Request(url = "https://example.com/image.jpg", transformations = listOf(transform2))

        val dataKey1 = engine.buildDataKey(req1)
        val dataKey2 = engine.buildDataKey(req2)

        assertEquals(dataKey1, dataKey2)
    }

    @Test
    fun `buildDataKey includes resize dimensions`() {
        val req1 = Request(url = "https://example.com/image.jpg")
        val req2 =
            Request(url = "https://example.com/image.jpg", resizeWidth = 500, resizeHeight = 500)

        val dataKey1 = engine.buildDataKey(req1)
        val dataKey2 = engine.buildDataKey(req2)

        assertNotEquals(dataKey1, dataKey2)
    }

    // ============ load() priority queue tests ============

    @Test
    fun `load returns cancellable job`() = runTest {
        `when`(diskCache.get(anyString())).thenReturn(null)
        `when`(fetcher.fetch(anyString())).thenAnswer {
            throw Exception("Should not reach here")
        }

        val req = Request(url = "https://example.com/image.jpg")
        val job = engine.load(req, target, RequestPriority.LOW)

        assertNotNull(job)
        assertFalse(job.isCancelled)

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
    }

    @Test
    fun `load with HIGH priority processes request`() = runTest {
        val bitmap = createTestBitmap()
        `when`(diskCache.get(anyString())).thenReturn(bitmap)

        val req = Request(url = "https://example.com/image.jpg")
        engine.load(req, target, RequestPriority.HIGH)

        advanceUntilIdle()

        verify(target).onLoadStarted()
    }

    @Test
    fun `load with LOW priority processes request`() = runTest {
        val bitmap = createTestBitmap()
        `when`(diskCache.get(anyString())).thenReturn(bitmap)

        val req = Request(url = "https://example.com/image.jpg")
        engine.load(req, target, RequestPriority.LOW)

        advanceUntilIdle()

        verify(target).onLoadStarted()
    }

    // ============ Integration tests with real loading ============

    @Test
    fun `load starts with onLoadStarted callback`() = runTest {
        val bytes = createTestBitmap()
        `when`(diskCache.get(anyString())).thenReturn(bytes)

        val req = Request(url = "https://example.com/image.jpg")
        engine.load(req, target)

        advanceUntilIdle()

        verify(target).onLoadStarted()
    }

//    @Test
//    fun `load from disk cache does not fetch from network`() = runTest {
//        val bytes = createTestBitmap()
//        `when`(diskCache.get(anyString())).thenReturn(bytes)
//
//        val req = Request(url = "https://example.com/image.jpg")
//        engine.load(req, target)
//
//        advanceUntilIdle()
//
//        verify(diskCache, atLeastOnce()).get(anyString())
//        verify(fetcher, never()).fetch(anyString())
//    }

    @Test
    fun `load with resize dimensions includes in keys`() {
        val req1 =
            Request(url = "https://example.com/image.jpg", resizeWidth = 100, resizeHeight = 100)
        val req2 =
            Request(url = "https://example.com/image.jpg", resizeWidth = 200, resizeHeight = 200)

        val key1 = engine.buildKey(req1)
        val key2 = engine.buildKey(req2)
        val dataKey1 = engine.buildDataKey(req1)
        val dataKey2 = engine.buildDataKey(req2)

        assertNotEquals(key1, key2)
        assertNotEquals(dataKey1, dataKey2)
    }

    @Test
    fun `load with output dimensions affects key`() {
        val req1 = Request(url = "https://example.com/image.jpg", outWidth = 100, outHeight = 100)
        val req2 = Request(url = "https://example.com/image.jpg", outWidth = 200, outHeight = 200)

        val key1 = engine.buildKey(req1)
        val key2 = engine.buildKey(req2)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `same URL and params produce same key`() {
        val req1 =
            Request(url = "https://example.com/image.jpg", resizeWidth = 100, resizeHeight = 100)
        val req2 =
            Request(url = "https://example.com/image.jpg", resizeWidth = 100, resizeHeight = 100)

        val key1 = engine.buildKey(req1)
        val key2 = engine.buildKey(req2)

        assertEquals(key1, key2)
    }

    @Test
    fun `multiple transformations affect key uniquely`() {
        val transform1 = mock(Transformation::class.java)
        val transform2 = mock(Transformation::class.java)
        `when`(transform1.key()).thenReturn("crop")
        `when`(transform2.key()).thenReturn("blur")

        val req1 =
            Request(url = "https://example.com/img.jpg", transformations = listOf(transform1))
        val req2 = Request(
            url = "https://example.com/img.jpg",
            transformations = listOf(transform1, transform2)
        )
        val req3 = Request(
            url = "https://example.com/img.jpg",
            transformations = listOf(transform2, transform1)
        )

        val key1 = engine.buildKey(req1)
        val key2 = engine.buildKey(req2)
        val key3 = engine.buildKey(req3)

        assertNotEquals(key1, key2)
        assertNotEquals(key2, key3)
    }

    @Test
    fun `buildKey handles URL with special characters`() {
        val req = Request(url = "https://example.com/image.jpg?query=test&param=value#hash")
        val key = engine.buildKey(req)

        assertNotNull(key)
        assertEquals(32, key.length)
    }

    @Test
    fun `buildDataKey ignores output dimensions`() {
        val req1 = Request(url = "https://example.com/image.jpg", outWidth = 100, outHeight = 100)
        val req2 = Request(url = "https://example.com/image.jpg", outWidth = 200, outHeight = 200)

        val dataKey1 = engine.buildDataKey(req1)
        val dataKey2 = engine.buildDataKey(req2)

        // Data keys should be same because they only depend on URL and resize, not output dimensions
        assertEquals(dataKey1, dataKey2)
    }

    // ============ Helper methods ============

    private fun createTestBitmap(): ByteArray {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
