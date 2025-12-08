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
import io.github.aaravmahajanofficial.auth.jwt.TokenRequest
import io.github.aaravmahajanofficial.users.RoleType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class LogoutIntegrationTests @Autowired constructor(
    private val webTestClient: WebTestClient,
    private val jwtService: JwtService,
) {

    @Test
    fun `should return 204 No Content when logout succeeds`() {
        // Given
        val tokenRequest = TokenRequest(
            userID = UUID.randomUUID(),
            email = "test@example.com",
            roles = setOf(RoleType.CUSTOMER),
        )

        val jwt = jwtService.generateAccessToken(tokenRequest)

        // When
        val result = webTestClient.post()
            .uri("/api/v1/auth/logout")
            .header("Authorization", "Bearer $jwt")
            .accept(APPLICATION_JSON)
            .exchange()

        // Then
        result.expectStatus().isNoContent
    }

    @Test
    fun `should return 405 Method Not Allowed for GET`() {
        // Given
        val tokenRequest = TokenRequest(
            userID = UUID.randomUUID(),
            email = "test@example.com",
            roles = setOf(RoleType.CUSTOMER),
        )
        val jwt = jwtService.generateAccessToken(tokenRequest)

        // When
        val result = webTestClient.get()
            .uri("/api/v1/auth/logout")
            .header("Authorization", "Bearer $jwt")
            .accept(APPLICATION_PROBLEM_JSON)
            .exchange()

        // Then
        result.expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
            .expectBody()
            .jsonPath("$.rejectedMethod").isEqualTo("GET")
            .jsonPath("$.allowedMethods[0]").isEqualTo("POST")
    }
}
