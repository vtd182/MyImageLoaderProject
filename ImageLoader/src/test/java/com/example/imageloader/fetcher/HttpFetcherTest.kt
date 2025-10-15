package com.example.imageloader.fetcher

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection

@OptIn(ExperimentalCoroutinesApi::class)
class HttpFetcherTest {

    private lateinit var connectionFactory: ConnectionFactory
    private lateinit var connection: HttpURLConnection
    private lateinit var fetcher: HttpFetcher

    @Before
    fun setup() {
        connectionFactory = mock(ConnectionFactory::class.java)
        connection = mock(HttpURLConnection::class.java)
        fetcher = HttpFetcher(
            maxRetries = 3,
            retryDelayMillis = 1,
            connectionFactory = connectionFactory
        )
    }

    @Test
    fun `fetch should return data when HTTP OK`() = runTest {
        // Arrange
        val expectedData = "hello".toByteArray()
        `when`(connectionFactory.open(anyString())).thenReturn(connection)
        `when`(connection.responseCode).thenReturn(HttpURLConnection.HTTP_OK)
        `when`(connection.inputStream).thenReturn(ByteArrayInputStream(expectedData))

        // Act
        val result = fetcher.fetch("https://example.com")

        // Assert
        assertArrayEquals(expectedData, result)
        verify(connection).connect()
        verify(connection).disconnect()
    }

//    @Test
//    fun `fetch should retry when connection fails`() = runTest {
//        // Arrange
//        whenever(connectionFactory.open(anyString()))
//            .thenAnswer { throw RuntimeException("Network error") }
//
//        // Act + Assert
//        assertThrows(RuntimeException::class.java) {
//            runTest {
//                fetcher.fetch("https://fail.com")
//            }
//        }
//
//        // Verify: 5 attempts total
//        verify(connectionFactory, times(1)).open(anyString())
//    }


    @Test
    fun `fetch should throw when response is not OK`() = runTest {
        // Arrange
        `when`(connectionFactory.open(anyString())).thenReturn(connection)
        `when`(connection.responseCode).thenReturn(500)

        // Act + Assert
        assertThrows(Exception::class.java) {
            runTest {
                fetcher.fetch("https://bad.com")
            }
        }
    }
}
