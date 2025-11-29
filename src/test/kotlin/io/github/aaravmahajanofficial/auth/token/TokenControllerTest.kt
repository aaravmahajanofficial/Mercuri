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
package io.github.aaravmahajanofficial.auth.token

import io.github.aaravmahajanofficial.ProblemResponseAssertions
import io.github.aaravmahajanofficial.auth.jwt.JwtService
import io.github.aaravmahajanofficial.auth.jwt.TokenValidationError
import io.github.aaravmahajanofficial.common.exception.InvalidTokenException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

@WebMvcTest(TokenController::class)
@AutoConfigureMockMvc(addFilters = false)
class TokenControllerTest : ProblemResponseAssertions() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var tokenService: TokenService

    private fun createRefreshTokenRequest(refreshToken: String) = RefreshTokenRequestDto(refreshToken)

    @Nested
    @DisplayName("POST /api/v1/auth/token/refresh")
    inner class RefreshToken {

        @Test
        fun `should return 200 OK with new access token`() {
            // Given
            val request = createRefreshTokenRequest("new.refresh.token")
            val serviceResponse = RefreshTokenResponseDto("new.access.token", "Bearer", 900)

            whenever(tokenService.refreshToken(any())).thenReturn(serviceResponse)

            // When
            val result = mockMvc.post("/api/v1/auth/token/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = MediaType.APPLICATION_JSON
            }

            // Then
            result.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.data.accessToken") { value(serviceResponse.accessToken) }
                jsonPath("$.data.tokenType") { value(serviceResponse.tokenType) }
                jsonPath("$.data.expiresIn") { value(serviceResponse.expiresIn) }

                jsonPath("$.meta.timestamp") { exists() }
            }
        }

        @Test
        fun `should return 401 Unauthorized when refresh token is expired`() {
            // Given
            val request = createRefreshTokenRequest("expired.refresh.token")

            whenever(tokenService.refreshToken(any())).thenThrow(
                InvalidTokenException(
                    "Token expired",
                    TokenValidationError.EXPIRED,
                ),
            )

            // When
            val result = mockMvc.post("/api/v1/auth/token/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = MediaType.APPLICATION_JSON
            }

            // Then
            assertInvalidToken(result, "/api/v1/auth/token/refresh")
        }

        @Test
        fun `should return 401 Unauthorized when refresh token is invalid`() {
            // Given
            val request = createRefreshTokenRequest("invalid.token")

            whenever(tokenService.refreshToken(any())).thenThrow(
                InvalidTokenException(
                    "Invalid token",
                    TokenValidationError.INVALID_SIGNATURE,
                ),
            )

            // When
            val result = mockMvc.post("/api/v1/auth/token/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = MediaType.APPLICATION_JSON
            }

            // Then
            assertInvalidToken(result, "/api/v1/auth/token/refresh")
        }

        @Test
        fun `should return 422 Unprocessable Content when refresh token is blank`() {
            // Given
            val request = createRefreshTokenRequest("")

            // When
            val result = mockMvc.post("/api/v1/auth/token/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = MediaType.APPLICATION_JSON
            }

            // Then
            assertUnprocessableContent(result, "/api/v1/auth/token/refresh")
        }
    }
}
