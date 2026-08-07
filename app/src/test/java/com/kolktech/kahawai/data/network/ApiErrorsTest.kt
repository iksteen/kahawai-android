package com.kolktech.kahawai.data.network

import com.kolktech.kahawai.testutil.httpException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorsTest {

    @Test
    fun `readableMessage returns trimmed error body for HttpException`() {
        val e = httpException(409, "  no source is currently available  ")
        assertEquals("no source is currently available", e.readableMessage())
    }

    @Test
    fun `readableMessage falls back to status message when HttpException body is blank`() {
        val e = httpException(500, "")
        assertEquals("HTTP 500 Response.error()", e.readableMessage())
    }

    @Test
    fun `readableMessage returns message for plain IOException`() {
        val e = IOException("network down")
        assertEquals("network down", e.readableMessage())
    }

    @Test
    fun `readableMessage falls back to Unknown error when message is null`() {
        val e = object : Throwable(null as String?) {}
        assertEquals("Unknown error", e.readableMessage())
    }

    @Test
    fun `isAuthError is true only for 401 HttpException`() {
        assertTrue(httpException(401).isAuthError())
        assertFalse(httpException(403).isAuthError())
        assertFalse(httpException(500).isAuthError())
        assertFalse(IOException("down").isAuthError())
    }
}
