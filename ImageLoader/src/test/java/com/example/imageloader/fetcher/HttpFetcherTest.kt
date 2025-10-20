package com.example.imageloader.fetcher

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection

class HttpFetcherTest {

    @Mock
    private lateinit var mockConnection: HttpURLConnection

    @Mock
    private lateinit var mockConnectionFactory: ConnectionFactory

    private lateinit var httpFetcher: HttpFetcher

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `fetch should return HttpResult on successful response`() = runTest {
        // Given
        val url = "https://example.com/image.jpg"
        val expectedBytes = byteArrayOf(1, 2, 3, 4, 5)
        val contentType = "image/jpeg"
        val inputStream = ByteArrayInputStream(expectedBytes)

        whenever(mockConnectionFactory.open(url)).thenReturn(mockConnection)
        whenever(mockConnection.responseCode).thenReturn(HttpURLConnection.HTTP_OK)
        whenever(mockConnection.contentType).thenReturn(contentType)
        whenever(mockConnection.inputStream).thenReturn(inputStream)

        httpFetcher = HttpFetcher(connectionFactory = mockConnectionFactory)

        val result = httpFetcher.fetch(url)

        assertEquals(expectedBytes.contentToString(), result.bytes.contentToString())
        assertEquals(contentType, result.contentType)
    }

    @Test(expected = Exception::class)
    fun `fetch should throw exception on non-OK response code`() = runTest {
        // Given
        val url = "https://example.com/image.jpg"

        whenever(mockConnectionFactory.open(url)).thenReturn(mockConnection)
        whenever(mockConnection.responseCode).thenReturn(HttpURLConnection.HTTP_NOT_FOUND)

        httpFetcher = HttpFetcher(maxRetries = 1, connectionFactory = mockConnectionFactory)

        httpFetcher.fetch(url)

    }

    @Test(expected = Exception::class)
    fun `fetch should throw exception after max retries on failure`() = runTest {
        // Given
        val url = "https://example.com/image.jpg"
        val maxRetries = 3

        whenever(mockConnectionFactory.open(url)).thenReturn(mockConnection)
        whenever(mockConnection.connect()).thenThrow(RuntimeException("Connection failed"))

        httpFetcher =
            HttpFetcher(maxRetries = maxRetries, connectionFactory = mockConnectionFactory)

        httpFetcher.fetch(url)

    }
}
