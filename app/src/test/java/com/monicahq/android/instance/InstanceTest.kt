package com.monicahq.android.instance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `an empty address means the default instance`() {
        assertEquals(DEFAULT_INSTANCE_URL, normalizeInstanceUrl("   "))
    }

    @Test
    fun `an address without scheme gets https`() {
        assertEquals("https://monica.example.com", normalizeInstanceUrl(" monica.example.com "))
    }

    @Test
    fun `trailing slashes are removed`() {
        assertEquals("https://monica.example.com", normalizeInstanceUrl("https://monica.example.com//"))
    }

    @Test
    fun `http, port and path are kept`() {
        assertEquals("http://192.168.1.10:8080/monica", normalizeInstanceUrl("http://192.168.1.10:8080/monica/"))
    }

    @Test
    fun `a valid address is kept as typed`() {
        assertEquals("https://monica.example.com", normalizeInstanceUrl("https://monica.example.com"))
    }

    @Test
    fun `text with spaces is not an address`() {
        assertNull(normalizeInstanceUrl("not an address"))
    }

    @Test
    fun `another scheme is not accepted`() {
        assertNull(normalizeInstanceUrl("ftp://monica.example.com"))
    }

    @Test
    fun `a scheme without host is not an address`() {
        assertNull(normalizeInstanceUrl("https://"))
    }

    @Test
    fun `an unparsable address is refused`() {
        assertNull(normalizeInstanceUrl("https://monica.example.com/a b"))
        assertNull(normalizeInstanceUrl("https://[broken"))
    }
}
