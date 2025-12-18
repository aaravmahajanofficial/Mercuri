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
package io.github.aaravmahajanofficial.auth

import io.github.aaravmahajanofficial.auth.events.UserLoginEvent
import io.github.aaravmahajanofficial.auth.events.UserRegisterEvent
import io.github.aaravmahajanofficial.auth.jwt.JwtService
import io.github.aaravmahajanofficial.auth.jwt.TokenType
import io.github.aaravmahajanofficial.auth.jwt.TokenValidationError
import io.github.aaravmahajanofficial.auth.jwt.TokenValidationResult
import io.github.aaravmahajanofficial.auth.login.LoginRequestDto
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.auth.token.RefreshTokenManager
import io.github.aaravmahajanofficial.auth.token.RefreshTokenRequestDto
import io.github.aaravmahajanofficial.auth.token.TokenBlacklistService
import io.github.aaravmahajanofficial.common.exception.model.AccountSuspendedException
import io.github.aaravmahajanofficial.common.exception.model.DefaultRoleNotFoundException
import io.github.aaravmahajanofficial.common.exception.model.EmailNotVerifiedException
import io.github.aaravmahajanofficial.common.exception.model.InvalidTokenException
import io.github.aaravmahajanofficial.common.exception.model.UserAlreadyExistsException
import io.github.aaravmahajanofficial.config.JwtProperties
import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import io.github.aaravmahajanofficial.users.UserStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    // Mock the injected dependencies
    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var roleRepository: RoleRepository

    @Mock
    lateinit var passwordEncoder: PasswordEncoder

    @Mock
    lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    lateinit var jwtService: JwtService

    @Mock
    lateinit var refreshTokenManager: RefreshTokenManager

    @Mock
    lateinit var jwtProperties: JwtProperties

    @Mock
    lateinit var tokenBlacklistService: TokenBlacklistService

    @InjectMocks
    lateinit var authService: AuthService

    @Nested
    inner class Registration {
        @Test
        fun `should register user successfully`() {
            // Given
            val request = createRegisterRequest()
            val customerRole = createCustomerRole()

            whenever(userRepository.findByEmail(request.email)).thenReturn(null)
            whenever(roleRepository.findByName(RoleType.CUSTOMER)).thenReturn(customerRole)
            whenever(passwordEncoder.encode(request.password)).thenReturn("hashed_password")

            val savedUser = createExistingUser()
            whenever(userRepository.saveAndFlush(any())).thenReturn(savedUser)

            // When
            val result = authService.register(request)

            // Then - Verify Side Effect (The Input to DB)
            // Verifies that the correct user object was sent to the repository by the service
            // Also, verifies the dto mapping (toUser), verifies the User JPA entity
            verify(userRepository)
                .saveAndFlush(
                    check {
                        it.email shouldBe request.email
                        it.passwordHash shouldBe "hashed_password"
                        it.createdAt.shouldBeNull()
                        it.updatedAt.shouldBeNull()
                        it.roles shouldContain customerRole
                        it.id shouldBe null
                    },
                )

            // Then - Verify Return Value
            val eventCaptor = argumentCaptor<UserRegisterEvent>()
            verify(eventPublisher).publishEvent(eventCaptor.capture())
            eventCaptor.firstValue.user.email shouldBe request.email

            // fails if service returns pre-save user & not updated user from DB
            result.id shouldBe savedUser.id
            result.createdAt shouldBe savedUser.createdAt
            result.roles shouldContain RoleType.CUSTOMER // ensure that Role assignment works
        }

        @Test
        fun `should throw UserAlreadyExistsException when email is taken`() {
            // Given
            val request = createRegisterRequest()

            whenever(userRepository.findByEmail(request.email)).thenReturn(createExistingUser())

            // When
            shouldThrow<UserAlreadyExistsException> { authService.register(request) }

            // Then
            verifyNoInteractions(passwordEncoder)
            verify(userRepository, never()).saveAndFlush(any())
        }

        @Test
        fun `should throw DefaultRoleNotFoundException when default role is missing`() {
            val request = createRegisterRequest()

            whenever(userRepository.findByEmail(request.email)).thenReturn(null)
            whenever(roleRepository.findByName(RoleType.CUSTOMER)).thenReturn(null)

            shouldThrow<DefaultRoleNotFoundException> { authService.register(request) }
        }

        private fun createRegisterRequest() = RegisterRequestDto(
            email = "john.doe@example.com",
            password = "StrongP@ss123!",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
        )
    }

    @Nested
    inner class Login {

        @Test
        fun `should login successfully and update the last login time`() {
            // Given
            val request = createLoginRequest()
            val existingUser = createExistingUser()

            whenever(userRepository.findByEmail(request.email)).thenReturn(existingUser)
            whenever(passwordEncoder.matches(request.password, existingUser.passwordHash)).thenReturn(true)

            val persistedUser = createExistingUser().apply {
                id = existingUser.id
                lastLoginAt = Instant.now()
                updatedAt = Instant.now().plusSeconds(1)
            }
            whenever(userRepository.save(any())).thenReturn(persistedUser)

            whenever(jwtService.generateAccessToken(any())).thenReturn("new.access.token")
            whenever(refreshTokenManager.createRefreshToken(any(), isNull(), isNull()))
                .thenReturn("new.refresh.token")
            whenever(jwtService.accessTokenExpiration()).thenReturn(900L)

            // When
            val result = authService.login(request)

            // Then
            // 1. Timestamp must be updated
            verify(userRepository)
                .save(
                    check<User> {
                        it.lastLoginAt.shouldNotBeNull() // Confirm service actually set timestamp before saving
                    },
                )

            // 2. Verify Event emitted
            val eventCaptor = argumentCaptor<UserLoginEvent>()
            verify(eventPublisher).publishEvent(eventCaptor.capture())
            eventCaptor.firstValue.user.email shouldBe request.email

            // 3. Token generation
            verify(jwtService).generateAccessToken(any())
            verify(refreshTokenManager).createRefreshToken(
                check {
                    it.updatedAt shouldBe persistedUser.updatedAt
                },
                isNull(),
                isNull(),
            )

            // 4. Response DTO correctness
            result.accessToken shouldBe "new.access.token"
            result.refreshToken shouldBe "new.refresh.token"
            result.expiresIn shouldBe 900L
            result.tokenType shouldBe "Bearer"
            result.authStatus shouldBe AuthStatus.VERIFIED

            // 5. Response should contain the updated lastLoginAt
            result.user.lastLoginAt shouldBe persistedUser.lastLoginAt
        }

        @Test
        fun `should throw BadCredentialsException when password mismatch`() {
            val request = createLoginRequest()
            val existingUser = createExistingUser()

            whenever(userRepository.findByEmail(request.email)).thenReturn(existingUser)
            whenever(passwordEncoder.matches(request.password, existingUser.passwordHash)).thenReturn(false)

            shouldThrow<BadCredentialsException> { authService.login(request) }
        }

        @Test
        fun `should throw BadCredentialsException when user not found`() {
            // Given
            val request = createLoginRequest()

            whenever(userRepository.findByEmail(request.email)).thenReturn(null)

            shouldThrow<BadCredentialsException> { authService.login(request) }
        }

        @Test
        fun `should throw AccountSuspendedException when user account is suspended`() {
            val request = createLoginRequest()
            val suspendedUser = createExistingUser(UserStatus.SUSPENDED)

            whenever(userRepository.findByEmail(request.email)).thenReturn(suspendedUser)
            whenever(passwordEncoder.matches(request.password, suspendedUser.passwordHash)).thenReturn(true)

            shouldThrow<AccountSuspendedException> { authService.login(request) }
        }

        @Test
        fun `should throw EmailNotVerifiedException when email is not verified`() {
            val request = createLoginRequest()
            val existingUser = createExistingUser(emailVerified = false)

            whenever(userRepository.findByEmail(request.email)).thenReturn(existingUser)
            whenever(passwordEncoder.matches(request.password, existingUser.passwordHash)).thenReturn(true)

            shouldThrow<EmailNotVerifiedException> { authService.login(request) }
        }

        private fun createLoginRequest() = LoginRequestDto(
            email = "john.doe@example.com",
            password = "StrongP@ss123!",
        )
    }

    @Nested
    inner class Refresh {

        @BeforeEach
        fun setUp() {
            lenient().whenever(jwtProperties.accessTokenExpiration).thenReturn(900_000L)
        }

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
            val result = authService.refreshAccessToken(createRefreshAccessTokenRequest(refreshToken))

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

            whenever(jwtService.validateToken(refreshToken, TokenType.REFRESH)).thenReturn(
                createTokenValidationResult(),
            )
            whenever(refreshTokenManager.isTokenValid(any())).thenReturn(false)

            // When & Then
            val exception =
                shouldThrow<InvalidTokenException> {
                    authService.refreshAccessToken(
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
                    authService.refreshAccessToken(
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
                authService.refreshAccessToken(createRefreshAccessTokenRequest(refreshToken))
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
                authService.refreshAccessToken(createRefreshAccessTokenRequest(refreshToken))
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
                authService.refreshAccessToken(createRefreshAccessTokenRequest(refreshToken))
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
                authService.refreshAccessToken(createRefreshAccessTokenRequest(refreshToken))
            }
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
    }

    private fun createCustomerRole() = Role(name = RoleType.CUSTOMER)

    private fun createExistingUser(status: UserStatus = UserStatus.ACTIVE, emailVerified: Boolean = true): User = User(
        email = "john.doe@example.com",
        passwordHash = "hashed_password",
        firstName = "John",
        lastName = "Doe",
        phoneNumber = "+1234567890",
        emailVerified = emailVerified,
        phoneVerified = true,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    ).apply {
        id = UUID.randomUUID()
        addRole(createCustomerRole())
    }
}
