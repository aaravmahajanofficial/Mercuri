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

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.auth.register.RegisterResponseDto
import io.github.aaravmahajanofficial.users.RoleType
import org.hamcrest.CoreMatchers.hasItem
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@WebMvcTest(AuthController::class)
class AuthControllerTest @Autowired constructor(val mockMvc: MockMvc, val objectMapper: ObjectMapper) {
    @MockitoBean
    lateinit var authService: AuthService

    @Test
    fun `should return 201 Created with user details`() {
        // Given
        val request = RegisterRequestDto(
            email = "john.doe@example.com",
            username = "john_doe_123",
            password = "SecureP@ss123",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
        )

        val serviceResponse = RegisterResponseDto(
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
            jsonPath("$.data.roles") { value(hasItem(RoleType.CUSTOMER.value)) }
            jsonPath("$.meta.timeStamp") { exists() }
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
}
