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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
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

    private lateinit var tokenService: TokenService

    @BeforeEach
    fun setUp() {
        tokenService = TokenService(jwtService, userRepository, jwtProperties)

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

    private fun createTokenValidationResult(email: String) = TokenValidationResult(
        isValid = true,
        userID = UUID.randomUUID(),
        email = email,
        roles = setOf(RoleType.CUSTOMER),
    )

    private fun createRefreshTokenRequest(refreshToken: String) = RefreshTokenRequestDto(
        refreshToken = refreshToken,
    )

    @Nested
    @DisplayName("Successful Refresh")
    inner class SuccessfulRefresh {

        @Test
        fun `should refresh token successfully`() {
            // Given
            val refreshToken = "valid.refresh.token"
            val user = createExistingUser()

            whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
                createTokenValidationResult(user.email),
            )
            whenever(userRepository.findByEmail(user.email)).thenReturn(createExistingUser())

            val newAccessToken = "new.access.token"
            whenever(jwtService.refreshAccessToken(refreshToken, setOf(RoleType.CUSTOMER))).thenReturn(
                newAccessToken,
            )

            // When
            val result = tokenService.refreshToken(createRefreshTokenRequest(refreshToken))

            // Then
            result.accessToken shouldBe newAccessToken
            result.expiresIn shouldBe 900_000L / 1000
        }
    }

    @Nested
    @DisplayName("Token Validation Failures")
    inner class TokenValidationFailures {

        @Test
        fun `should throw when refresh token is invalid`() {
            // Given
            val refreshToken = "invalid.token"

            whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
                TokenValidationResult.invalid(
                    TokenValidationError.INVALID_SIGNATURE,
                ),
            )

            // When & Then
            shouldThrow<InvalidTokenException> { tokenService.refreshToken(createRefreshTokenRequest(refreshToken)) }
        }

        @Test
        fun `should throw when refresh token is expired`() {
            // Given
            val refreshToken = "expired.token"

            whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
                TokenValidationResult.invalid(
                    TokenValidationError.EXPIRED,
                ),
            )
            // When & Then
            shouldThrow<InvalidTokenException> {
                tokenService.refreshToken(createRefreshTokenRequest(refreshToken))
            }
        }
    }

    @Nested
    @DisplayName("User Validation Failures")
    inner class UserValidationFailures {

        @Test
        fun `should throw when user does not exist`() {
            // Given
            val refreshToken = "valid.access.token"
            val userEmail = "non_existing@example.com"

            whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
                createTokenValidationResult(userEmail),
            )
            whenever(userRepository.findByEmail(userEmail)).thenReturn(null)

            // When & Then
            shouldThrow<InvalidTokenException> {
                tokenService.refreshToken(createRefreshTokenRequest(refreshToken))
            }
        }

        @Test
        fun `should throw when user email is not verified`() {
            // Given
            val refreshToken = "valid.refresh.token"
            val user = createExistingUser(emailVerified = false)

            whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
                createTokenValidationResult(user.email),
            )
            whenever(userRepository.findByEmail(user.email)).thenReturn(user)

            // When & Then
            shouldThrow<InvalidTokenException> {
                tokenService.refreshToken(createRefreshTokenRequest(refreshToken))
            }
        }
    }

    @Nested
    @DisplayName("Role Handling")
    inner class RoleHandling {

        @Test
        fun `should pass correct roles to refreshAccessToken()`() {
            // Given
            val refreshToken = "valid.refresh.token"
            val user = createExistingUser().apply {
                addRole(Role(RoleType.SELLER))
            }

            whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
                createTokenValidationResult(user.email),
            )
            whenever(userRepository.findByEmail(user.email)).thenReturn(user)

            whenever(jwtService.refreshAccessToken(any(), any())).thenReturn(
                "new.access.token",
            )

            // When
            tokenService.refreshToken(createRefreshTokenRequest(refreshToken))

            // Then
            verify(jwtService).refreshAccessToken(
                eq(refreshToken),
                check { roles ->
                    roles shouldContainExactlyInAnyOrder setOf(RoleType.CUSTOMER, RoleType.SELLER)
                },
            )
        }
    }
}
