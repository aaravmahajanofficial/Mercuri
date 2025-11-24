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

import io.github.aaravmahajanofficial.ProblemResponseAssertions
import io.github.aaravmahajanofficial.auth.login.LoginRequestDto
import io.github.aaravmahajanofficial.auth.login.LoginResponseDto
import io.github.aaravmahajanofficial.auth.login.UserDto
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.auth.register.RegisterResponseDto
import io.github.aaravmahajanofficial.common.exception.AuthenticationFailedException
import io.github.aaravmahajanofficial.common.exception.UserAlreadyExistsException
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.UserStatus
import org.hamcrest.CoreMatchers.hasItem
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.http.MediaType.APPLICATION_XML
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@WebMvcTest(AuthController::class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest @Autowired constructor(val mockMvc: MockMvc, val objectMapper: ObjectMapper) :
    ProblemResponseAssertions() {

    @MockitoBean
    lateinit var authService: AuthService

    companion object {
        fun createValidRegisterRequest(email: String = "john.doe@example.com") =
            RegisterRequestDto(email, "SecureP@ss123", "John", "Doe", "+1234567890")

        fun createMockUser(id: UUID = UUID.randomUUID(), email: String = "john.doe@example.com") = UserDto(
            id, email, "John", "Doe", "+1234567890", true, true, UserStatus.ACTIVE, Instant.now(),
            Instant.now(), listOf(RoleType.CUSTOMER),
        )
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    inner class Registration {

        @Test
        fun `should return 201 Created with user details`() {
            // Given
            val request = createValidRegisterRequest()

            val serviceResponse = RegisterResponseDto(
                id = UUID.randomUUID(),
                email = request.email,
                phoneNumber = request.phoneNumber,
                status = UserStatus.ACTIVE,
                emailVerified = false,
                createdAt = Instant.now(),
                roles = listOf(RoleType.CUSTOMER),
            )

            whenever(authService.register(any())).thenReturn(serviceResponse)

            // When & Then
            mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_JSON
            }.andExpect {
                status { isCreated() }
                content { contentTypeCompatibleWith(APPLICATION_JSON) }

                jsonPath("$.data.id") { value(serviceResponse.id.toString()) }
                jsonPath("$.data.email") { value(serviceResponse.email) }
                jsonPath("$.data.phoneNumber") { value(serviceResponse.phoneNumber) }
                jsonPath("$.data.status") { value(serviceResponse.status.value) }
                jsonPath("$.data.emailVerified") { value(serviceResponse.emailVerified) }
                jsonPath("$.data.roles") { value(hasItem(RoleType.CUSTOMER.value)) }
                jsonPath("$.data.createdAt") { exists() }

                jsonPath("$.meta.timestamp") { exists() }
            }

            // to verify if the input sent to the service method is correct
            verify(authService).register(
                check { capturedDto ->
                    assertEquals(request.email, capturedDto.email)
                    assertEquals(request.password, capturedDto.password)
                    assertEquals(request.firstName, capturedDto.firstName)
                    assertEquals(request.lastName, capturedDto.lastName)
                    assertEquals(request.phoneNumber, capturedDto.phoneNumber)
                },
            )
        }

        @Test
        fun `should return 400 Bad Request for malformed JSON`() {
            // Given
            val request = "{ invalid"

            // When
            val result = mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_JSON
                content = request
                accept = APPLICATION_PROBLEM_JSON
            }

            // Then
            assertBadRequest(result, "/api/v1/auth/register")

            verify(authService, never()).register(any())
        }

        @Test
        fun `should return 405 Method Not Allowed`() {
            // Given

            // When
            val result = mockMvc.get("/api/v1/auth/register") {
                accept = APPLICATION_PROBLEM_JSON
            }

            // Then
            assertMethodNotAllowed(result, "/api/v1/auth/register")

            result.andExpect {
                jsonPath("$.rejectedMethod") { value("GET") }
                jsonPath("$.allowedMethods[0]") { value("POST") }
            }

            verify(authService, never()).register(any())
        }

        @Test
        fun `should return 415 Unsupported Media Type for non-JSON request`() {
            // Given
            val request = createValidRegisterRequest()

            // When
            val result = mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_XML
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            // Then
            assertUnsupportedMediaType(result, "/api/v1/auth/register")

            verify(authService, never()).register(any())
        }

        @Test
        fun `should return 422 Unprocessable Content for all validation failures`() {
            // Given
            val request = RegisterRequestDto(
                email = "",
                password = "bad",
                firstName = "",
                lastName = "",
                phoneNumber = "bad-phone",
            )

            // When
            val result = mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            // Then
            assertUnprocessableContent(result, "/api/v1/auth/register")

            result.andExpect {
                jsonPath("$.validationErrors") { isArray() }
                jsonPath("$.validationErrors[?(@.field=='email')]") { exists() }
                jsonPath("$.validationErrors[?(@.field=='password')]") { exists() }
                jsonPath("$.validationErrors[?(@.field=='firstName')]") { exists() }
                jsonPath("$.validationErrors[?(@.field=='lastName')]") { exists() }
                jsonPath("$.validationErrors[?(@.field=='phoneNumber')]") { exists() }
            }

            verify(authService, never()).register(any())
        }

        @Test
        fun `should return 409 Conflict when email already exists`() {
            // Given
            val request = createValidRegisterRequest()
            whenever(authService.register(any())).thenThrow(UserAlreadyExistsException())

            // When
            val result = mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            // Then
            assertConflict(
                result = result,
                title = "User Already Exists",
                detail = "That email address is taken. Try another",
                instance = "/api/v1/auth/register",
            )

            verify(authService, times(1)).register(any())
        }

        @Test
        fun `should return 500 Server Error when unexpected failure occurs`() {
            // Given
            val request = createValidRegisterRequest()

            whenever(authService.register(any())).thenThrow(RuntimeException())

            // When
            val result = mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            // Then
            assertInternalServerError(
                result = result,
                title = "Internal Server Error",
                instance = "/api/v1/auth/register",
            )

            verify(authService, times(1)).register(any())
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    inner class Login {

        @Test
        fun `should return 200 OK when login with valid email`() {
            // Given
            val request = LoginRequestDto(
                email = "john.doe@example.com",
                password = "StrongP@ss1",
            )

            val mockUser = createMockUser(email = request.email!!)

            val serviceResponse = LoginResponseDto(
                authStatus = AuthStatus.VERIFIED,
                accessToken = "mock.jwt.token",
                expiresIn = 3600,
                user = mockUser,
            )

            whenever(authService.login(any())).thenReturn(serviceResponse)

            // When
            val result = mockMvc.post("/api/v1/auth/login") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_JSON
            }

            // Then
            result.andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(APPLICATION_JSON) }

                jsonPath("$.data.authStatus") { value(serviceResponse.authStatus.value) }
                jsonPath("$.data.accessToken") { value("mock.jwt.token") }
                jsonPath("$.data.tokenType") { value("Bearer") }
                jsonPath("$.data.expiresIn") { value(3600) }

                // Verify the nested fields within the 'user' object
                jsonPath("$.data.user.id") { value(serviceResponse.user.id.toString()) }
                jsonPath("$.data.user.email") { value(serviceResponse.user.email) }
                jsonPath("$.data.user.firstName") { value(serviceResponse.user.firstName) }
                jsonPath("$.data.user.lastName") { value(serviceResponse.user.lastName) }
                jsonPath("$.data.user.emailVerified") { value(serviceResponse.user.emailVerified) }
                jsonPath("$.data.user.phoneVerified") { value(serviceResponse.user.phoneVerified) }
                jsonPath("$.data.user.status") { value(serviceResponse.user.status.value) }
                jsonPath("$.data.user.createdAt") { isNotEmpty() }
                jsonPath("$.data.user.lastLoginAt") { isNotEmpty() }
                jsonPath("$.data.user.roles") { value(hasItem(RoleType.CUSTOMER.value)) }
            }

            verify(authService, times(1)).login(
                check { capturedDto ->
                    assertEquals(request.email, capturedDto.email)
                },
            )
        }

        @Test
        fun `should return 401 Unauthorized when bad credentials`() {
            // Given
            val request = LoginRequestDto(
                email = "john.doe@example.com",
                password = "wrong-password",
            )

            whenever(authService.login(any())).thenThrow(AuthenticationFailedException())

            // When
            val result = mockMvc.post("/api/v1/auth/login") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            // Then
            assertUnauthorized(
                result = result,
                detail = "Invalid email or password. Please check your credentials and try again.",
                instance = "/api/v1/auth/login",
            )

            verify(authService, times(1)).login(any())
        }

        @Test
        fun `should return 400 Bad Request for malformed JSON`() {
            // Given
            val request = " { invalid"

            // When
            val result = mockMvc.post("/api/v1/auth/login") {
                contentType = APPLICATION_JSON
                content = request
                accept = APPLICATION_PROBLEM_JSON
            }

            // Then
            assertBadRequest(result, "/api/v1/auth/login")

            verify(authService, never()).login(any())
        }

        @Test
        fun `should return 422 Unprocessable Content for all validation failures`() { // Validation failed
            // Given
            val request = LoginRequestDto(
                email = "invalid",
                password = "",
            )

            // When
            val result = mockMvc.post("/api/v1/auth/login") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            // Then
            assertUnprocessableContent(result = result, instance = "/api/v1/auth/login")

            result.andExpect {
                jsonPath("$.validationErrors") { isArray() }
                jsonPath("$.validationErrors[?(@.field=='email')]") { exists() }
                jsonPath("$.validationErrors[?(@.field=='password')]") { exists() }
            }

            verify(authService, never()).login(any())
        }

        @Test
        fun `should return 500 Server Error when unexpected failure occurs`() {
            // Given
            val request = LoginRequestDto(
                email = "john.doe@example.com",
                password = "StrongP@ss1",
            )

            whenever(authService.login(any())).thenThrow(RuntimeException())

            // When
            val result = mockMvc.post("/api/v1/auth/login") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            // Then
            assertInternalServerError(
                result = result,
                title = "Internal Server Error",
                instance = "/api/v1/auth/login",
            )

            verify(authService, times(1)).login(any())
        }
    }
}
