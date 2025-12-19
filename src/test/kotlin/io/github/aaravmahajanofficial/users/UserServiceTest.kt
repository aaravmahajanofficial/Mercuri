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

import io.github.aaravmahajanofficial.common.exception.model.ResourceNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UserServiceTest {

    @Mock
    lateinit var userRepository: UserRepository

    @InjectMocks
    lateinit var userService: UserService

    @Test
    fun `should return user profile data successfully`() {
        // Given
        val userId = UUID.randomUUID()
        val user = User(
            email = "john.doe@example.com",
            passwordHash = "hashed_password",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
            emailVerified = true,
            phoneVerified = true,
            status = UserStatus.ACTIVE,
            createdAt = Instant.now(),
            lastLoginAt = Instant.now(),
        ).apply {
            id = userId
            addRole(Role(RoleType.CUSTOMER))
            addRole(Role(RoleType.SELLER))
        }

        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

        // When
        val result = userService.getUserProfile(userId)

        // Then
        result.id shouldBe user.id
        result.fullName shouldBe user.fullName()
        result.email shouldBe user.email
        result.phoneNumber shouldBe user.phoneNumber
        result.emailVerified shouldBe user.emailVerified
        result.phoneVerified shouldBe user.phoneVerified
        result.status shouldBe user.status
        result.createdAt.shouldNotBeNull()
        result.lastLoginAt.shouldNotBeNull()
        result.roles shouldContainExactlyInAnyOrder user.roles.map { it.name }
    }

    @Test
    fun `should throw exception when user does not exist in the database`() {
        // Given
        val userId = UUID.randomUUID()
        whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

        // When
        val exception = shouldThrow<ResourceNotFoundException> { userService.getUserProfile(userId) }

        // Then
        exception.message shouldBe "User not found with id: '$userId'"
    }
}
