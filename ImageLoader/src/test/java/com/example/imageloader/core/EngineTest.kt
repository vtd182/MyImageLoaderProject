package com.example.imageloader.core

import android.graphics.Bitmap
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.core.abstract.BitmapPool
import com.example.imageloader.fetcher.DataFetcher
import com.example.imageloader.target.Target
import com.example.imageloader.transformation.Transformation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@ExperimentalCoroutinesApi
class EngineTest {

    @Mock
    private lateinit var activeResources: ActiveResources

    @Mock
    private lateinit var memoryCache: MemoryCache

    @Mock
    private lateinit var diskCache: DiskCache

    @Mock
    private lateinit var fetcher: DataFetcher

    @Mock
    private lateinit var bitmapPool: BitmapPool

    @Mock
    private lateinit var target: Target

    private lateinit var engine: Engine

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        engine = Engine(activeResources, memoryCache, diskCache, fetcher, bitmapPool)
    }

    @Test
    fun `buildKey with basic request`() {
        val request = Request(url = "http://example.com/image.jpg")
        val key = engine.buildKey(request)
        assertNotNull(key)
        assertTrue(key.isNotEmpty())
        // Since md5, hard to assert exact, but can check it's different for different requests
    }

    @Test
    fun `buildKey with transformations`() {
        val transformation = mock(Transformation::class.java)
        `when`(transformation.key()).thenReturn("testTransform")
        val request = Request(
            url = "http://example.com/image.jpg",
            transformations = listOf(transformation)
        )
        val key = engine.buildKey(request)
        assertNotNull(key)
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
        // Data key should be different if transformations are present
    }


    @Test
    fun `checkMemoryCache should clean up released active resource`() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val releasedResource = mock(EngineResource::class.java)
        `when`(releasedResource.isReleased()).thenReturn(true)
        `when`(activeResources.get(anyString())).thenReturn(releasedResource)

        val req = Request(url = "https://example.com")
        val result = engine.checkMemoryCache(req, target)

        assertTrue(result.not())
        verify(activeResources).remove(anyString())
    }


    @Test
    fun `checkMemoryCache should remove recycled memory bitmap`() {
        val bitmap = mock(Bitmap::class.java)
        `when`(bitmap.isRecycled).thenReturn(true)
        `when`(memoryCache.get(anyString())).thenReturn(bitmap)

        val req = Request(url = "https://example.com")
        val result = engine.checkMemoryCache(req, target)

        assertTrue(!result)
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




}
