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

import io.github.aaravmahajanofficial.TestcontainersConfiguration
import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import kotlin.test.assertEquals

@DataJpaTest
@Import(TestcontainersConfiguration::class)
class AuthRepositoryTest @Autowired constructor(
    private val testEntityManager: TestEntityManager,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
) {
    lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        testUser = User(
            email = "john.doe@example.com",
            username = "john_doe",
            passwordHash = "hashed_password",
            firstName = "John",
            lastName = "Doe",
            phoneNumber = "+1234567890",
        )
    }

    @Test
    fun `should find user by email`() {
        // Given
        testEntityManager.persistAndFlush(testUser)

        // When
        val foundUser = userRepository.findByEmail("john.doe@example.com")

        // Then
        assertNotNull(foundUser)
        assertEquals(testUser.email, foundUser.email)
    }

    @Test
    fun `should return null for non-existent email`() {
        // Given

        // When
        val foundUser = userRepository.findByEmail("john.doe@example.com")

        // Then
        assertNull(foundUser)
    }

    @Test
    fun `should save and retrieve user with roles`() { // test many-to-many mapping between user and role

        // Given
        val customerRole = Role(name = RoleType.CUSTOMER)
        roleRepository.save(customerRole)

        testUser.addRole(customerRole)

        // mark entity for insertion & force Hibernate to execute SQL immediately
        testEntityManager.persistAndFlush(testUser)

        // clear the first-level cache, to read fresh from the DB, otherwise it would return the same in-memory object
        testEntityManager.clear()

        // When
        val foundUser = userRepository.findByIdOrNull(testUser.id!!)

        // Then
        assertEquals(1, foundUser?.roles?.size) // assure only 1 role is persisted
        assertEquals(RoleType.CUSTOMER, foundUser?.roles?.first()?.name) // exactly the one that was persisted
    }
}
