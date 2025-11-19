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

import io.github.aaravmahajanofficial.users.Role
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@DataJpaTest
class RoleRepositoryTest @Autowired constructor(
    private val testEntityManager: TestEntityManager,
    private val roleRepository: RoleRepository,
) {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine")
    }

    @Test
    fun `should find role by name`() {
        // Given
        val customerRole = Role(name = RoleType.CUSTOMER)

        testEntityManager.persistAndFlush(customerRole)

        // When
        val foundRole = roleRepository.findByName(RoleType.CUSTOMER)

        // Then
        assertNotNull(foundRole)
        assertEquals(RoleType.CUSTOMER, foundRole.name)
    }

    @Test
    fun `should return null for non-existent role name`() {
        // Given
        // When
        val foundRole = roleRepository.findByName(RoleType.ADMIN)

        // Then
        assertNull(foundRole)
    }

    @Test
    fun `should save and retrieve multiple roles`() {
        // Given
        val roles = listOf(
            Role(name = RoleType.CUSTOMER),
            Role(name = RoleType.SELLER),
            Role(name = RoleType.ADMIN),
            Role(name = RoleType.SUPER_ADMIN),
        )

        roles.forEach { testEntityManager.persistAndFlush(it) }

        // When
        val allRoles = roleRepository.findAll()

        // Then
        assertEquals(4, allRoles.size)

        val allTypes = allRoles.map { it.name }

        assertTrue(
            allTypes.containsAll(
                listOf(
                    RoleType.CUSTOMER,
                    RoleType.SELLER,
                    RoleType.ADMIN,
                    RoleType.SUPER_ADMIN,
                ),
            ),
        )
    }
}
