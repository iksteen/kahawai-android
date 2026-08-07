package com.kolktech.kahawai.data.auth

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64 as JavaBase64

class JwtClaimsTest {

    @Before
    fun setUp() {
        // decodeAdminClaim() calls android.util.Base64, which throws
        // "not mocked" under a plain JVM unit test (no Robolectric) — this
        // delegates to the real java.util.Base64, which works fine on the JVM.
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            JavaBase64.getUrlDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun token(payloadJson: String, header: String = "header", signature: String = "sig"): String {
        val payload = JavaBase64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray())
        return "$header.$payload.$signature"
    }

    @Test
    fun `decodeAdminClaim returns true when payload has admin true`() {
        assertTrue(decodeAdminClaim(token("""{"admin":true}""")))
    }

    @Test
    fun `decodeAdminClaim returns false when payload has admin false`() {
        assertFalse(decodeAdminClaim(token("""{"admin":false}""")))
    }

    @Test
    fun `decodeAdminClaim returns false when admin key is missing`() {
        assertFalse(decodeAdminClaim(token("""{}""")))
    }

    @Test
    fun `decodeAdminClaim returns false for a token that is not three dot-separated parts`() {
        assertFalse(decodeAdminClaim("onlyonepart"))
        assertFalse(decodeAdminClaim("a.b"))
        verify(exactly = 0) { android.util.Base64.decode(any<String>(), any()) }
    }

    @Test
    fun `decodeAdminClaim returns false for corrupt base64 payload`() {
        assertFalse(decodeAdminClaim("header.!!!not-base64!!!.sig"))
    }

    @Test
    fun `decodeAdminClaim returns false for a payload that decodes but is not valid JSON`() {
        assertFalse(decodeAdminClaim(token("not json")))
    }
}
