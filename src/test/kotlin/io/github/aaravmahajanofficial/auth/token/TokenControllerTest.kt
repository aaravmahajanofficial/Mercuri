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
import io.github.aaravmahajanofficial.common.exception.AccountSuspendedException
import io.github.aaravmahajanofficial.common.exception.EmailNotVerifiedException
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

    @Nested
    @DisplayName("POST /api/v1/auth/token/refresh")
    inner class RefreshToken {

        @Test
        fun `should return 200 OK with new access token`() {
            // Given
            val request = createRefreshTokenRequest("new.refresh.token")
            val serviceResponse = RefreshTokenResponseDto("new.access.token", "Bearer", "new.refresh.token", 900)

            whenever(tokenService.refreshAccessToken(any())).thenReturn(serviceResponse)

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
                jsonPath("$.data.refreshToken") { value(serviceResponse.refreshToken) }
                jsonPath("$.data.tokenType") { value(serviceResponse.tokenType) }
                jsonPath("$.data.expiresIn") { value(serviceResponse.expiresIn) }
                jsonPath("$.meta.timestamp") { exists() }
            }
        }

        @Test
        fun `should return 401 Unauthorized when refresh token is invalid or expired`() {
            // Given
            val request = createRefreshTokenRequest("expired.refresh.token")

            whenever(tokenService.refreshAccessToken(any())).thenThrow(
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
        fun `should return 403 Forbidden when user account is suspended`() {
            // Given
            val request = createRefreshTokenRequest("valid.jwt.token")

            whenever(tokenService.refreshAccessToken(any())).thenThrow(AccountSuspendedException())

            // When
            val result = mockMvc.post("/api/v1/auth/token/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = MediaType.APPLICATION_JSON
            }

            // Then
            assertForbidden(
                result = result,
                title = "Account Suspended",
                detail = "Your account is currently suspended. Please contact support.",
                instance = "/api/v1/auth/token/refresh",
            )
        }

        @Test
        fun `should return 403 Forbidden when email is not verified`() {
            // Given
            val request = createRefreshTokenRequest("valid.jwt.token")

            whenever(tokenService.refreshAccessToken(any())).thenThrow(EmailNotVerifiedException())

            // When
            val result = mockMvc.post("/api/v1/auth/token/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = MediaType.APPLICATION_JSON
            }

            // Then
            assertForbidden(
                result = result,
                title = "Email Not Verified",
                detail = "You must verify your email address before logging in.",
                instance = "/api/v1/auth/token/refresh",
            )
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

    private fun createRefreshTokenRequest(refreshToken: String) = RefreshTokenRequestDto(refreshToken)
}
