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
package io.github.aaravmahajanofficial.users

import io.github.aaravmahajanofficial.TestcontainersConfiguration
import io.github.aaravmahajanofficial.auth.jwt.JwtService
import io.github.aaravmahajanofficial.auth.jwt.TokenRequest
import io.github.aaravmahajanofficial.auth.token.RefreshTokenRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestcontainersConfiguration::class)
class UserIntegrationTests @Autowired constructor(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService,
) {

    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
        roleRepository.deleteAll()
        refreshTokenRepository.deleteAll()

        roleRepository.saveAndFlush(Role(name = RoleType.CUSTOMER))
    }

    @Test
    fun `should return 200 OK with user profile data`() {
        // Given
        val user = createUser()
        val tokenRequest = TokenRequest(user.id!!, "john.doe@example.com", setOf(RoleType.CUSTOMER, RoleType.SELLER))
        val jwt = jwtService.generateAccessToken(tokenRequest)

        // When & Then
        webTestClient.get()
            .uri("/api/v1/users/me")
            .headers { headers -> headers.setBearerAuth(jwt) }
            .exchange()
            .expectStatus().isOk
            .expectHeader()
            .contentTypeCompatibleWith(APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.data.id").isEqualTo(user.id.toString())
            .jsonPath("$.data.email").isEqualTo(user.email)
    }

    @Test
    fun `should return 404 Not Found when user does not exist`() {
        // Given
        createUser()
        val nonExistentUserId = UUID.randomUUID()
        val tokenRequest = TokenRequest(nonExistentUserId, "non.existent@example.com", setOf(RoleType.CUSTOMER))
        val jwt = jwtService.generateAccessToken(tokenRequest)

        // When & Then
        webTestClient.get()
            .uri("/api/v1/users/me")
            .headers { headers -> headers.setBearerAuth(jwt) }
            .exchange()
            .expectStatus().isNotFound
            .expectHeader()
            .contentType(APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.detail").isEqualTo("User not found with id: '$nonExistentUserId'")
    }

    private fun createUser(): User {
        val customerRole = roleRepository.findByName(RoleType.CUSTOMER) ?: error("Customer role missing in DB")

        return userRepository.saveAndFlush(
            User(
                email = "valid.user@example.com",
                passwordHash = "passwordHash",
                firstName = "Test",
                lastName = "User",
                phoneNumber = "+1234567890",
                status = UserStatus.ACTIVE,
                lastLoginAt = Instant.now(),
            ).apply { addRole(customerRole) },
        )
    }
}
