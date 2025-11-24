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

import io.github.aaravmahajanofficial.auth.login.LoginRequestDto
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.common.exception.AuthenticationFailedException
import io.github.aaravmahajanofficial.common.exception.DefaultRoleNotFoundException
import io.github.aaravmahajanofficial.common.exception.UserAlreadyExistsException
import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import io.github.aaravmahajanofficial.users.UserStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.exactly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var roleRepository: RoleRepository

    @Mock
    lateinit var passwordEncoder: PasswordEncoder

    @Mock
    lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMocks
    lateinit var authService: AuthService

    private lateinit var customerRole: Role
    private lateinit var user: User
    private lateinit var registerRequest: RegisterRequestDto
    private lateinit var loginRequest: LoginRequestDto

    @BeforeEach
    fun setUp() {
        user = User(
            email = "john.doe@example.com",
            username = "john_doe_123",
            passwordHash = "hashed_password",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
            status = UserStatus.ACTIVE,
        ).apply {
            this.id = UUID.randomUUID()
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }

        customerRole = Role(
            name = RoleType.CUSTOMER,
        )

        registerRequest = RegisterRequestDto(
            email = "john.doe@example.com",
            username = "john_doe_123",
            password = "SecureP@ss123",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
        )

        loginRequest = LoginRequestDto(
            email = "john.doe@example.com",
            password = "StrongP@ss123!",
        )
    }

    @Nested
    inner class Registration {
        @Test
        fun `should register user successfully`() {
            // Given
            whenever(userRepository.findByEmail(registerRequest.email)).thenReturn(null)
            whenever(roleRepository.findByName(RoleType.CUSTOMER)).thenReturn(customerRole)
            whenever(passwordEncoder.encode(registerRequest.password)).thenReturn("hashed_password")
            whenever(userRepository.save(any())).thenReturn(user)

            // When
            val result = authService.register(registerRequest)

            // Then
            assertNotNull(result)
            assertEquals(registerRequest.email, result.email)
            assertEquals(registerRequest.username, result.username)

            // verify the saved object
            verify(exactly(1)) {
                userRepository.save(
                    check { dto ->
                        assertEquals(registerRequest.email, dto.email)
                        assertEquals(registerRequest.username, dto.username)
                        assertEquals("hashed_password", dto.passwordHash)
                        assertEquals(registerRequest.firstName, dto.firstName)
                        assertEquals(registerRequest.lastName, dto.lastName)
                        assertEquals(registerRequest.phoneNumber, dto.phoneNumber)
                        assertEquals(false, dto.emailVerified)
                        assertEquals(false, dto.phoneVerified)
                        assertNotNull(dto.createdAt)
                        assertNotNull(dto.updatedAt)
                        assertEquals(setOf(customerRole), dto.roles)
                    },
                )
            }

            verify(exactly(1)) { eventPublisher.publishEvent(any()) }
        }

        @Test
        fun `should throw UserAlreadyExistsException when email is taken`() {
            // Given
            whenever(userRepository.findByEmail(registerRequest.email)).thenReturn(user)

            // When & Then
            assertThrows<UserAlreadyExistsException> { authService.register(registerRequest) }

            verify(exactly(0)) { userRepository.save(any()) }
            verify(exactly(0)) { eventPublisher.publishEvent(any()) }
        }

        @Test
        fun `should throw DefaultRoleNotFoundException when default role is missing`() {
            // Given
            whenever(userRepository.findByEmail(registerRequest.email)).thenReturn(null)
            whenever(roleRepository.findByName(RoleType.CUSTOMER)).thenReturn(null)

            // When & Then
            assertThrows<DefaultRoleNotFoundException> { authService.register(registerRequest) }

            verify(exactly(0)) { userRepository.save(any()) }
            verify(exactly(0)) { eventPublisher.publishEvent(any()) }
        }
    }

    @Nested
    inner class Login {

        @Test
        fun `should login successfully and update the last login time`() {
            // Given
            whenever(userRepository.findByEmail(loginRequest.email)).thenReturn(user)
            whenever(passwordEncoder.matches(loginRequest.password, user.passwordHash)).thenReturn(true)
            whenever(userRepository.save(any())).thenReturn(user)

            // When
            val result = authService.login(loginRequest)

            // Then
            assertNotNull(result)
            assertNotNull(result.accessToken)
            assertEquals(user.email, result.user.email)

            verify(exactly(0)) {
                userRepository.save(
                    check { dto ->
                        assertEquals(user.email, dto.email)
                        assertNotNull(user.lastLoginAt)
                    },
                )
            }

            verify(exactly(0)) { userRepository.findByEmail(any()) }
            verify(exactly(0)) { userRepository.save(any()) }
            verify(exactly(0)) { eventPublisher.publishEvent(any()) }
        }

        @Test
        fun `should throw AuthenticationFailedException when invalid credentials`() {
            // Given
            whenever(userRepository.findByEmail(loginRequest.email)).thenReturn(user)
            whenever(passwordEncoder.matches(loginRequest.password, user.passwordHash)).thenReturn(null)

            // When
            shouldThrow<AuthenticationFailedException> {
                authService.login(loginRequest)
            }

            // Then
            verify(exactly(1)) { userRepository.findByEmail(loginRequest.email) }
            verify(exactly(0)) { userRepository.save(any()) }
            verify(exactly(0)) { eventPublisher.publishEvent(any()) }
        }

        @Test
        fun `should throw AuthenticationFailedException when user not found`() {
            // Given
            whenever(userRepository.findByEmail(loginRequest.email)).thenReturn(null)

            // When
            shouldThrow<AuthenticationFailedException> { authService.login(loginRequest) }

            // Then
            verify(exactly(1)) { userRepository.findByEmail(loginRequest.email) }
            verify(exactly(0)) { userRepository.save(any()) }
            verify(exactly(0)) { eventPublisher.publishEvent(any()) }
        }
    }
}
