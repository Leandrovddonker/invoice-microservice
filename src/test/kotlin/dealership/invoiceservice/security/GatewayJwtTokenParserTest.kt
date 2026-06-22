package dealership.invoiceservice.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `parses nested numeric user id claim`() {
        val token = tokenWithPayload("""{"user":{"id":91},"roles":["customer"]}""")

        val principal = parser.parse(token)

        assertEquals(91L, principal?.userId)
        assertEquals("CUSTOMER", principal?.role)
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

    @Test
    fun `parses alternate top level customer id claim`() {
        val token = tokenWithPayload("""{"customer_id":"65","role":"manager"}""")

        val principal = parser.parse(token)

        assertEquals(65L, principal?.userId)
        assertEquals("MANAGER", principal?.role)
    }

    @Test
    fun `parses user id from nested collection and authorities claim`() {
        val token = tokenWithPayload("""{"accounts":[{"customer_id":"55"}],"authorities":["ROLE_MANAGER","ROLE_ADMIN"]}""")

        val principal = parser.parse(token)

        assertEquals(55L, principal?.userId)
        assertEquals("MANAGER", principal?.role)
    }

    @Test
    fun `prefers supported top level user id claim over nested values`() {
        val parser = parserWithClaims(
            mapOf(
                "userId" to 44,
                "nested" to mapOf("customer_id" to "99"),
                "roles" to listOf("customer")
            )
        )

        val principal = parser.parse(tokenWithPayload("""{}"""))

        assertEquals(44L, principal?.userId)
        assertEquals("CUSTOMER", principal?.role)
    }

    @Test
    fun `parses nested user id from array returned by object mapper`() {
        val parser = parserWithClaims(
            mapOf(
                "wrapped" to arrayOf(
                    "ignored",
                    mapOf("account_id" to "73")
                ),
                "authorities" to arrayOf("ROLE_ADMIN", "ROLE_MANAGER")
            )
        )

        val principal = parser.parse(tokenWithPayload("""{}"""))

        assertEquals(73L, principal?.userId)
        assertEquals("ADMIN", principal?.role)
    }

    @Test
    fun `returns null when nested structures contain no numeric user id`() {
        val parser = parserWithClaims(
            mapOf(
                "user" to mapOf("id" to "not-a-number"),
                "accounts" to listOf(mapOf("customer_id" to "still-not-a-number"))
            )
        )

        val principal = parser.parse(tokenWithPayload("""{}"""))

        assertNull(principal)
    }

    @Test
    fun `skips blank roles until it finds a valid role`() {
        val parser = parserWithClaims(
            mapOf(
                "sub" to "10",
                "authorities" to listOf("   ", "ROLE_CUSTOMER", "ROLE_ADMIN")
            )
        )

        val principal = parser.parse(tokenWithPayload("""{}"""))

        assertEquals(10L, principal?.userId)
        assertEquals("CUSTOMER", principal?.role)
    }

    @Test
    fun `returns null role when role claim is blank`() {
        val token = tokenWithPayload("""{"sub":"10","role":"   "}""")

        val principal = parser.parse(token)

        assertEquals(10L, principal?.userId)
        assertNull(principal?.role)
    }

    @Test
    fun `throws when jwt does not contain payload part`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            parser.parse("header-only")
        }

        assertEquals("JWT must contain at least header and payload", exception.message)
    }

    @Test
    fun `throws when jwt payload is not valid base64url`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            parser.parse("header.%%%25.signature")
        }

        assertEquals("JWT payload is not valid Base64URL", exception.message)
    }

    private fun tokenWithPayload(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body.signature"
    }

    @Suppress("UNCHECKED_CAST")
    private fun parserWithClaims(claims: Map<String, Any?>): GatewayJwtTokenParser =
        GatewayJwtTokenParser(
            object : ObjectMapper() {
                override fun <T : Any?> readValue(src: ByteArray?, valueTypeRef: TypeReference<T>?): T = claims as T
            }
        )
}
