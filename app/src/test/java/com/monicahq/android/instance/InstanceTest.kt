package com.monicahq.android.instance

import org.junit.Assert.assertEquals
import org.junit.Test

class InstanceTest {
    @Test
    fun `the default instance is Monica's hosted service`() {
        assertEquals("https://app.monicahq.com", DEFAULT_INSTANCE_URL)
    }

    @Test
    fun `host removes the https scheme`() {
        assertEquals("app.monicahq.com", instanceHost("https://app.monicahq.com"))
    }

    @Test
    fun `host removes the http scheme`() {
        assertEquals("monica.example.org", instanceHost("http://monica.example.org"))
    }

    @Test
    fun `host removes the trailing slash`() {
        assertEquals("app.monicahq.com", instanceHost("https://app.monicahq.com/"))
    }

    @Test
    fun `host keeps the port and the path`() {
        assertEquals("localhost:8000/monica", instanceHost("http://localhost:8000/monica/"))
    }

    @Test
    fun `host ignores surrounding spaces`() {
        assertEquals("app.monicahq.com", instanceHost("  https://app.monicahq.com  "))
    }

    @Test
    fun `host of an address without scheme is the address itself`() {
        assertEquals("app.monicahq.com", instanceHost("app.monicahq.com"))
    }
}
