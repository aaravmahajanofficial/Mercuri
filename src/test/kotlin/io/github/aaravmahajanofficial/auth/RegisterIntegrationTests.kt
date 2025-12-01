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
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.reactive.server.WebTestClient

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RegisterIntegrationTests @Autowired constructor(
    val webTestClient: WebTestClient,
    val roleRepository: RoleRepository,
    val userRepository: UserRepository,
    val passwordEncoder: PasswordEncoder,
) {
    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
        roleRepository.deleteAll()

        roleRepository.saveAndFlush(Role(name = RoleType.CUSTOMER))
    }

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
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerRequest)
            .exchange()

        // Then
        result.expectStatus().isCreated
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
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
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerRequest)
            .exchange().expectStatus().isEqualTo(HttpStatus.CONFLICT)

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
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerRequest)
            .exchange().expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)

        // Then - DB State
        userRepository.findByEmail(registerRequest.email).shouldBeNull() // User should not exist in DB
    }
}
