package dealership.invoiceservice.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class GatewayJwtTokenParserTest {

    private val parser = GatewayJwtTokenParser(ObjectMapper())

    @Test
    fun `parses numeric userId claim`() {
        val token = tokenWithPayload("""{"userId":42,"role":"CUSTOMER"}""")

        val principal = parser.parse(token)

        assertEquals(42L, principal?.userId)
        assertEquals("CUSTOMER", principal?.role)
        assertEquals("CUSTOMER", principal?.claims?.get("role"))
    }

    @Test
    fun `falls back to sub claim when it is numeric text`() {
        val token = tokenWithPayload("""{"sub":"84"}""")

        val principal = parser.parse(token)

        assertEquals(84L, principal?.userId)
    }

    @Test
    fun `returns null when no supported user id claim exists`() {
        val token = tokenWithPayload("""{"email":"ada@example.com"}""")

        val principal = parser.parse(token)

        assertNull(principal)
    }

    @Test
    fun `parses first role from roles collection`() {
        val token = tokenWithPayload("""{"sub":"10","roles":["manager","admin"]}""")

        val principal = parser.parse(token)

        assertEquals(10L, principal?.userId)
        assertEquals("MANAGER", principal?.role)
    }

    @Test
    fun `normalizes prefixed role claim`() {
        val token = tokenWithPayload("""{"sub":"10","role":"ROLE_ADMIN"}""")

        val principal = parser.parse(token)

        assertEquals(10L, principal?.userId)
        assertEquals("ADMIN", principal?.role)
    }

    private fun tokenWithPayload(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body.signature"
    }
}
