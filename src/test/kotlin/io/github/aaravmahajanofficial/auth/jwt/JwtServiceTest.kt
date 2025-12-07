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

import io.github.aaravmahajanofficial.config.JwtProperties
import io.github.aaravmahajanofficial.users.RoleType
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.io.Encoders
import io.jsonwebtoken.security.Keys
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.crypto.SecretKey

class JwtServiceTest {

    private lateinit var jwtProperties: JwtProperties
    private lateinit var jwtService: JwtService
    private lateinit var secretKey: SecretKey

    companion object {
        private const val ACCESS_TOKEN_EXPIRATION = 900_000L
        private const val REFRESH_TOKEN_EXPIRATION = 604_800_000L

        private val TEST_SECRET_KEY: String by lazy {
            val key = Jwts.SIG.HS512.key().build()
            Encoders.BASE64URL.encode(key.encoded)
        }
    }

    @BeforeEach
    fun setUp() {
        jwtProperties = JwtProperties(
            secretKey = TEST_SECRET_KEY,
            refreshTokenSecretKey = TEST_SECRET_KEY,
            accessTokenExpiration = ACCESS_TOKEN_EXPIRATION,
            refreshTokenExpiration = REFRESH_TOKEN_EXPIRATION,
        )

        val keyBytes = Decoders.BASE64URL.decode(TEST_SECRET_KEY)
        secretKey = Keys.hmacShaKeyFor(keyBytes)

        jwtService = JwtService(jwtProperties, secretKey)
    }

    private fun createTokenRequest(
        userID: UUID = UUID.randomUUID(),
        email: String = "john.doe@example.com",
        roles: Set<RoleType> = setOf(RoleType.CUSTOMER),
    ) = TokenRequest(userID, email, roles)

    @Nested
    @DisplayName("Access Token Generation")
    inner class AccessTokenGeneration {

        @Test
        fun `should generate valid access token with correct claims`() {
            // Given
            val userID = UUID.randomUUID()
            val email = "john.doe@example.com"
            val roles = setOf(RoleType.CUSTOMER, RoleType.SELLER)
            val request = createTokenRequest(userID, email, roles)

            // When
            val accessToken = jwtService.generateAccessToken(request)

            // Then
            accessToken.shouldNotBeEmpty()

            val claims = jwtService.extractAllClaims(accessToken)
            claims.subject shouldBe userID.toString()
            claims["email"] shouldBe email
            claims["type"] shouldBe TokenType.ACCESS.name

            @Suppress("UNCHECKED_CAST")
            val extractedRoles = claims["roles"] as List<String>
            extractedRoles shouldContainExactlyInAnyOrder roles.map { it.value }
        }

        @Test
        fun `should generate access token with correct expiration`() {
            // Given
            val request = createTokenRequest()

            // When
            val accessToken = jwtService.generateAccessToken(request)
            val claims = jwtService.extractAllClaims(accessToken)
            val expiration = claims.expiration.time
            val issuedAt = claims.issuedAt.time

            // Then
            (expiration - issuedAt) shouldBe ACCESS_TOKEN_EXPIRATION
        }
    }

    @Nested
    @DisplayName("Refresh Token Generation")
    inner class RefreshTokenGeneration {

        @Test
        fun `should generate valid refresh token`() {
            // Given
            val userID = UUID.randomUUID()
            val request = createTokenRequest(userID)

            // When
            val refreshToken = jwtService.generateRefreshToken(request)

            // Then
            refreshToken.shouldNotBeEmpty()

            val claims = jwtService.extractAllClaims(refreshToken)
            claims.subject shouldBe userID.toString()
            claims["type"] shouldBe TokenType.REFRESH.name
        }

        @Test
        fun `should generate refresh token with correct expiration`() {
            // Given
            val request = createTokenRequest()

            // When
            val refreshToken = jwtService.generateRefreshToken(request)
            val claims = jwtService.extractAllClaims(refreshToken)
            val expiration = claims.expiration.time
            val issuedAt = claims.issuedAt.time

            // Then
            (expiration - issuedAt) shouldBe REFRESH_TOKEN_EXPIRATION
        }
    }

