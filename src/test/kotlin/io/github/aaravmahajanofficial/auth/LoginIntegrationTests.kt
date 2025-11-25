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
import io.github.aaravmahajanofficial.auth.login.LoginRequestDto
import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import io.github.aaravmahajanofficial.users.UserStatus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class LoginIntegrationTests @Autowired constructor(
    val webTestClient: WebTestClient,
    val roleRepository: RoleRepository,
    val userRepository: UserRepository,
    val passwordEncoder: PasswordEncoder,
) {
    private lateinit var activeUser: User
    private lateinit var loginRequest: LoginRequestDto

    @BeforeEach
    fun setup() {
        val customerRole =
            roleRepository.findByName(RoleType.CUSTOMER) ?: roleRepository.save(Role(name = RoleType.CUSTOMER))

        activeUser = User(
            email = "valid.user@example.com",
            passwordHash = passwordEncoder.encode("StrongP@ss1")!!,
            firstName = "Test",
            lastName = "User",
            phoneNumber = "+1234567890",
            phoneVerified = true,
            emailVerified = true,
            status = UserStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        ).apply { addRole(customerRole) }

        userRepository.saveAndFlush(activeUser)

        loginRequest = LoginRequestDto(
            email = activeUser.email,
            password = "StrongP@ss1",
        )
    }

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
    }

    @Test
    fun `should return 200 OK when login successful with valid email`() {
        // Given
        // When
        webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange().expectStatus().isEqualTo(HttpStatus.OK)

        // Then - DB State
        val attemptedUser = userRepository.findByEmail(loginRequest.email)
        attemptedUser?.lastLoginAt.shouldNotBeNull() // DB should be updated with login timestamp
    }

    @Test
    fun `should fail with 401 on login with incorrect password`() {
        // Given
        loginRequest = loginRequest.copy(password = "wrong-password")

        // When
        webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)

        // Then
        val attemptedUser = userRepository.findByEmail(loginRequest.email)
        attemptedUser?.lastLoginAt.shouldBeNull()
    }

    @Test
    fun `should fail with 401 on login with non-existent user`() {
        // Given
        loginRequest = loginRequest.copy(email = "doesNotExist@example.com")

        // When
        webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)

        // Then
        val attemptedUser = userRepository.findByEmail(loginRequest.email)
        attemptedUser.shouldBeNull() // A user should not be created upon failed login attempt
    }

    @Test
    fun `should fail with 403 when user is suspended`() {
        // Given
        activeUser.apply { status = UserStatus.SUSPENDED }
        userRepository.saveAndFlush(activeUser)

        // When
        webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN)

        // Then
        val attemptedUser = userRepository.findByEmail(loginRequest.email)
        attemptedUser?.lastLoginAt.shouldBeNull()
    }

    @Test
    fun `should fail with 403 when user hasn't verified the email`() {
        // Given
        activeUser.apply { emailVerified = false }
        userRepository.saveAndFlush(activeUser)

        // When
        webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN)

        // Then
        val attemptedUser = userRepository.findByEmail(loginRequest.email)
        attemptedUser?.lastLoginAt.shouldBeNull()
    }
}
