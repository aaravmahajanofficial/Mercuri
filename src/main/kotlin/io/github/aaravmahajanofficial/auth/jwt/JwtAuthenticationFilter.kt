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

import io.github.aaravmahajanofficial.auth.token.TokenBlacklistService
import io.github.aaravmahajanofficial.common.LogSanitizer.sanitizeLogInput
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val tokenBlacklistService: TokenBlacklistService,
    private val entryPoint: JwtAuthenticationEntryPoint,
) : OncePerRequestFilter() {

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
        // Skip if already authenticated
        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        val token = extractToken(request)

        // No token -> treat as anonymous
        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }

        val validationResult = jwtService.validateToken(token, TokenType.ACCESS)

        if (!validationResult.isValid) {
            log.debug(
                "Token validation failed for request {}: {}",
                sanitizeLogInput(request.requestURI),
                validationResult.error,
            )

            // Don't set authentication -> user remains anonymous
            filterChain.doFilter(request, response)
            return
        }

        val jti = validationResult.jti.toString()
        val userId = validationResult.userID.toString()
        val issuedAt = requireNotNull(validationResult.issuedAt)

        if (tokenBlacklistService.isTokenBlacklisted(jti, userId, issuedAt)) {
            val authException = InsufficientAuthenticationException("Token has been revoked")
            entryPoint.commence(request, response, authException)
            return
        }

        // Token is valid -> build Authentication and set in Security Context
        setAuthentication(request, validationResult)

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
        // User object that Spring stores in the Security Context
        val principal = JwtAuthenticationPrincipal(
            userId = validationResult.userID!!,
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

        log.debug(
            "Successfully authenticated user {} for request {}",
            sanitizeLogInput(validationResult.email),
            sanitizeLogInput(request.requestURI),
        )
    }
}
