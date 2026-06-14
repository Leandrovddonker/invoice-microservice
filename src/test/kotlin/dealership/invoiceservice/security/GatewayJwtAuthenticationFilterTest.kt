package dealership.invoiceservice.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
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

    private fun bearerToken(userId: Long, role: String): String =
        tokenWithPayload("""{"sub":"$userId","role":"$role"}""")

    private fun tokenWithPayload(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "Bearer $header.$body.signature"
    }
}
