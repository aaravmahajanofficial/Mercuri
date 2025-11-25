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
import io.github.aaravmahajanofficial.common.exception.AccountSuspendedException
import io.github.aaravmahajanofficial.common.exception.AuthenticationFailedException
import io.github.aaravmahajanofficial.common.exception.DefaultRoleNotFoundException
import io.github.aaravmahajanofficial.common.exception.EmailNotVerifiedException
import io.github.aaravmahajanofficial.common.exception.UserAlreadyExistsException
import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import io.github.aaravmahajanofficial.users.UserStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID

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
            verify(userRepository, times(1))
                .saveAndFlush(
                    check { inputUser ->
                        inputUser.email shouldBe registerRequest.email
                        inputUser.passwordHash shouldBe "hashed_password"
                        inputUser.firstName shouldBe registerRequest.firstName
                        inputUser.lastName shouldBe registerRequest.lastName
                        inputUser.phoneNumber shouldBe registerRequest.phoneNumber
                        inputUser.emailVerified shouldBe false
                        inputUser.phoneVerified shouldBe false
                        inputUser.createdAt.shouldBeNull()
                        inputUser.updatedAt.shouldBeNull()
                        inputUser.roles shouldBe setOf(customerRole)
                    },
                )

            // Then - Verify Return Value (The Mapper Logic), otherwise Controller would receive garbage data.
            // 1. Check fields passed through from Request
            result.email shouldBe registerRequest.email
            result.phoneNumber shouldBe registerRequest.phoneNumber

            // 2. Check fields generated by Logic/DB (The Mocked User)
            result.id shouldBe savedUser.id // ensures controller gets the actual id from DB, not random UUID
            result.status shouldBe savedUser.status
            result.createdAt shouldBe savedUser.createdAt // ensures controller gets actual time persisted in DB
            result.roles shouldBe savedUser.roles.map { it.name }

            verify(passwordEncoder, times(1)).encode(registerRequest.password)
            verify(eventPublisher, times(1)).publishEvent(any())
        }

        @Test
        fun `should throw UserAlreadyExistsException when email is taken`() {
            // Given
            whenever(userRepository.findByEmail(registerRequest.email)).thenReturn(savedUser)

            // When & Then
            shouldThrow<UserAlreadyExistsException> { authService.register(registerRequest) }

            verify(userRepository, times(0)).saveAndFlush(any())
            verify(eventPublisher, times(0)).publishEvent(any())
        }

        @Test
        fun `should throw DefaultRoleNotFoundException when default role is missing`() {
            // Given
            whenever(userRepository.findByEmail(registerRequest.email)).thenReturn(null)
            whenever(roleRepository.findByName(RoleType.CUSTOMER)).thenReturn(null)

            // When & Then
            shouldThrow<DefaultRoleNotFoundException> { authService.register(registerRequest) }

            verify(userRepository, times(0)).saveAndFlush(any())
            verify(eventPublisher, times(0)).publishEvent(any())
        }
    }

    @Nested
    inner class Login {

        @Test
        fun `should login successfully and update the last login time`() {
            // Given
            savedUser.emailVerified = true
            whenever(userRepository.findByEmail(loginRequest.email)).thenReturn(savedUser)
            whenever(passwordEncoder.matches(loginRequest.password, savedUser.passwordHash)).thenReturn(true)
            whenever(userRepository.saveAndFlush(any())).thenReturn(savedUser)

            // When
            val result = authService.login(loginRequest)

            // Then
            result.accessToken.shouldNotBeNull()
            result.user.email shouldBe savedUser.email

            verify(userRepository, times(1)).saveAndFlush(
                check { inputUser ->
                    inputUser.email shouldBe savedUser.email
                    savedUser.lastLoginAt.shouldNotBeNull() // Confirm service actually set the timestamp before saving
                },
            )

            verify(userRepository, times(1)).findByEmail(any())
            verify(passwordEncoder, times(1)).matches(loginRequest.password, savedUser.passwordHash)
            verify(userRepository, times(1)).saveAndFlush(any())
            verify(eventPublisher, times(1)).publishEvent(any())
        }

        @Test
        fun `should throw AuthenticationFailedException when password does not match`() {
            // Given
            whenever(userRepository.findByEmail(loginRequest.email)).thenReturn(savedUser)
            whenever(passwordEncoder.matches(loginRequest.password, savedUser.passwordHash)).thenReturn(false)

            // When
            shouldThrow<AuthenticationFailedException> {
                authService.login(loginRequest)
            }

            // Then
            verify(userRepository, times(1)).findByEmail(loginRequest.email)
            verify(passwordEncoder, times(1)).matches(loginRequest.password, savedUser.passwordHash)
            verify(userRepository, times(0)).saveAndFlush(any())
            verify(eventPublisher, times(0)).publishEvent(any())
        }

        @Test
        fun `should throw AuthenticationFailedException when user not found`() {
            // Given
            whenever(userRepository.findByEmail(loginRequest.email)).thenReturn(null)

            // When
            shouldThrow<AuthenticationFailedException> { authService.login(loginRequest) }

            // Then
            verify(userRepository, times(1)).findByEmail(loginRequest.email)
            verify(userRepository, times(0)).saveAndFlush(any())
            verify(eventPublisher, times(0)).publishEvent(any())
        }

        @Test
        fun `should throw AccountSuspendedException when user account is suspended`() {
            // Given
            savedUser.status = UserStatus.SUSPENDED
            whenever(userRepository.findByEmail(loginRequest.email)).thenReturn(savedUser)
            whenever(passwordEncoder.matches(loginRequest.password, savedUser.passwordHash)).thenReturn(true)

            // When
            shouldThrow<AccountSuspendedException> { authService.login(loginRequest) }

            // Then
            verify(userRepository, times(1)).findByEmail(loginRequest.email)
            verify(passwordEncoder, times(1)).matches(loginRequest.password, savedUser.passwordHash)
            verify(userRepository, times(0)).saveAndFlush(any())
            verify(eventPublisher, times(0)).publishEvent(any())
        }

        @Test
        fun `should throw EmailNotVerifiedException when email is not verified`() {
            // Given
            whenever(userRepository.findByEmail(loginRequest.email)).thenReturn(savedUser)
            whenever(passwordEncoder.matches(loginRequest.password, savedUser.passwordHash)).thenReturn(true)

            // When
            shouldThrow<EmailNotVerifiedException> { authService.login(loginRequest) }

            // Then
            verify(userRepository, times(1)).findByEmail(loginRequest.email)
            verify(passwordEncoder, times(1)).matches(loginRequest.password, savedUser.passwordHash)
            verify(userRepository, times(0)).saveAndFlush(any())
            verify(eventPublisher, times(0)).publishEvent(any())
        }
    }
}
