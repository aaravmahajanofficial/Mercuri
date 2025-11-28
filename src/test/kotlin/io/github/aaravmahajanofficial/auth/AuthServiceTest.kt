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

import io.github.aaravmahajanofficial.auth.events.UserLoginEvent
import io.github.aaravmahajanofficial.auth.events.UserRegisterEvent
import io.github.aaravmahajanofficial.auth.login.LoginRequestDto
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.common.exception.AccountSuspendedException
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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    // Mock the injected dependencies
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

    private fun createRegisterRequest() = RegisterRequestDto(
        email = "john.doe@example.com",
        password = "StrongP@ss123!",
        firstName = "John",
        lastName = "Doe",
        phoneNumber = "+1234567890",
    )

    private fun createLoginRequest() = LoginRequestDto(
        email = "john.doe@example.com",
        password = "StrongP@ss123!",
    )

    private fun createCustomerRole() = Role(name = RoleType.CUSTOMER)

    private fun createExistingUser(status: UserStatus = UserStatus.ACTIVE, emailVerified: Boolean = true): User = User(
        email = "john.doe@example.com",
        passwordHash = "hashed_password",
        firstName = "John",
        lastName = "Doe",
        phoneNumber = "+1234567890",
        emailVerified = emailVerified,
        phoneVerified = true,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    ).apply {
        id = UUID.randomUUID()
        addRole(createCustomerRole())
    }

    @Nested
    inner class Registration {
        @Test
        fun `should register user successfully`() {
            // Given
            val request = createRegisterRequest()
            val customerRole = createCustomerRole()

            whenever(userRepository.findByEmail(request.email)).thenReturn(null)
            whenever(roleRepository.findByName(RoleType.CUSTOMER)).thenReturn(customerRole)
            whenever(passwordEncoder.encode(request.password)).thenReturn("hashed_password")

            val savedUser = createExistingUser()
            whenever(userRepository.saveAndFlush(any())).thenReturn(savedUser)

            // When
            val result = authService.register(request)

            // Then - Verify Side Effect (The Input to DB)
            // Verifies that the correct user object was sent to the repository by the service
            // Also, verifies the dto mapping (toUser), verifies the User JPA entity
            verify(userRepository)
                .saveAndFlush(
                    check {
                        it.email shouldBe request.email
                        it.passwordHash shouldBe "hashed_password"
                        it.createdAt.shouldBeNull()
                        it.updatedAt.shouldBeNull()
                        it.roles shouldContain customerRole
                        it.id shouldBe null
                    },
                )

            // Then - Verify Return Value
            val eventCaptor = argumentCaptor<UserRegisterEvent>()
            verify(eventPublisher).publishEvent(eventCaptor.capture())
            eventCaptor.firstValue.user.email shouldBe request.email

            // fails if service returns pre-save user & not updated user from DB
            result.id shouldBe savedUser.id
            result.createdAt shouldBe savedUser.createdAt
            result.roles shouldContain RoleType.CUSTOMER // ensure that Role assignment works
        }

        @Test
        fun `should throw UserAlreadyExistsException when email is taken`() {
            // Given
            val request = createRegisterRequest()

            whenever(userRepository.findByEmail(request.email)).thenReturn(createExistingUser())

            // When
            shouldThrow<UserAlreadyExistsException> { authService.register(request) }

            // Then
            verifyNoInteractions(passwordEncoder)
            verify(userRepository, never()).saveAndFlush(any())
        }

        @Test
        fun `should throw DefaultRoleNotFoundException when default role is missing`() {
            val request = createRegisterRequest()

            whenever(userRepository.findByEmail(request.email)).thenReturn(null)
            whenever(roleRepository.findByName(RoleType.CUSTOMER)).thenReturn(null)

            shouldThrow<DefaultRoleNotFoundException> { authService.register(request) }
        }
    }

    @Nested
    inner class Login {

        @Test
        fun `should login successfully and update the last login time`() {
            // Given
            val request = createLoginRequest()
            val existingUser = createExistingUser()

            whenever(userRepository.findByEmail(request.email)).thenReturn(existingUser)
            whenever(passwordEncoder.matches(request.password, existingUser.passwordHash)).thenReturn(true)

            val updatedUser = existingUser.apply {
                lastLoginAt = Instant.now()
                updatedAt = Instant.now()
            }
            whenever(userRepository.saveAndFlush(any())).thenReturn(updatedUser)

            // When
            val result = authService.login(request)

            // Then - Verify Side Effect (The Input to DB)
            verify(userRepository)
                .saveAndFlush(
                    check {
                        it.lastLoginAt.shouldNotBeNull() // Confirm service actually set timestamp before saving
                        it.updatedAt.shouldNotBeNull() // Confirm service actually set timestamp before saving
                    },
                )

            // Then - Verify Return Value
            val eventCaptor = argumentCaptor<UserLoginEvent>()
            verify(eventPublisher).publishEvent(eventCaptor.capture())
            eventCaptor.firstValue.user.email shouldBe request.email

            result.authStatus shouldBe AuthStatus.VERIFIED
            result.user.lastLoginAt shouldBe updatedUser.lastLoginAt
        }

        @Test
        fun `should throw BadCredentialsException when password mismatch`() {
            val request = createLoginRequest()
            val existingUser = createExistingUser()

            whenever(userRepository.findByEmail(request.email)).thenReturn(existingUser)
            whenever(passwordEncoder.matches(request.password, existingUser.passwordHash)).thenReturn(false)

            shouldThrow<BadCredentialsException> { authService.login(request) }
        }

        @Test
        fun `should throw BadCredentialsException when user not found`() {
            // Given
            val request = createLoginRequest()

            whenever(userRepository.findByEmail(request.email)).thenReturn(null)

            shouldThrow<BadCredentialsException> { authService.login(request) }
        }

        @Test
        fun `should throw AccountSuspendedException when user account is suspended`() {
            val request = createLoginRequest()
            val suspendedUser = createExistingUser(UserStatus.SUSPENDED)

            whenever(userRepository.findByEmail(request.email)).thenReturn(suspendedUser)
            whenever(passwordEncoder.matches(request.password, suspendedUser.passwordHash)).thenReturn(true)

            shouldThrow<AccountSuspendedException> { authService.login(request) }
        }

        @Test
        fun `should throw EmailNotVerifiedException when email is not verified`() {
            val request = createLoginRequest()
            val existingUser = createExistingUser(emailVerified = false)

            whenever(userRepository.findByEmail(request.email)).thenReturn(existingUser)
            whenever(passwordEncoder.matches(request.password, existingUser.passwordHash)).thenReturn(true)

            shouldThrow<EmailNotVerifiedException> { authService.login(request) }
        }
    }
}
