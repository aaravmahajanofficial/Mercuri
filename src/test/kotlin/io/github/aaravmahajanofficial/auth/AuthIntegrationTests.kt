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

import io.github.aaravmahajanofficial.TestcontainersConfiguration
import io.github.aaravmahajanofficial.auth.jwt.JwtService
import io.github.aaravmahajanofficial.auth.login.LoginRequestDto
import io.github.aaravmahajanofficial.auth.login.LoginResponseDto
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.auth.token.RefreshTokenRepository
import io.github.aaravmahajanofficial.auth.token.RefreshTokenRequestDto
import io.github.aaravmahajanofficial.auth.token.RefreshTokenResponseDto
import io.github.aaravmahajanofficial.common.ApiResponse
import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import io.github.aaravmahajanofficial.users.UserStatus
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.time.Instant
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestcontainersConfiguration::class)
class AuthIntegrationTests @Autowired constructor(
    private val webTestClient: WebTestClient,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService,
    private val redisTemplate: StringRedisTemplate,
) {
    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
        roleRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()

        roleRepository.saveAndFlush(Role(name = RoleType.CUSTOMER))
    }

    @Nested
    @DisplayName("User Registration")
    inner class Registration {
        @Test
        fun `should return 201 if user registration successful`() {
            // Given
            val registerRequest = RegisterRequestDto(
                email = "john.doe@example.com",
                password = "SecureP@ss123",
                firstName = "John",
                lastName = "Doe",
                phoneNumber = "+1234567890",
            )

            // When
            val result = webTestClient.post().uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .bodyValue(registerRequest)
                .exchange()

            // Then
            result.expectStatus().isCreated
                .expectHeader()
                .contentType(APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.data.id").isNotEmpty
                .jsonPath("$.data.email").isEqualTo(registerRequest.email)
                .jsonPath("$.data.roles").isArray
                .jsonPath("$.data.roles[0]").isEqualTo(RoleType.CUSTOMER.value)
                .jsonPath("$.data.createdAt").isNotEmpty

            // Then - DB State
            val savedUser = userRepository.findByEmail(registerRequest.email)
            savedUser.shouldNotBeNull() // User should be persisted in the database
            savedUser.email shouldBe registerRequest.email
            savedUser.roles.shouldHaveSize(1)
            savedUser.roles.first().name shouldBe RoleType.CUSTOMER
            savedUser.passwordHash shouldNotBe registerRequest.password // Password must be hashed before saving to DB
            passwordEncoder.matches(registerRequest.password, savedUser.passwordHash).shouldBeTrue()
        }

        @Test
        fun `should return 409 on duplicate email`() { // tests unique email constraint and domain handling
            // Given
            userRepository.saveAndFlush(
                User(
                    email = "john.doe@example.com",
                    passwordHash = "hashed_password",
                    firstName = "Johnny",
                    lastName = "Doe",
                    phoneNumber = "+1111111111",
                ),
            )

            // Try to register with same email
            val registerRequest = RegisterRequestDto(
                email = "john.doe@example.com", // Duplicate Email
                password = "SecureP@ss123",
                firstName = "Bob",
                lastName = "Doe",
                phoneNumber = "+1234567890",
            )

            // When
            webTestClient.post().uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .bodyValue(registerRequest)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)

            // Then - DB State
            userRepository.count() shouldBe 1 // Constraint ensures no duplicate records
        }

        @Test
        fun `should return 422 for invalid input data`() {
            val registerRequest = RegisterRequestDto(
                email = "not-an-email",
                password = "123",
                firstName = "",
                lastName = "",
                phoneNumber = "",
            )

            // When
            webTestClient.post().uri("/api/v1/auth/register")
                .contentType(APPLICATION_JSON)
                .bodyValue(registerRequest)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)

            // Then - DB State
            userRepository.findByEmail(registerRequest.email).shouldBeNull() // User should not exist in DB
        }
    }

    @Nested
    @DisplayName("User Login")
    inner class Login {
        @Test
        fun `should return 200 OK when login successful with valid email`() {
            // Given
            createUser()

            // When
            val result = webTestClient.post().uri("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .bodyValue(loginRequest())
                .exchange()

            result.expectStatus().isOk
                .expectBody()
                .jsonPath("$.data.accessToken").isNotEmpty
                .jsonPath("$.data.refreshToken").isNotEmpty
                .jsonPath("$.data.tokenType").isEqualTo("Bearer")
                .jsonPath("$.data.expiresIn").isNumber
                .jsonPath("$.data.authStatus").isEqualTo(AuthStatus.VERIFIED.value)

            // Then - DB State
            // DB should be updated with login timestamp
            userRepository.findByEmail(loginRequest().email)?.lastLoginAt.shouldNotBeNull()
        }

        @Test
        fun `should return 401 on incorrect password`() {
            // Given
            val wrongRequest = loginRequest().copy(password = "wrong-password")

            // When
            webTestClient.post().uri("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .bodyValue(wrongRequest)
                .exchange()
                .expectStatus().isUnauthorized

            // Then
            userRepository.findByEmail(wrongRequest.email)?.lastLoginAt.shouldBeNull()
        }

        @Test
        fun `should return 401 when user does not exist`() {
            // Given
            val wrongRequest = loginRequest().copy(email = "doesNotExist@example.com")

            // When
            webTestClient.post().uri("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .bodyValue(wrongRequest)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)

            // Then
            // A user should not be created upon failed login attempt
            userRepository.findByEmail(wrongRequest.email).shouldBeNull()
        }

        @Test
        fun `should fail with 403 when user is suspended`() {
            // Given
            createUser(UserStatus.SUSPENDED)

            // When
            webTestClient.post().uri("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .bodyValue(loginRequest())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)

            // Then
            userRepository.findByEmail(loginRequest().email)?.lastLoginAt.shouldBeNull()
        }

        @Test
        fun `should fail with 403 when email is not verified`() {
            // Given
            createUser(emailVerified = false)

            // When
            webTestClient.post().uri("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .bodyValue(loginRequest())
                .exchange()
                .expectStatus().isForbidden

            // Then
            userRepository.findByEmail(loginRequest().email)?.lastLoginAt.shouldBeNull()
        }

        @Test
        fun `should return 422 for invalid login input`() {
            // Given
            val request = loginRequest(
                email = "not-valid",
                password = "",
            )

            // When
            webTestClient.post().uri("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)

            // Then
            userRepository.findByEmail(loginRequest().email)?.lastLoginAt.shouldBeNull()
        }
    }

    @Nested
    @DisplayName("Refresh Token")
    inner class RefreshToken {
        @Test
        fun `should rotate refresh token and return new access token`() {
            // Given
            createUser()
            val loginResponse = performLogin()
            val originalRefreshToken = loginResponse.refreshToken
            val refreshTokenRequest = RefreshTokenRequestDto(originalRefreshToken)

            // When
            val result = webTestClient.post()
                .uri("/api/v1/auth/refresh")
                .contentType(APPLICATION_JSON)
                .bodyValue(refreshTokenRequest)
                .exchange()
                .expectStatus().isOk
                .expectBody<ApiResponse.Success<RefreshTokenResponseDto>>()
                .returnResult()
                .responseBody!!.data

            result.accessToken.shouldNotBeEmpty()
            result.refreshToken.shouldNotBeEmpty()
            result.refreshToken shouldNotBe originalRefreshToken

            // Then - Verify old token is marked "revoked"
            val claims = jwtService.extractAllClaims(originalRefreshToken)
            refreshTokenRepository.findById(UUID.fromString(claims.id)).get().revoked.shouldBeTrue()
        }
    }

    @Nested
    @DisplayName("User Logout")
    inner class Logout {

        private lateinit var accessToken: String
        private lateinit var refreshToken: String

        @BeforeEach
        fun setUp() {
            createUser()

            val response = performLogin()

            accessToken = response.accessToken
            refreshToken = response.refreshToken
        }

        @Test
        fun `should blacklist the specific access token`() {
            // Given
            // When
            webTestClient.post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .exchange()
                .expectStatus().isNoContent

            // Then
            // 1. Verify if the key is added to redis
            val claims = jwtService.extractAllClaims(accessToken)
            val blacklistKey = "jwt:bl:token:${claims.id}"

            redisTemplate.hasKey(blacklistKey).shouldBeTrue()

            // 2. Verify Access is Denied
            webTestClient.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .exchange()
                .expectStatus().isUnauthorized
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Token has been revoked")
        }

        @Test
        fun `should revoke the refresh token if provided in body`() {
            // Given
            val refreshTokenDto = RefreshTokenRequestDto(refreshToken)

            // When
            webTestClient.post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(APPLICATION_JSON)
                .bodyValue(refreshTokenDto)
                .exchange()
                .expectStatus().isNoContent

            // Then
            refreshTokenRepository.findAll().first().revoked.shouldBeTrue()
        }
    }

    @Nested
    @DisplayName("User Logout From All Devices")
    inner class LogoutAll {

        private lateinit var accessToken: String
        private lateinit var refreshToken: String

        @BeforeEach
        fun setUp() {
            createUser()
            val response = performLogin()
            accessToken = response.accessToken
            refreshToken = response.refreshToken
        }

        @Test
        fun `should invalidate ALL the tokens before the logout`() {
            // Given - User logs in on two different "devices"
            val loginResponse1 = performLogin()
            val tokenDevice1 = loginResponse1.accessToken

            Thread.sleep(100)

            val loginResponse2 = performLogin()
            val tokenDevice2 = loginResponse2.accessToken

            // When
            webTestClient.post()
                .uri("/api/v1/auth/logout-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $tokenDevice1")
                .exchange()
                .expectStatus().isNoContent

            // Then - Verify Access is Denied on both "devices"
            // Check Device 1
            webTestClient.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $tokenDevice1")
                .exchange()
                .expectStatus().isUnauthorized
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Token has been revoked")

            // Check Device 2
            webTestClient.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $tokenDevice2")
                .exchange()
                .expectStatus().isUnauthorized
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Token has been revoked")

            // Check that NEW logins still work, and the user is not permanently banned
            Thread.sleep(1000)

            val tokenDevice3 = performLogin().accessToken
            webTestClient.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $tokenDevice3")
                .exchange()
                .expectStatus().isOk
        }
    }

    private fun performLogin(): LoginResponseDto = webTestClient.post().uri("/api/v1/auth/login")
        .contentType(APPLICATION_JSON)
        .bodyValue(loginRequest())
        .exchange()
        .expectStatus().isOk
        .expectBody<ApiResponse.Success<LoginResponseDto>>()
        .returnResult()
        .responseBody!!.data

    private fun createUser(userStatus: UserStatus = UserStatus.ACTIVE, emailVerified: Boolean = true): User {
        val customerRole = roleRepository.findByName(RoleType.CUSTOMER) ?: error("Customer role missing in DB")

        return userRepository.saveAndFlush(
            User(
                email = "valid.user@example.com",
                passwordHash = passwordEncoder.encode("StrongP@ss1")!!,
                firstName = "Test",
                lastName = "User",
                phoneNumber = "+1234567890",
                phoneVerified = true,
                emailVerified = emailVerified,
                status = userStatus,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ).apply { addRole(customerRole) },
        )
    }

    private fun loginRequest(email: String = "valid.user@example.com", password: String = "StrongP@ss1") =
        LoginRequestDto(
            email = email,
            password = password,
        )
}
