package io.github.aaravmahajanofficial.auth

import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import org.junit.jupiter.api.BeforeEach
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

@Testcontainers
@DataJpaTest
class AuthRepositoryTest @Autowired constructor(
    private val testEntityManager: TestEntityManager,
    private val userRepository: UserRepository,
) {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine")
    }

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
    fun `should find user by username`() {
        // Given
        testEntityManager.persistAndFlush(testUser)

        // When
        val foundUser = userRepository.findByUsername("john_doe")

        // Then
        assertNotNull(foundUser)
        assertEquals(testUser.username, foundUser.username)
    }

    @Test
    fun `should return null for non-existent username`() {
        // Given

        // When
        val foundUser = userRepository.findByUsername("john_doe")

        // Then
        assertNull(foundUser)
    }
}
