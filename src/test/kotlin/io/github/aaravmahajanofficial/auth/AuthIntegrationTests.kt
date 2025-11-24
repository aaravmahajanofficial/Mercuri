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
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthIntegrationTests @Autowired constructor(
    val webTestClient: WebTestClient,
    val roleRepository: RoleRepository,
    val userRepository: UserRepository,
) {
    @BeforeEach
    fun setup() {
        if (roleRepository.findByName(RoleType.CUSTOMER) == null) {
            roleRepository.save(Role(name = RoleType.CUSTOMER))
        }
    }

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
    }

    @Test
    fun `should register a new user successfully`() {
        // Given
        val request = RegisterRequestDto(
            email = "john.doe@example.com",
            username = "john_doe_123",
            password = "SecureP@ss123",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
        )

        // When
        val result = webTestClient.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()

        // Then - API contract
        result.expectStatus().isEqualTo(HttpStatus.CREATED)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.data.id").exists()
            .jsonPath("$.data.email").isEqualTo(request.email)
            .jsonPath("$.data.username").isEqualTo(request.username)
            .jsonPath("$.data.phoneNumber").isEqualTo(request.phoneNumber)
            .jsonPath("$.data.status").isEqualTo(UserStatus.ACTIVE.value)
            .jsonPath("$.data.emailVerified").isEqualTo(false)
            .jsonPath("$.data.roles").isEqualTo(listOf(RoleType.CUSTOMER.value))
            .jsonPath("$.data.createdAt").isNotEmpty
            .jsonPath("$.meta.timestamp").isNotEmpty

        // Then - DB State
        val users = userRepository.findAll()
        assertEquals(1,  users.size, "Exactly one user should be persisted")
        val persistedUser = users.first()
        assertEquals(request.email, persistedUser.email)
    }

    @Test
    fun `should return 409 Conflict when email already exists`() { // tests unique email constraint and domain handling
        // Given
        val existingUser = User(
            email = "john.doe@example.com",
            username = "already_existing_user",
            passwordHash = "hashed_password",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1987654321",
        )

        userRepository.save(existingUser)

        // Try to register with same email

        val request = RegisterRequestDto(
            email = "john.doe@example.com", // same email as existingUser
            username = "john_doe_123",
            password = "SecureP@ss123",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
        )

        // When
        val result = webTestClient.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()

        // Then - API contract
        result.expectStatus().isEqualTo(HttpStatus.CONFLICT) // 409 Conflict
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(HttpStatus.CONFLICT.value())
            .jsonPath("$.title").isEqualTo("User Already Exists")
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists()

        // Then - DB State
        assertEquals(1, userRepository.count(), "Email conflict must not create additional users")
    }

    @Test
    fun `should return 422 Unprocessable Content on validation failure`() {
        val invalidRequest = RegisterRequestDto(
            email = "not-an-email",
            username = "",
            password = "123",
            firstName = "",
            lastName = "",
            phoneNumber = "",
        )

        // When
        val result = webTestClient.post().uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()

        // Then - API contract
        result.expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value())
            .jsonPath("$.title").isEqualTo("Validation Failed")
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists()
            .jsonPath("$.validationErrors").isArray
            .jsonPath("$.validationErrors[?(@.field=='email')]").exists()

        // Then - DB State
        assertEquals(0, userRepository.count(), "Validation failure must not persist any user")
    }
}
