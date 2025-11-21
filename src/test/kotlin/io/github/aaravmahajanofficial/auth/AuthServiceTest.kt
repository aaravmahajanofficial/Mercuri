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

import io.github.aaravmahajanofficial.auth.register.RequestDto
import io.github.aaravmahajanofficial.common.exception.DefaultRoleNotFoundException
import io.github.aaravmahajanofficial.common.exception.UserAlreadyExistsException
import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
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

    @Captor
    lateinit var userCaptor: ArgumentCaptor<User>

    @InjectMocks
    lateinit var authService: AuthService

    private lateinit var requestDto: RequestDto
    private lateinit var customerRole: Role
    private lateinit var customer: User

    @BeforeEach
    fun setUp() {
        requestDto = RequestDto(
            email = "john.doe@example.com",
            username = "john_doe_123",
            password = "SecureP@ss123",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
        )

        customer = User(
            email = "john.doe@example.com",
            username = "john_doe",
            passwordHash = "hashed_password",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
        )

        customerRole = Role(
            name = RoleType.CUSTOMER,
        )
    }

    @Test
    fun `should register user successfully`() {
        // Given
        whenever(userRepository.findByEmail(requestDto.email)).thenReturn(null)
        whenever(userRepository.findByUsername(requestDto.username)).thenReturn(null)
        whenever(roleRepository.findByName(RoleType.CUSTOMER)).thenReturn(customerRole)

        val hashedPassword = "hashed_password"
        whenever(passwordEncoder.encode(requestDto.password)).thenReturn(hashedPassword)

        whenever(userRepository.save(any<User>())).thenAnswer { invocation ->
            val user = invocation.arguments[0] as User
            user.id = user.id ?: UUID.randomUUID()
            user.createdAt = user.createdAt ?: Instant.now()
            user.updatedAt = user.updatedAt ?: Instant.now()
            user
        }

        // When
        val result = authService.register(requestDto)

        // Then
        assertNotNull(result)
        assertEquals(requestDto.email, result.email)
        assertEquals(requestDto.username, result.username)
        assertEquals(listOf(RoleType.CUSTOMER), result.roles)

        // verify the saved object
        verify(userRepository).save(userCaptor.capture())
        val savedUser = userCaptor.value

        assertEquals(requestDto.email, savedUser.email)
        assertEquals(requestDto.username, savedUser.username)
        assertEquals(hashedPassword, savedUser.passwordHash)
        assertEquals(requestDto.firstName, savedUser.firstName)
        assertEquals(requestDto.lastName, savedUser.lastName)
        assertEquals(requestDto.phoneNumber, savedUser.phoneNumber)
        assertEquals(false, savedUser.emailVerified)
        assertEquals(false, savedUser.phoneVerified)
        assertNotNull(savedUser.createdAt)
        assertNotNull(savedUser.updatedAt)
        assertEquals(setOf(customerRole), savedUser.roles)

        verify(eventPublisher).publishEvent(any())
    }

    @Test
    fun `should throw UserAlreadyExistsException when email is taken`() {
        // Given
        whenever(userRepository.findByEmail(requestDto.email)).thenReturn(customer)

        // When & Then
        val exception = assertThrows<UserAlreadyExistsException> {
            authService.register(requestDto)
        }

        assertNotNull(exception.message)
        verify(userRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `should throw UserAlreadyExistsException when username is taken`() {
        // Given
        whenever(userRepository.findByUsername(requestDto.username)).thenReturn(customer)

        // When & Then
        val exception = assertThrows<UserAlreadyExistsException> {
            authService.register(requestDto)
        }

        assertNotNull(exception.message)
        verify(userRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `should throw DefaultRoleNotFoundException when 'Customer' role is missing`() {
        // Given
        whenever(userRepository.findByEmail(requestDto.email)).thenReturn(null)
        whenever(userRepository.findByUsername(requestDto.username)).thenReturn(null)
        whenever(roleRepository.findByName(RoleType.CUSTOMER)).thenReturn(null)

        // When & Then
        val exception = assertThrows<DefaultRoleNotFoundException> {
            authService.register(requestDto)
        }

        assertNotNull(exception.message)
        verify(userRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }
}
