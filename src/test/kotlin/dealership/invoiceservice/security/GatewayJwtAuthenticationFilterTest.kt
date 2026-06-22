package dealership.invoiceservice.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Base64
import jakarta.servlet.FilterChain

class GatewayJwtAuthenticationFilterTest {

    private val filter = GatewayJwtAuthenticationFilter(GatewayJwtTokenParser(ObjectMapper()))

    @Test
    fun `adds manager role authority to security context`() {
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, bearerToken(userId = 7L, role = "MANAGER"))
        }
        val response = MockHttpServletResponse()

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ ->
                    val authentication = SecurityContextHolder.getContext().authentication
                    assertNotNull(authentication)
                    assertEquals("ROLE_MANAGER", authentication!!.authorities.single().authority)
                }
            )
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    @Test
    fun `normalizes prefixed manager role authority in security context`() {
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, bearerToken(userId = 7L, role = "ROLE_MANAGER"))
        }
        val response = MockHttpServletResponse()

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ ->
                    val authentication = SecurityContextHolder.getContext().authentication
                    assertNotNull(authentication)
                    assertEquals("ROLE_MANAGER", authentication!!.authorities.single().authority)
                }
            )
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    @Test
    fun `accepts lowercase bearer scheme`() {
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, bearerToken(userId = 7L, role = "CUSTOMER").replace("Bearer ", "bearer "))
        }
        val response = MockHttpServletResponse()

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ ->
                    val authentication = SecurityContextHolder.getContext().authentication
                    assertNotNull(authentication)
                    assertEquals("ROLE_CUSTOMER", authentication!!.authorities.single().authority)
                }
            )
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    @Test
    fun `leaves authorities empty when role claim is missing`() {
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, tokenWithPayload("""{"sub":"42"}"""))
        }
        val response = MockHttpServletResponse()

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ ->
                    val authentication = SecurityContextHolder.getContext().authentication
                    assertNotNull(authentication)
                    assertTrue(authentication!!.authorities.isEmpty())
                }
            )
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    @Test
    fun `returns unauthorized when token payload is invalid`() {
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.token")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(
            request,
            response,
            FilterChain { _, _ -> }
        )

        assertEquals(401, response.status)
    }

    @Test
    fun `returns unauthorized when token has no supported user id claim`() {
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, tokenWithPayload("""{"email":"ada@example.com"}"""))
        }
        val response = MockHttpServletResponse()

        filter.doFilter(
            request,
            response,
            FilterChain { _, _ -> }
        )

        assertEquals(401, response.status)
    }

    @Test
    fun `continues filter chain when authorization header is missing`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        var chainCalled = false

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ ->
                    chainCalled = true
                    assertNull(SecurityContextHolder.getContext().authentication)
                }
            )
        } finally {
            SecurityContextHolder.clearContext()
        }

        assertTrue(chainCalled)
        assertEquals(200, response.status)
    }

    @Test
    fun `ignores non bearer authorization header`() {
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, "Basic abc123")
        }
        val response = MockHttpServletResponse()
        var chainCalled = false

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ ->
                    chainCalled = true
                    assertNull(SecurityContextHolder.getContext().authentication)
                }
            )
        } finally {
            SecurityContextHolder.clearContext()
        }

        assertTrue(chainCalled)
        assertEquals(200, response.status)
    }

    @Test
    fun `returns unauthorized when bearer token value is blank`() {
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, "Bearer   ")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(
            request,
            response,
            FilterChain { _, _ -> }
        )

        assertEquals(401, response.status)
        assertEquals("Bearer token is missing", response.errorMessage)
    }

    @Test
    fun `clears existing security context on unauthorized request`() {
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.token")
        }
        val response = MockHttpServletResponse()
        SecurityContextHolder.getContext().authentication =
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                "existing",
                null,
                emptyList()
            )

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ -> }
            )
        } finally {
            // leave the global state clean even if the assertion fails
            val authentication = SecurityContextHolder.getContext().authentication
            SecurityContextHolder.clearContext()
            assertNull(authentication)
        }

        assertEquals(401, response.status)
    }

    @Test
    fun `uses parser principal as authenticated principal and continues chain`() {
        val parser = mock(GatewayJwtTokenParser::class.java)
        val filter = GatewayJwtAuthenticationFilter(parser)
        val principal = GatewayJwtPrincipal(91L, "ADMIN", mapOf("sub" to "91"))
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, "Bearer some.token")
        }
        val response = MockHttpServletResponse()
        var chainCalled = false

        `when`(parser.parse("some.token")).thenReturn(principal)

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ ->
                    chainCalled = true
                    val authentication = SecurityContextHolder.getContext().authentication as UsernamePasswordAuthenticationToken
                    assertEquals(principal, authentication.principal)
                    assertEquals("ROLE_ADMIN", authentication.authorities.single().authority)
                }
            )
        } finally {
            SecurityContextHolder.clearContext()
        }

        assertTrue(chainCalled)
        assertEquals(200, response.status)
    }

    @Test
    fun `returns unauthorized and does not call chain when parser returns null`() {
        val parser = mock(GatewayJwtTokenParser::class.java)
        val filter = GatewayJwtAuthenticationFilter(parser)
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, "Bearer some.token")
        }
        val response = MockHttpServletResponse()
        var chainCalled = false

        `when`(parser.parse("some.token")).thenReturn(null)

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ -> chainCalled = true }
            )
            assertNull(SecurityContextHolder.getContext().authentication)
        } finally {
            SecurityContextHolder.clearContext()
        }

        assertEquals(401, response.status)
        assertEquals("JWT is missing a supported numeric user id claim", response.errorMessage)
        assertTrue(!chainCalled)
    }

    @Test
    fun `returns unauthorized and does not call chain when parser throws illegal argument`() {
        val parser = mock(GatewayJwtTokenParser::class.java)
        val filter = GatewayJwtAuthenticationFilter(parser)
        val request = MockHttpServletRequest().apply {
            addHeader(HttpHeaders.AUTHORIZATION, "Bearer some.token")
        }
        val response = MockHttpServletResponse()
        var chainCalled = false

        `when`(parser.parse("some.token")).thenThrow(IllegalArgumentException("JWT payload is not valid Base64URL"))

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ -> chainCalled = true }
            )
            assertNull(SecurityContextHolder.getContext().authentication)
        } finally {
            SecurityContextHolder.clearContext()
        }

        assertEquals(401, response.status)
        assertEquals("JWT payload is not valid Base64URL", response.errorMessage)
        assertTrue(!chainCalled)
    }

    private fun bearerToken(userId: Long, role: String): String =
        tokenWithPayload("""{"sub":"$userId","role":"$role"}""")

    private fun tokenWithPayload(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "Bearer $header.$body.signature"
    }
}
