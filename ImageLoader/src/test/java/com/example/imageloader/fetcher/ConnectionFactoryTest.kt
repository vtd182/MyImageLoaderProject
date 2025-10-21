//package com.example.imageloader.fetcher
//
//import org.junit.Assert.assertEquals
//import org.junit.Assert.assertNotNull
//import org.junit.Assert.assertTrue
//import org.junit.Assert.fail
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.mockito.Mockito.mock
//import org.mockito.Mockito.mockStatic
//import org.mockito.Mockito.verify
//import org.mockito.Mockito.`when`
//import org.robolectric.RobolectricTestRunner
//import java.net.HttpURLConnection
//import java.net.URL
//
//@RunWith(RobolectricTestRunner::class)
//class ConnectionFactoryTest {
//
//    @Test
//    fun `DefaultConnectionFactory open should return HttpURLConnection from URL`() {
//        val url = "http://example.com"
//        val mockUrl = mock(URL::class.java)
//        val mockConnection = mock(HttpURLConnection::class.java)
//
//        mockStatic(URL::class.java).use { mockedUrl ->
//            mockedUrl.`when`<URL> { URL(url) }.thenReturn(mockUrl)
//            `when`(mockUrl.openConnection()).thenReturn(mockConnection)
//            `when`(mockConnection as? HttpURLConnection).thenReturn(mockConnection)
//
//            val result = DefaultConnectionFactory.open(url)
//
//            assertEquals(mockConnection, result)
//            verify(mockUrl).openConnection()
//        }
//    }
//
//    @Test
//    fun `DefaultConnectionFactory open should handle URL construction`() {
//        val url = "https://upload.wikimedia.org/wikipedia/vi/a/af/Son_Goku_YoungAdult.PNG"
//
//        // Since mocking URL is complex, we can test that it doesn't throw
//        // In real test, it would open actual connection, but in unit test with Robolectric, it should work
//        try {
//            val result = DefaultConnectionFactory.open(url)
//            assertNotNull(result)
//            assertTrue(result is HttpURLConnection)
//        } catch (e: Exception) {
//            // In case of network issues, but for unit test, assume it's ok if no exception in setup
//            fail("Should not throw exception: ${e.message}")
//        }
//    }
//}
