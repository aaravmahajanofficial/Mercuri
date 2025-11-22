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
import io.github.aaravmahajanofficial.auth.register.RequestDto
import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.test.web.reactive.server.WebTestClient

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
        roleRepository.deleteAll()
    }

    @Test
    fun `should register a new user successfully`() {
        // Given
        val request = RequestDto(
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

        // Then
        result.expectStatus().isCreated
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.data.id").exists()
            .jsonPath("$.data.email").isEqualTo(request.email)
            .jsonPath("$.data.username").isEqualTo(request.username)
            .jsonPath("$.data.phoneNumber").isEqualTo(request.phoneNumber)
            .jsonPath("$.data.status").isEqualTo(AuthStatus.PENDING_VERIFICATION)
            .jsonPath("$.data.emailVerified").isEqualTo(false)
            .jsonPath("$.data.roles").isEqualTo(listOf(RoleType.CUSTOMER.value))
            .jsonPath("$.data.createdAt").isNotEmpty
            .jsonPath("$.meta.timestamp").isNotEmpty
    }

    @Test
    fun `should return 409 Conflict when email already exists`() { // test database has a UNIQUE constraint
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

        val request = RequestDto(
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

        // Then
        result.expectStatus().is4xxClientError // 409 Conflict
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(409)
            .jsonPath("$.title").isEqualTo("Resource Conflict")
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists()
    }

    @Test
    fun `should return 422 Unprocessable Content on validation failure`() {
        val invalidRequest = RequestDto(
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

        // Then
        result.expectStatus().is4xxClientError
            .expectHeader().contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(422)
            .jsonPath("$.title").isEqualTo("Validation Failed")
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists()
            .jsonPath("$.validationErrors").isArray
            .jsonPath("$.validationErrors[?(@.field=='email')]").exists()
    }
}
