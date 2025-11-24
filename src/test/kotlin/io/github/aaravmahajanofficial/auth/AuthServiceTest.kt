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
import org.junit.jupiter.api.assertNull
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
    private lateinit var savedUser: User
    private lateinit var registerRequest: RegisterRequestDto
    private lateinit var loginRequest: LoginRequestDto

    @BeforeEach
    fun setUp() {
        savedUser = User(
            email = "john.doe@example.com",
            passwordHash = "hashed_password",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
            status = UserStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        ).apply {
            id = UUID.randomUUID()
        }

        customerRole = Role(
            name = RoleType.CUSTOMER,
        )

        registerRequest = RegisterRequestDto(
            email = "john.doe@example.com",
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
            whenever(userRepository.saveAndFlush(any())).thenReturn(savedUser)

            // When
            val result = authService.register(registerRequest)

            // Then - Verify Side Effect (The Input to DB)
            // Verify that the correct user object was sent to the repository
            // Also, verify the dto mapping (toUser)
            verify(exactly(1)) {
                userRepository.saveAndFlush(
                    check { inputUser ->
                        assertEquals(registerRequest.email, inputUser.email)
                        assertEquals("hashed_password", inputUser.passwordHash)
                        assertEquals(registerRequest.firstName, inputUser.firstName)
                        assertEquals(registerRequest.lastName, inputUser.lastName)
                        assertEquals(registerRequest.phoneNumber, inputUser.phoneNumber)
                        assertEquals(false, inputUser.emailVerified)
                        assertEquals(false, inputUser.phoneVerified)
                        assertNull(inputUser.createdAt)
                        assertNull(inputUser.updatedAt)
                        assertEquals(setOf(customerRole), inputUser.roles)
                    },
                )
            }

            // Then - Verify Return Value (The Mapper Logic), otherwise Controller would receive garbage data.
            // 1. Check fields passed through from Request
            assertEquals(registerRequest.email, result.email)
            assertEquals(registerRequest.phoneNumber, result.phoneNumber)

            // 2. Check fields generated by Logic/DB (The Mocked User)
            assertEquals(savedUser.id, result.id) // ensures controller gets the actual id from DB, not random UUID
            assertEquals(savedUser.status, result.status)
            assertEquals(savedUser.createdAt, result.createdAt) // ensures controller gets actual time persisted in DB
            assertEquals(savedUser.roles.map { it.name }, result.roles)

            verify(exactly(1)) { passwordEncoder.encode(registerRequest.password) }
            verify(exactly(1)) { eventPublisher.publishEvent(any()) }
        }

        @Test
        fun `should throw UserAlreadyExistsException when email is taken`() {
            // Given
            whenever(userRepository.findByEmail(registerRequest.email)).thenReturn(savedUser)

            // When & Then
            assertThrows<UserAlreadyExistsException> { authService.register(registerRequest) }

            verify(exactly(0)) { userRepository.saveAndFlush(any()) }
            verify(exactly(0)) { eventPublisher.publishEvent(any()) }
        }

        @Test
        fun `should throw DefaultRoleNotFoundException when default role is missing`() {
            // Given
            whenever(userRepository.findByEmail(registerRequest.email)).thenReturn(null)
            whenever(roleRepository.findByName(RoleType.CUSTOMER)).thenReturn(null)

            // When & Then
            assertThrows<DefaultRoleNotFoundException> { authService.register(registerRequest) }

            verify(exactly(0)) { userRepository.saveAndFlush(any()) }
            verify(exactly(0)) { eventPublisher.publishEvent(any()) }
        }
    }

    @Nested
    inner class Login {

        @Test
        fun `should login successfully and update the last login time`() {
            // Given
            whenever(userRepository.findByEmail(loginRequest.email)).thenReturn(savedUser)
            whenever(passwordEncoder.matches(loginRequest.password, savedUser.passwordHash)).thenReturn(true)
            whenever(userRepository.saveAndFlush(any())).thenReturn(savedUser)

            // When
            val result = authService.login(loginRequest)

            // Then
            assertNotNull(result.accessToken)
            assertEquals(savedUser.email, result.user.email)

            verify(exactly(0)) {
                userRepository.saveAndFlush(
                    check { inputUser ->
                        assertEquals(savedUser.email, inputUser.email)
                        // Confirming that the service actually set the timestamp before saving
                        assertNotNull(savedUser.lastLoginAt)
                    },
                )
            }

            verify(exactly(1)) { userRepository.saveAndFlush(any()) }
            verify(exactly(0)) { userRepository.findByEmail(any()) }
            verify(exactly(0)) { eventPublisher.publishEvent(any()) }
        }

        @Test
        fun `should throw AuthenticationFailedException when invalid credentials`() {
            // Given
            whenever(userRepository.findByEmail(loginRequest.email)).thenReturn(savedUser)
            whenever(passwordEncoder.matches(loginRequest.password, savedUser.passwordHash)).thenReturn(null)

            // When
            shouldThrow<AuthenticationFailedException> {
                authService.login(loginRequest)
            }

            // Then
            verify(exactly(1)) { userRepository.findByEmail(loginRequest.email) }
            verify(exactly(0)) { userRepository.saveAndFlush(any()) }
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
            verify(exactly(0)) { userRepository.saveAndFlush(any()) }
            verify(exactly(0)) { eventPublisher.publishEvent(any()) }
        }
    }
}
