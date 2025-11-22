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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient

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

    @BeforeEach
    fun setup() {
        val customerRole =
            roleRepository.findByName(RoleType.CUSTOMER) ?: roleRepository.save(Role(name = RoleType.CUSTOMER))

        activeUser = User(
            email = "valid.user@example.com",
            username = "valid_user_123",
            passwordHash = passwordEncoder.encode("StrongP@ss1")!!,
            firstName = "Test",
            lastName = "User",
            phoneNumber = "+1234567890",
            phoneVerified = true,
            emailVerified = true,
            status = UserStatus.ACTIVE,
        ).apply { addRole(customerRole) }

        userRepository.save(activeUser)
    }

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
        roleRepository.deleteAll()
    }

    @Test
    fun `should login successfully with valid email`() {
        // Given
        val request = LoginRequestDto(
            id = "valid.user@example.com",
            password = "StrongP@ss1",
        )

        // When
        val result = webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(request)
            .exchange()

        // Then
        result.expectStatus().isOk
            .expectHeader()
            .contentType(APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.data.token").isNotEmpty
            .jsonPath("$.data.email").isEqualTo(activeUser.email)
            .jsonPath("$.data.username").isEqualTo(activeUser.username)
            .jsonPath("$.data.status").isEqualTo(AuthStatus.PENDING_VERIFICATION)
            .jsonPath("$.data.roles").isEqualTo(listOf(RoleType.CUSTOMER.name))
            .jsonPath("$.meta.timestamp").isNotEmpty
    }

    @Test
    fun `should login successfully with valid username`() { // test database has a UNIQUE constraint
        // Given
        val request = LoginRequestDto(
            id = "valid_user_123",
            password = "StrongP@ss1",
        )

        // When
        val result = webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(request)
            .exchange()

        // Then
        result.expectStatus().isOk
            .expectHeader()
            .contentType(APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.data.token").isNotEmpty
            .jsonPath("$.data.email").isEqualTo(activeUser.email)
            .jsonPath("$.data.username").isEqualTo(activeUser.username)
            .jsonPath("$.data.status").isEqualTo(AuthStatus.PENDING_VERIFICATION)
            .jsonPath("$.data.roles").isEqualTo(listOf(RoleType.CUSTOMER.name))
            .jsonPath("$.meta.timestamp").isNotEmpty
    }

    @Test
    fun `should fail with 401 Unauthorized for invalid password`() {
        // Given
        val request = LoginRequestDto(
            id = "valid_user_123",
            password = "wrong-password",
        )

        // When
        val result = webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(request)
            .exchange()

        // Then
        result.expectStatus().isUnauthorized
            .expectHeader()
            .contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.title").isEqualTo("Authentication Failed")
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists()
    }

    @Test
    fun `should fail with 401 Unauthorized for non-existent user`() {
        // Given
        val request = LoginRequestDto(
            id = "ghostUser@example.com",
            password = "StrongP@ss1",
        )

        // When
        val result = webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(request)
            .exchange()

        // Then
        result.expectStatus().isUnauthorized
            .expectHeader()
            .contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.title").isEqualTo("Authentication Failed")
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists()
    }

    @Test
    fun `should fail with 401 Unauthorized when user is suspended`() {
        // Given
        val suspendedUser = User(
            email = "valid_user@example.com",
            username = "valid_user_123",
            passwordHash = passwordEncoder.encode("StrongP@ss1")!!,
            firstName = "Test",
            lastName = "User",
            phoneNumber = "+1234567890",
            status = UserStatus.SUSPENDED,
        ).apply { addRole(roleRepository.findByName(RoleType.CUSTOMER)!!) }

        userRepository.save(suspendedUser)

        val request = LoginRequestDto(
            id = "valid-user@example.com",
            password = "StrongP@ss1",
        )

        // When
        val result = webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(request)
            .exchange()

        // Then
        result.expectStatus().isUnauthorized
            .expectHeader()
            .contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.title").isEqualTo("Authentication Failed")
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists()
    }

    @Test
    fun `should fail with 403 Forbidden when user hasn't verified the email`() {
        // Given
        val unverifiedUser = User(
            email = "valid_user@example.com",
            username = "valid_user_123",
            passwordHash = passwordEncoder.encode("StrongP@ss1")!!,
            firstName = "Test",
            lastName = "User",
            phoneNumber = "+1234567890",
            emailVerified = false,
        ).apply { addRole(roleRepository.findByName(RoleType.CUSTOMER)!!) }

        userRepository.save(unverifiedUser)

        val request = LoginRequestDto(
            id = "valid-user@example.com",
            password = "StrongP@ss1",
        )

        // When
        val result = webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(request)
            .exchange()

        // Then
        result.expectStatus().isForbidden
            .expectHeader()
            .contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(403)
            .jsonPath("$.title").isEqualTo("Account unverified")
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists()
    }

    @Test
    fun `should return 422 Unprocessable Content on validation failure`() {
        // verifies that the framework actually triggers the validation logic during a real request
        val request = mapOf("id" to "")

        // When
        val result = webTestClient.post().uri("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .bodyValue(request)
            .exchange()

        // Then
        result.expectStatus().isEqualTo(422)
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(422)
            .jsonPath("$.title").isEqualTo("Validation Failed")
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists()
            .jsonPath("$.validationErrors").isArray
            .jsonPath("$.validationErrors").isNotEmpty
    }
}
