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

import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTests @Autowired constructor(
    val webTestClient: WebTestClient,
    val roleRepository: RoleRepository,
) {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine")
    }

    @BeforeEach
    fun setup() {
        roleRepository.findByName(RoleType.CUSTOMER) ?: roleRepository.save(Role(name = RoleType.CUSTOMER))
    }

    @AfterEach
    fun tearDown() {
        roleRepository.deleteAll()
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
            .jsonPath("$.data.roles").isEqualTo(listOf(RoleType.CUSTOMER.name))
            .jsonPath("$.meta.timeStamp").isNotEmpty
            .jsonPath("$.data.createdAt").isNotEmpty
    }
}
