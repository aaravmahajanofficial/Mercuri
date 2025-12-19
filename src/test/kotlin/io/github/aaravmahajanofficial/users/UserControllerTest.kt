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

import io.github.aaravmahajanofficial.ProblemResponseAssertions
import io.github.aaravmahajanofficial.auth.jwt.JwtAuthenticationFilter
import io.github.aaravmahajanofficial.auth.jwt.JwtAuthenticationPrincipal
import io.github.aaravmahajanofficial.common.exception.model.ResourceNotFoundException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@WebMvcTest(UserController::class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest : ProblemResponseAssertions() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @MockitoBean
    lateinit var userService: UserService

    @Test
    fun `should return 200 OK and return user profile`() {
        // Given
        val userId = UUID.randomUUID()
        val principal = JwtAuthenticationPrincipal(userId, "john.doe@example.com")
        val auth = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth

        val serviceResponse = createUserProfile(userId)
        whenever(userService.getUserProfile(userId)).thenReturn(serviceResponse)

        // When
        val result = mockMvc.get("/api/v1/users/me")

        // Then
        result.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(APPLICATION_JSON) }

            jsonPath("$.data.id") { value(serviceResponse.id.toString()) }
            jsonPath("$.data.email") { value(serviceResponse.email) }

            jsonPath("$.meta.timestamp") { exists() }
        }

        verify(userService).getUserProfile(userId)
    }

    @Test
    fun `should return 404 Not Found when user does not exist in DB`() {
        // Given
        val userId = UUID.randomUUID()
        val principal = JwtAuthenticationPrincipal(userId, "john.doe@example.com")
        val auth = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth

        whenever(userService.getUserProfile(userId)).thenThrow(ResourceNotFoundException("User", "id", userId))

        // When
        val result = mockMvc.get("/api/v1/users/me")

        // Then
        assertNotFound(result, "User not found with id: '$userId'", "/api/v1/users/me")
    }

    private fun createUserProfile(userId: UUID): UserProfileDto = UserProfileDto(
        id = userId,
        email = "john.doe@example.com",
        fullName = "John Doe",
        phoneNumber = "+1234567890",
        emailVerified = true,
        phoneVerified = true,
        status = UserStatus.ACTIVE,
        createdAt = Instant.now(),
        lastLoginAt = Instant.now(),
        roles = setOf(RoleType.CUSTOMER),
    )
}
