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

import io.github.aaravmahajanofficial.common.exception.InvalidTokenException
import io.github.aaravmahajanofficial.config.JwtProperties
import io.github.aaravmahajanofficial.users.RoleType
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.SignatureException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(private val jwtProperties: JwtProperties, private val secretKey: SecretKey) {

    private val logger = LoggerFactory.getLogger(JwtService::class.java)

    companion object {
        private const val CLAIMS_EMAIL = "email"
        private const val CLAIMS_ROLES = "roles"
        private const val CLAIMS_TYPE = "type"
    }

    fun generateAccessToken(request: TokenRequest): String {
        val now = Date()
        val expiration = Date(now.time + jwtProperties.accessTokenExpiration)

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(request.userID.toString())
            .claim(CLAIMS_EMAIL, request.email)
            .claim(CLAIMS_ROLES, request.roles.map { it.value })
            .claim(CLAIMS_TYPE, TokenType.ACCESS.name)
            .issuedAt(now)
            .expiration(expiration)
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact()
    }

    fun generateRefreshToken(request: TokenRequest): String {
        val now = Date()
        val expiration = Date(now.time + jwtProperties.refreshTokenExpiration)

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(request.userID.toString())
            .claim(CLAIMS_EMAIL, request.email)
            .claim(CLAIMS_TYPE, TokenType.REFRESH.name)
            .issuedAt(now)
            .expiration(expiration)
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact()
    }

    fun generateTokenPair(request: TokenRequest): TokenPair = TokenPair(
        accessToken = generateAccessToken(request),
        refreshToken = generateRefreshToken(request),
        expiresIn = jwtProperties.accessTokenExpiration / 1000,
    )

    fun validateToken(token: String, expectedType: TokenType): TokenValidationResult = try {
        val claims = extractAllClaims(token)
        val actualType = claims[CLAIMS_TYPE] as? String

        when {
            actualType != expectedType.name -> {
                logger.debug("Token type mismatch: expected {}, actual {}", expectedType, actualType)
                TokenValidationResult.invalid(TokenValidationError.WRONG_TOKEN_TYPE)
            }

            else -> TokenValidationResult.valid(
                UUID.fromString(claims.subject),
                claims[CLAIMS_EMAIL] as? String ?: "",
                extractRolesFromClaims(claims),
            )
        }
    } catch (e: InvalidTokenException) {
        TokenValidationResult.invalid(e.error ?: TokenValidationError.MALFORMED)
    }

    fun extractAllClaims(token: String): Claims = try {
        Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    } catch (e: ExpiredJwtException) {
        logger.debug("Token expired: {}", e.message)
        throw InvalidTokenException("Token Expired", TokenValidationError.EXPIRED, e)
    } catch (e: SignatureException) {
        logger.debug("Token signature is invalid: {}", e.message)
        throw InvalidTokenException("Invalid signature", TokenValidationError.INVALID_SIGNATURE, e)
    } catch (e: MalformedJwtException) {
        logger.debug("Token is malformed: {}", e.message)
        throw InvalidTokenException("Malformed Token", TokenValidationError.MALFORMED, e)
    } catch (e: JwtException) {
        logger.debug("Token parsing failed: {}", e.message)
        throw InvalidTokenException("Failed to parse token", TokenValidationError.MALFORMED, e)
    }

    private fun extractRolesFromClaims(claims: Claims): Set<RoleType> {
        @Suppress("UNCHECKED_CAST")
        val rolesList = claims[CLAIMS_ROLES] as? List<String> ?: emptyList()
        return rolesList.mapNotNull { roleName ->
            RoleType.entries.find { it.value == roleName } ?: run {
                logger.warn("Unknown role in token: {}", roleName)
                null
            }
        }.toSet()
    }

    fun refreshAccessToken(refreshToken: String, currentRoles: Set<RoleType>): String {
        val validationResult = validateToken(refreshToken, TokenType.REFRESH)

        if (!validationResult.isValid) {
            throw InvalidTokenException("Invalid refresh token", validationResult.error)
        }

        val tokenRequest = TokenRequest(
            userID = validationResult.userID!!,
            email = validationResult.email!!,
            roles = currentRoles,
        )

        return generateAccessToken(tokenRequest)
    }
}
