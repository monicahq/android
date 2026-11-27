package com.monicahq.android.instance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanceTest {
    @Test
    fun `the default instance is Monica's hosted service`() {
        assertEquals("https://monica.test", DEFAULT_INSTANCE_URL)
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
        assertEquals(DEFAULT_INSTANCE_URL, normalizeInstanceUrl("   ", allowHttp = true))
    }

    @Test
    fun `an address without scheme gets https`() {
        assertEquals("https://monica.example.com", normalizeInstanceUrl(" monica.example.com ", allowHttp = true))
    }

    @Test
    fun `trailing slashes are removed`() {
        assertEquals("https://monica.example.com", normalizeInstanceUrl("https://monica.example.com//", allowHttp = true))
    }

    @Test
    fun `release builds refuse http`() {
        assertNull(normalizeInstanceUrl("http://monica.example.com", allowHttp = false))
        assertTrue(requiresHttps("http://monica.example.com", allowHttp = false))
    }

    @Test
    fun `release builds accept https`() {
        assertEquals("https://monica.example.com", normalizeInstanceUrl("monica.example.com", allowHttp = false))
        assertFalse(requiresHttps("https://monica.example.com", allowHttp = false))
    }

    @Test
    fun `debug builds never ask for https`() {
        assertFalse(requiresHttps("http://monica.example.com", allowHttp = true))
    }

    @Test
    fun `an invalid address does not ask for https`() {
        assertFalse(requiresHttps("not an address", allowHttp = false))
    }

    @Test
    fun `scheme and host are lowercased, the path is not`() {
        assertEquals("https://monica.example.com/Monica", normalizeInstanceUrl("HTTPS://Monica.Example.COM/Monica/", allowHttp = false))
    }

    @Test
    fun `credentials, query and fragment are refused`() {
        assertNull(normalizeInstanceUrl("https://user:secret@monica.example.com", allowHttp = false))
        assertNull(normalizeInstanceUrl("https://monica.example.com/?a=b", allowHttp = false))
        assertNull(normalizeInstanceUrl("https://monica.example.com/#top", allowHttp = false))
    }

    @Test
    fun `an address under the base is accepted`() {
        assertTrue(isUnderBase("https://example.com/monica/oauth/token", "https://example.com/monica"))
        assertTrue(isUnderBase("https://example.com/oauth/token", "https://example.com"))
        assertTrue(isUnderBase("https://example.com:443/oauth/token", "https://example.com"))
    }

    @Test
    fun `an address on another origin or outside the base path is refused`() {
        assertFalse(isUnderBase("https://evil.example/oauth/token", "https://example.com"))
        assertFalse(isUnderBase("http://example.com/oauth/token", "https://example.com"))
        assertFalse(isUnderBase("https://example.com:8443/oauth/token", "https://example.com"))
        assertFalse(isUnderBase("https://example.com/monica-evil/oauth/token", "https://example.com/monica"))
        assertFalse(isUnderBase("https://example.com/oauth/token", "https://example.com/monica"))
        assertFalse(isUnderBase("not a url", "https://example.com"))
    }

    @Test
    fun `http, port and path are kept`() {
        assertEquals("http://192.168.1.10:8080/monica", normalizeInstanceUrl("http://192.168.1.10:8080/monica/", allowHttp = true))
    }

    @Test
    fun `a valid address is kept as typed`() {
        assertEquals("https://monica.example.com", normalizeInstanceUrl("https://monica.example.com", allowHttp = true))
    }

    @Test
    fun `text with spaces is not an address`() {
        assertNull(normalizeInstanceUrl("not an address", allowHttp = true))
    }

    @Test
    fun `another scheme is not accepted`() {
        assertNull(normalizeInstanceUrl("ftp://monica.example.com", allowHttp = true))
    }

    @Test
    fun `a scheme without host is not an address`() {
        assertNull(normalizeInstanceUrl("https://", allowHttp = true))
    }

    @Test
    fun `an unparsable address is refused`() {
        assertNull(normalizeInstanceUrl("https://monica.example.com/a b", allowHttp = true))
        assertNull(normalizeInstanceUrl("https://[broken", allowHttp = true))
    }
}
