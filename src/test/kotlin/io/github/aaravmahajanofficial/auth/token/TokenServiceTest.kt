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
package io.github.aaravmahajanofficial.auth.token

import io.github.aaravmahajanofficial.auth.jwt.JwtService
import io.github.aaravmahajanofficial.auth.jwt.TokenType
import io.github.aaravmahajanofficial.auth.jwt.TokenValidationError
import io.github.aaravmahajanofficial.auth.jwt.TokenValidationResult
import io.github.aaravmahajanofficial.common.exception.AccountSuspendedException
import io.github.aaravmahajanofficial.common.exception.EmailNotVerifiedException
import io.github.aaravmahajanofficial.common.exception.InvalidTokenException
import io.github.aaravmahajanofficial.config.JwtProperties
import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import io.github.aaravmahajanofficial.users.UserStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TokenServiceTest {

    @Mock
    lateinit var jwtService: JwtService

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var jwtProperties: JwtProperties

    @Mock
    lateinit var refreshTokenManager: RefreshTokenManager

    private lateinit var tokenService: TokenService

    @BeforeEach
    fun setUp() {
        tokenService = TokenService(jwtService, userRepository, jwtProperties, refreshTokenManager)

        lenient().whenever(jwtProperties.accessTokenExpiration).thenReturn(900_000L)
    }

    private fun createExistingUser(emailVerified: Boolean = true): User = User(
        email = "john.doe@example.com",
        passwordHash = "hashed_password",
        firstName = "John",
        lastName = "Doe",
        phoneNumber = "+1234567890",
        emailVerified = emailVerified,
        phoneVerified = true,
        status = UserStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    ).apply {
        id = UUID.randomUUID()
        addRole(Role(name = RoleType.CUSTOMER))
    }

    private fun createTokenValidationResult(email: String? = null) = TokenValidationResult(
        isValid = true,
        jti = UUID.randomUUID(),
        userID = UUID.randomUUID(),
        email = email,
        roles = setOf(RoleType.CUSTOMER),
    )

    private fun createRefreshAccessTokenRequest(refreshToken: String) = RefreshTokenRequestDto(
        refreshToken = refreshToken,
    )

    @Test
    fun `should refresh token successfully and include latest user roles`() {
        // Given
        val refreshToken = "valid.refresh.token"
        val newAccessToken = "new.access.token"
        val newRefreshToken = "new.refresh.token"
        val user = createExistingUser().apply {
            addRole(Role(RoleType.SELLER))
        }

        whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
            createTokenValidationResult(user.email),
        )
        whenever(refreshTokenManager.isTokenValid(any())).thenReturn(true)
        whenever(userRepository.findByEmail(user.email)).thenReturn(user)
        whenever(refreshTokenManager.rotateRefreshToken(any(), any())).thenReturn(newRefreshToken)
        whenever(jwtService.generateAccessToken(any())).thenReturn(newAccessToken)

        // When
        val result = tokenService.refreshAccessToken(createRefreshAccessTokenRequest(refreshToken))

        // Then
        result.accessToken shouldBe newAccessToken
        result.expiresIn shouldBe 900L

        // Verify the new token was created using user's updated roles from DB
        verify(jwtService).generateAccessToken(
            check {
                it.roles shouldContainExactlyInAnyOrder setOf(RoleType.CUSTOMER, RoleType.SELLER)
            },
        )
    }

    @Test
    fun `should throw InvalidTokenException on revoked or invalid token`() {
        // Given
        val refreshToken = "revoked.refresh.token"

        whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(createTokenValidationResult())
        whenever(refreshTokenManager.isTokenValid(any())).thenReturn(false)

        // When & Then
        val exception =
            shouldThrow<InvalidTokenException> {
                tokenService.refreshAccessToken(
                    createRefreshAccessTokenRequest(refreshToken),
                )
            }

        exception.error shouldBe TokenValidationError.INVALID_SIGNATURE
    }

    @Test
    fun `should throw InvalidTokenException when token validation fails`() {
        // Given
        val refreshToken = "invalid.token"

        whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
            TokenValidationResult.invalid(
                TokenValidationError.INVALID_SIGNATURE,
            ),
        )

        // When & Then
        val exception =
            shouldThrow<InvalidTokenException> {
                tokenService.refreshAccessToken(
                    createRefreshAccessTokenRequest(refreshToken),
                )
            }

        exception.message shouldBe "Invalid refresh token"
        exception.error shouldBe TokenValidationError.INVALID_SIGNATURE
    }

    @Test
    fun `should throw InvalidTokenException when email claim is missing`() {
        // Given
        val refreshToken = "valid.token.no.email"

        whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
            createTokenValidationResult(email = null),
        )
        whenever(refreshTokenManager.isTokenValid(any())).thenReturn(true)

        // When & Then
        val exception = shouldThrow<InvalidTokenException> {
            tokenService.refreshAccessToken(createRefreshAccessTokenRequest(refreshToken))
        }
        exception.message shouldBe "Missing email claim"
        exception.error shouldBe TokenValidationError.MISSING_CLAIMS
    }

    @Test
    fun `should throw InvalidTokenException when user does not exist`() {
        // Given
        val refreshToken = "valid.access.token"
        val userEmail = "unknown@example.com"

        whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
            createTokenValidationResult(userEmail),
        )
        whenever(refreshTokenManager.isTokenValid(any())).thenReturn(true)
        whenever(userRepository.findByEmail(userEmail)).thenReturn(null)

        // When & Then
        val exception = shouldThrow<InvalidTokenException> {
            tokenService.refreshAccessToken(createRefreshAccessTokenRequest(refreshToken))
        }
        exception.message shouldBe "User not found"
        exception.error shouldBe TokenValidationError.MISSING_CLAIMS
    }

    @Test
    fun `should throw EmailNotVerifiedException when user email is not verified`() {
        // Given
        val refreshToken = "valid.refresh.token"
        val user = createExistingUser(emailVerified = false)

        whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
            createTokenValidationResult(user.email),
        )
        whenever(refreshTokenManager.isTokenValid(any())).thenReturn(true)
        whenever(userRepository.findByEmail(user.email)).thenReturn(user)

        // When & Then
        shouldThrow<EmailNotVerifiedException> {
            tokenService.refreshAccessToken(createRefreshAccessTokenRequest(refreshToken))
        }
    }

    @Test
    fun `should throw AccountSuspendedException when user account is suspended`() {
        // Given
        val refreshToken = "valid.refresh.token"
        val user = createExistingUser().apply { status = UserStatus.SUSPENDED }

        whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
            createTokenValidationResult(user.email),
        )
        whenever(refreshTokenManager.isTokenValid(any())).thenReturn(true)
        whenever(userRepository.findByEmail(user.email)).thenReturn(user)

        // When & Then
        shouldThrow<AccountSuspendedException> {
            tokenService.refreshAccessToken(createRefreshAccessTokenRequest(refreshToken))
        }
    }
}
