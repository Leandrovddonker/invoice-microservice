package dealership.invoiceservice.security

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class GatewayJwtTokenParser(
    private val objectMapper: ObjectMapper
) {
    private val claimTypeReference = object : TypeReference<Map<String, Any?>>() {}
    private val userIdClaimNames = listOf(
        "userId",
        "id",
        "user_id",
        "uid",
        "customerId",
        "customer_id",
        "accountId",
        "account_id",
        "sub"
    )
    private val roleClaimNames = listOf("role", "roles", "authorities")

    fun parse(token: String): GatewayJwtPrincipal? {
        val parts = token.split('.')
        require(parts.size >= 2) { "JWT must contain at least header and payload" }

        val payloadBytes = try {
            Base64.getUrlDecoder().decode(parts[1])
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("JWT payload is not valid Base64URL", exception)
        }

        val claims = objectMapper.readValue(payloadBytes, claimTypeReference)
        val userId = findUserId(claims)
            ?: return null

        return GatewayJwtPrincipal(
            userId = userId,
            role = extractRole(claims),
            claims = claims
        )
    }

    private fun extractRole(claims: Map<String, Any?>): String? =
        roleClaimNames
            .asSequence()
            .mapNotNull { claimName -> claims[claimName] }
            .mapNotNull(::normalizeRole)
            .firstOrNull()

    private fun findUserId(claims: Map<String, Any?>): Long? {
        userIdClaimNames.forEach { claimName ->
            claims[claimName]?.coerceToLongOrNull()?.let { return it }
        }

        claims.values
            .asSequence()
            .mapNotNull(::findUserIdInNestedValue)
            .firstOrNull()
            ?.let { return it }

        return null
    }

    private fun findUserIdInNestedValue(value: Any?): Long? =
        when (value) {
            is Map<*, *> -> findUserId(value.entries
                .mapNotNull { (key, nestedValue) -> (key as? String)?.let { it to nestedValue } }
                .toMap())
            is Collection<*> -> value.asSequence().mapNotNull(::findUserIdInNestedValue).firstOrNull()
            is Array<*> -> value.asSequence().mapNotNull(::findUserIdInNestedValue).firstOrNull()
            else -> null
        }

    private fun Any.coerceToLongOrNull(): Long? =
        when (this) {
            is Number -> toLong()
            is String -> this.toLongOrNull()
            else -> null
        }

    private fun normalizeRole(value: Any?): String? =
        when (value) {
            is String -> value.trim()
                .takeIf(String::isNotEmpty)
                ?.uppercase()
                ?.removePrefix("ROLE_")
                ?.takeIf(String::isNotEmpty)
            is Collection<*> -> value.asSequence().mapNotNull(::normalizeRole).firstOrNull()
            is Array<*> -> value.asSequence().mapNotNull(::normalizeRole).firstOrNull()
            else -> null
        }
}
