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

import io.github.aaravmahajanofficial.AbstractControllerTest
import io.github.aaravmahajanofficial.auth.register.RequestDto
import io.github.aaravmahajanofficial.auth.register.ResponseDto
import io.github.aaravmahajanofficial.common.exception.ResourceConflictException
import io.github.aaravmahajanofficial.users.RoleType
import org.hamcrest.CoreMatchers.hasItem
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
    AbstractControllerTest() {
    @MockitoBean
    lateinit var authService: AuthService

    companion object {
        fun createValidRegisterRequest(
            email: String = "john.doe@example.com",
            username: String = "john_doe_123",
            password: String = "SecureP@ss123",
            firstName: String = "John",
            lastName: String = "Doe",
            phoneNumber: String = "+1234567890",
        ) = RequestDto(email, username, password, firstName, lastName, phoneNumber)
    }

    @Nested
    inner class Registration {
        @Test
        fun `should return 201 Created with user details`() {
            // Given
            val request = createValidRegisterRequest()

            val serviceResponse = ResponseDto(
                id = UUID.randomUUID(),
                email = request.email,
                username = request.username,
                phoneNumber = request.phoneNumber,
                status = AuthStatus.PENDING_VERIFICATION,
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
                jsonPath("$.data.username") { value(serviceResponse.username) }
                jsonPath("$.data.phoneNumber") { value(serviceResponse.phoneNumber) }
                jsonPath("$.data.status") { value(serviceResponse.status.value) }
                jsonPath("$.data.emailVerified") { value(serviceResponse.emailVerified) }

                jsonPath("$.data.roles") { value(hasItem(RoleType.CUSTOMER.name)) }

                jsonPath("$.meta.timestamp") { exists() }
                jsonPath("$.data.createdAt") { exists() }
            }

            // to verify if the input sent to the service method is correct
            verify(authService).register(
                check { capturedDto ->
                    assertEquals(request.email, capturedDto.email)
                    assertEquals(request.username, capturedDto.username)
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

            // When & Then
            val result = mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_JSON
                content = request
                accept = APPLICATION_PROBLEM_JSON
            }

            assertBadRequest(result, "http://localhost/api/v1/auth/register")

            verify(authService, never()).register(any())
        }

        @Test
        fun `should return 405 Method Not Allowed`() {
            // Given

            // When & Then
            val result = mockMvc.get("/api/v1/auth/register") {
                accept = APPLICATION_PROBLEM_JSON
            }

            assertMethodNotAllowed(result, "http://localhost/api/v1/auth/register")

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

            // When & Then
            val result = mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_XML
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            assertUnsupportedMediaType(result, "http://localhost/api/v1/auth/register")

            verify(authService, never()).register(any())
        }

        @Test
        fun `should return 422 Unprocessable Entity for all validation failures`() {
            // Given
            val request = RequestDto(
                email = "invalid_email",
                username = "",
                password = "invalid",
                firstName = "",
                lastName = "",
                phoneNumber = "abc",
            )

            // When & Then
            mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }.andExpect {
                status { isUnprocessableContent() }
                content { contentType(APPLICATION_PROBLEM_JSON) }

                jsonPath("$.type") { value("https://api.example.com/problems/validation") }
                jsonPath("$.status") { value(422) }
                jsonPath("$.title") { value("Validation Failed") }
                jsonPath("$.detail") { value("One or more fields failed validation.") }
                jsonPath("$.instance") { value("http://localhost/api/v1/auth/register") }

                jsonPath("$.validationErrors") { isArray() }

                jsonPath("$.validationErrors[?(@.field=='email')]") { exists() }
                jsonPath("$.validationErrors[?(@.field=='username')]") { exists() }
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

            whenever(authService.register(any())).thenThrow(ResourceConflictException("Email already in use"))

            // When & Then
            val result = mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            assertConflict(result, "Email already in use", "http://localhost/api/v1/auth/register")

            verify(authService, times(1)).register(any())
        }

        @Test
        fun `should return 409 Conflict when username already exists`() {
            // Given
            val request = createValidRegisterRequest()

            whenever(authService.register(any())).thenThrow(ResourceConflictException("Username already in use"))

            // When & Then
            val result = mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            assertConflict(result, "Username already in use", "http://localhost/api/v1/auth/register")

            verify(authService, times(1)).register(any())
        }

        @Test
        fun `should return 500 Server Error when unexpected failure occurs`() {
            // Given
            val request = createValidRegisterRequest()

            whenever(authService.register(any())).thenThrow(RuntimeException("Unexpected connection failure"))

            // When & Then
            val result = mockMvc.post("/api/v1/auth/register") {
                contentType = APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
                accept = APPLICATION_PROBLEM_JSON
            }

            assertInternalServerError(result, "http://localhost/api/v1/auth/register")

            verify(authService, times(1)).register(any())
        }
    }
}
