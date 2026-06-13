package dealership.invoiceservice.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION)

        if (authorizationHeader.isNullOrBlank() || !authorizationHeader.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authorizationHeader.removePrefix(BEARER_PREFIX).trim()

        try {
            val principal = tokenParser.parse(token)
            if (principal != null) {
                val authorities = principal.role
                    ?.let { listOf(SimpleGrantedAuthority("ROLE_$it")) }
                    ?: emptyList()

                val authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal,
                    null,
                    authorities
                )
                SecurityContextHolder.getContext().authentication = authentication
            }
            filterChain.doFilter(request, response)
        } catch (exception: IllegalArgumentException) {
            SecurityContextHolder.clearContext()
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, exception.message)
        }
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
