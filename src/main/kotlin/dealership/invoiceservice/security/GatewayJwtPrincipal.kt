package dealership.invoiceservice.security

data class GatewayJwtPrincipal(
    val userId: Long,
    val role: String?,
    val claims: Map<String, Any?>
)
