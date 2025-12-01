/*
 * Copyright 2025 Aarav Mahajan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.github.aaravmahajanofficial.auth.jwt

import io.github.aaravmahajanofficial.common.LogSanitizer.sanitizeLogInput
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val USER_ID_ATTRIBUTE = "userID"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try { // Skip if already authenticated
            if (SecurityContextHolder.getContext().authentication != null) {
                filterChain.doFilter(request, response)
                return
            }

            val token = extractToken(request)
            if (token == null) {
                filterChain.doFilter(request, response)
                return
            }

            val validationResult = jwtService.validateToken(token, TokenType.ACCESS)

            if (validationResult.isValid) {
                setAuthentication(request, validationResult)
            } else {
                log.debug(
                    "Token validation failed for request {}: {}",
                    sanitizeLogInput(request.requestURI),
                    validationResult.error,
                )
            }
        } catch (e: Exception) {
            SecurityContextHolder.clearContext()

            if (e is AuthenticationException) {
                log.error(
                    "Authentication error processing JWT for request:{} {}",
                    sanitizeLogInput(request.requestURI),
                    e.message,
                )
            } else {
                log.error(
                    "Unexpected error processing JWT authentication for request ${
                        sanitizeLogInput(
                            request.requestURI,
                        )
                    }",
                    e,
                )
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader(AUTHORIZATION_HEADER)

        if (authHeader.isNullOrBlank() || !authHeader.startsWith(BEARER_PREFIX)) {
            return null
        }

        val token = authHeader.substring(BEARER_PREFIX.length)
        return token.ifBlank { null }
    }

    // Build the Spring Authentication Object
    private fun setAuthentication(request: HttpServletRequest, validationResult: TokenValidationResult) {
        // User object Spring stores in the Security Context
        val principal = JwtAuthenticationPrincipal(
            userID = validationResult.userID!!,
            email = validationResult.email!!,
        )

        val authorities = validationResult.roles?.map { role ->
            SimpleGrantedAuthority("ROLE_${role.name}")
        } ?: emptyList()

        // Build authentication object
        val authentication = UsernamePasswordAuthenticationToken(
            principal,
            null,
            authorities,
        ).apply {
            details = WebAuthenticationDetailsSource().buildDetails(request) // metadata
        }

        // This would let Spring Security treat the user as authenticated
        SecurityContextHolder.getContext().authentication = authentication

        request.setAttribute(USER_ID_ATTRIBUTE, validationResult.userID)

        log.debug("Successfully authenticated user: {}", validationResult.email)
    }
}