    @Nested
    @DisplayName("Token Validation")
    inner class TokenValidation {

        @Test
        fun `should validate access token successfully`() {
            // Given
            val request = createTokenRequest()
            val accessToken = jwtService.generateAccessToken(request)

            // When
            val result = jwtService.validateToken(accessToken, TokenType.ACCESS)

            // Then
            result.isValid shouldBe true
            result.userID shouldBe request.userID
            result.email shouldBe request.email
            result.roles shouldContainExactlyInAnyOrder request.roles
        }

        @Test
        fun `should validate refresh token successfully`() {
            // Given
            val request = createTokenRequest()
            val refreshToken = jwtService.generateRefreshToken(request)

            // When
            val result = jwtService.validateToken(refreshToken, TokenType.REFRESH)

            // Then
            result.isValid shouldBe true
            result.userID shouldBe request.userID
        }

        @Test
        fun `should reject expired token`() {
            // Given
            val shortLivedProperties = JwtProperties(
                secretKey = jwtProperties.secretKey,
                refreshTokenSecretKey = jwtProperties.secretKey,
                accessTokenExpiration = -1000L,
                refreshTokenExpiration = -1000L,
            )

            val shortJwtService = JwtService(shortLivedProperties, secretKey)
            val request = createTokenRequest()

            val accessToken = shortJwtService.generateAccessToken(request)

            Thread.sleep(5)

            // When
            val result = shortJwtService.validateToken(accessToken, TokenType.ACCESS)

            // Then
            result.isValid shouldBe false
            result.error shouldBe TokenValidationError.EXPIRED
        }

        @Test
        fun `should reject malformed token`() {
            // Given
            val malformedToken = "not.a.valid.jwt.token"

            // When
            val result = jwtService.validateToken(malformedToken, TokenType.ACCESS)

            // Then
            result.isValid shouldBe false
            result.error shouldBe TokenValidationError.MALFORMED
        }

        @Test
        fun `should reject token with invalid signature`() {
            // Given
            val request = createTokenRequest()
            val accessToken = jwtService.generateAccessToken(request)

            // Tamper the token
            val parts = accessToken.split(".")
            val tamperToken = "${parts[0]}.${parts[1]}.invalidSignature"

            // When
            val result = jwtService.validateToken(tamperToken, TokenType.ACCESS)

            // Then
            result.isValid shouldBe false
            result.error shouldBe TokenValidationError.INVALID_SIGNATURE
        }

        @Test
        fun `should reject access token when refresh token expected`() {
            // Given
            val request = createTokenRequest()
            val accessToken = jwtService.generateAccessToken(request)

            // When
            val result = jwtService.validateToken(accessToken, TokenType.REFRESH)

            // Then
            result.isValid shouldBe false
            result.error shouldBe TokenValidationError.WRONG_TOKEN_TYPE
        }

        @Test
        fun `should reject refresh token when access token expected`() {
            // Given
            val request = createTokenRequest()
            val refreshToken = jwtService.generateRefreshToken(request)

            // When
            val result = jwtService.validateToken(refreshToken, TokenType.ACCESS)

            // Then
            result.isValid shouldBe false
            result.error shouldBe TokenValidationError.WRONG_TOKEN_TYPE
        }

        @Test
        fun `should reject token signed with different key`() {
            // Given
            // Generate a secret key
            val key = Jwts.SIG.HS512.key().build()
            val keyBase64 = Encoders.BASE64URL.encode(key.encoded)

            val differentKeyProperties = JwtProperties(
                secretKey = keyBase64,
                refreshTokenSecretKey = keyBase64,
                accessTokenExpiration = ACCESS_TOKEN_EXPIRATION,
                refreshTokenExpiration = REFRESH_TOKEN_EXPIRATION,
            )

            val differentKeyService = JwtService(differentKeyProperties, key)
            val request = createTokenRequest()
            val accessToken = differentKeyService.generateAccessToken(request)

            // When
            val result = jwtService.validateToken(accessToken, TokenType.ACCESS)

            // Then
            result.isValid shouldBe false
            result.error shouldBe TokenValidationError.INVALID_SIGNATURE
        }
    }

    @Nested
    @DisplayName("Token Pair Generation")
    inner class TokenPairGeneration {

        @Test
        fun `should generate both access and refresh tokens`() {
            // Given
            val request = createTokenRequest()

            // When
            val tokenPair = jwtService.generateTokenPair(request)

            // Then
            tokenPair.accessToken.shouldNotBeEmpty()
            tokenPair.refreshToken.shouldNotBeEmpty()
            tokenPair.expiresIn shouldBe ACCESS_TOKEN_EXPIRATION / 1000
            tokenPair.tokenType shouldBe "Bearer"
        }

        @Test
        fun `should validate both tokens from pair`() {
            // Given
            val request = createTokenRequest()
            val tokenPair = jwtService.generateTokenPair(request)

            // When
            val accessTokenResult = jwtService.validateToken(tokenPair.accessToken, TokenType.ACCESS)
            val refreshTokenResult = jwtService.validateToken(tokenPair.refreshToken, TokenType.REFRESH)

            // Then
            accessTokenResult.isValid shouldBe true
            refreshTokenResult.isValid shouldBe true
        }
    }

//    @Nested
//    @DisplayName("Token Refresh")
//    inner class TokenRefresh {
//
//        @Test
//        fun `should generate new access token from valid refresh token`() {
//            // Given
//            val request = createTokenRequest()
//            val refreshToken = jwtService.generateRefreshToken(request)
//
//            // When
//            val newAccessToken = jwtService.refreshAccessToken(refreshToken, request.roles)
//
//            // Then
//            newAccessToken.shouldNotBeEmpty()
//        }
//
//        @Test
//        fun `should throw exception when refreshing with expired token`() {
//            // Given
//            val shortLivedProperties = JwtProperties(
//                secretKey = TEST_SECRET_KEY,
//                refreshTokenSecretKey = TEST_SECRET_KEY,
//                accessTokenExpiration = ACCESS_TOKEN_EXPIRATION,
//                refreshTokenExpiration = -1000L,
//            )
//
//            val shortJwtService = JwtService(shortLivedProperties, secretKey)
//            val request = createTokenRequest()
//            val refreshToken = shortJwtService.generateRefreshToken(request)
//
//            Thread.sleep(5)
//
//            // When & Then
//            shouldThrow<InvalidTokenException> {
//                shortJwtService.refreshAccessToken(refreshToken, request.roles)
//            }
//        }
//
//        @Test
//        fun `should throw exception when refreshing with access token`() {
//            // Given
//            val request = createTokenRequest()
//            val accessToken = jwtService.generateAccessToken(request)
//
//            // When & Then
//            shouldThrow<InvalidTokenException> {
//                jwtService.refreshAccessToken(accessToken, request.roles)
//            }
//        }
//    }
}
