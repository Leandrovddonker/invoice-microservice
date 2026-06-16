package dealership.invoiceservice.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class GatewayJwtAuthenticationFilter(
    private val tokenParser: GatewayJwtTokenParser
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractBearerToken(request) ?: run {
            filterChain.doFilter(request, response)
            return
        }

        if (token.isBlank()) {
            log.warn("Unauthorized request to {} {}: bearer token is missing", request.method, request.requestURI)
            sendUnauthorized(response, "Bearer token is missing")
            return
        }

        try {
            val authentication = buildAuthentication(token)
            if (authentication == null) {
                log.warn(
                    "Unauthorized request to {} {}: JWT is missing a supported numeric user id claim",
                    request.method,
                    request.requestURI
                )
                sendUnauthorized(response, "JWT is missing a supported numeric user id claim")
                return
            }

            SecurityContextHolder.getContext().authentication = authentication
            filterChain.doFilter(request, response)
        } catch (exception: IllegalArgumentException) {
            log.warn(
                "Unauthorized request to {} {}: {}",
                request.method,
                request.requestURI,
                exception.message
            )
            sendUnauthorized(response, exception.message)
        }
    }

    private fun extractBearerToken(request: HttpServletRequest): String? {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authorizationHeader.isNullOrBlank() || !authorizationHeader.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            return null
        }

        return authorizationHeader.substringAfter(' ', "").trim()
    }

    private fun buildAuthentication(token: String): UsernamePasswordAuthenticationToken? {
        val principal = tokenParser.parse(token) ?: return null
        return UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            authoritiesFor(principal)
        )
    }

    private fun authoritiesFor(principal: GatewayJwtPrincipal): List<SimpleGrantedAuthority> =
        principal.role
            ?.let { listOf(SimpleGrantedAuthority("ROLE_$it")) }
            ?: emptyList()

    private fun sendUnauthorized(response: HttpServletResponse, message: String?) {
        SecurityContextHolder.clearContext()
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, message)
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
