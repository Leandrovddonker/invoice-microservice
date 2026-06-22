package dealership.invoiceservice.config

import dealership.invoiceservice.security.GatewayJwtAuthenticationFilter
import dealership.invoiceservice.security.GatewayJwtTokenParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException

class SecurityConfigTest {

    private val config = SecurityConfig(
        gatewayJwtAuthenticationFilter = GatewayJwtAuthenticationFilter(
            mock(GatewayJwtTokenParser::class.java)
        )
    )

    @Test
    fun `authentication entry point returns unauthorized status`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        config.authenticationEntryPoint().commence(request, response, BadCredentialsException("bad credentials"))

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.status)
    }

    @Test
    fun `cors configuration source exposes configured invoice frontend policy`() {
        val source = config.corsConfigurationSource()
        val configuration = source.getCorsConfiguration(MockHttpServletRequest("GET", "/api/invoices"))

        requireNotNull(configuration)
        assertTrue(configuration.allowedOriginPatterns.orEmpty().single().contains("http://student.inf.st.hsleiden.nl:8091"))
        assertEquals(listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"), configuration.allowedMethods)
        assertEquals(listOf("*"), configuration.allowedHeaders)
        assertEquals(listOf("Location"), configuration.exposedHeaders)
        assertTrue(configuration.allowCredentials == true)
    }
}
