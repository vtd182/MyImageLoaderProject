package com.example.imageloader.cache

import com.example.imageloader.core.EngineResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class ActiveResourcesTest {

    private lateinit var activeResources: ActiveResources
    private lateinit var mockResource: EngineResource

    @Before
    fun setup() {
        activeResources = ActiveResources()
        mockResource = mock(EngineResource::class.java)
    }

    @Test
    fun `put and get should store and retrieve same resource`() {
        activeResources.put("img1", mockResource)

        val result = activeResources.get("img1")

        assertNotNull(result)
        assertEquals(mockResource, result)
    }

    @Test
    fun `get should return null for missing key`() {
        val result = activeResources.get("missing")
        assertNull(result)
    }

    @Test
    fun `remove should delete resource`() {
        activeResources.put("img1", mockResource)
        activeResources.remove("img1")

        val result = activeResources.get("img1")
        assertNull(result)
    }

    @Test
    fun `onResourceReleased should remove and invoke callback`() {
        var callbackKey: String? = null
        var callbackResource: EngineResource? = null

        activeResources.put("imgX", mockResource)
        activeResources.setOnResourceReleased { key, res ->
            callbackKey = key
            callbackResource = res
        }

        activeResources.onResourceReleased("imgX", mockResource)

        assertNull(activeResources.get("imgX"))
        assertEquals("imgX", callbackKey)
        assertEquals(mockResource, callbackResource)
    }

    @Test
    fun `onResourceReleased without callback should still remove item`() {
        activeResources.put("imgZ", mockResource)
        activeResources.onResourceReleased("imgZ", mockResource)

        assertNull(activeResources.get("imgZ"))
    }
}
