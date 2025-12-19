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

import io.github.aaravmahajanofficial.auth.token.TokenBlacklistService
import io.github.aaravmahajanofficial.config.JwtProperties
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Instant
import java.util.Date
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TokenBlacklistServiceTest {

    @Mock
    lateinit var valueOperations: ValueOperations<String, String>

    @Mock
    lateinit var redisTemplate: StringRedisTemplate

    @Mock
    lateinit var jwtProperties: JwtProperties

    @InjectMocks
    lateinit var tokenBlacklistService: TokenBlacklistService

    private val jti = UUID.randomUUID().toString()
    private val userId = UUID.randomUUID().toString()
    private val tokenPrefix = "jwt:bl:token:"
    private val userPrefix = "jwt:bl:user:"

    @BeforeEach
    fun setUp() {
        lenient().whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
    }

    @Test
    fun `should return false when token is clean`() {
        // Given
        val issuedAt = Date()

        whenever(redisTemplate.hasKey("$tokenPrefix$jti")).thenReturn(false)
        whenever(valueOperations.get("$userPrefix$userId")).thenReturn(null)

        // When
        val result = tokenBlacklistService.isTokenBlacklisted(jti, userId, issuedAt)

        // Then
        result.shouldBeFalse()
    }

    @Test
    fun `should return true when specific JTI is blacklisted`() {
        // Given
        val issuedAt = Date()
        whenever(redisTemplate.hasKey("$tokenPrefix$jti")).thenReturn(true)

        // When
        val result = tokenBlacklistService.isTokenBlacklisted(jti, userId, issuedAt)

        // Then
        result.shouldBeTrue()
    }

    @Test
    fun `should return true when user revoked all tokens AND token is old`() {
        // Given
        val revokedAt = Instant.now()
        val issuedAt = Date.from(revokedAt.minusSeconds(30))

        whenever(redisTemplate.hasKey("$tokenPrefix$jti")).thenReturn(false)
        whenever(valueOperations.get("$userPrefix$userId")).thenReturn(revokedAt.toEpochMilli().toString())

        // When
        val result = tokenBlacklistService.isTokenBlacklisted(jti, userId, issuedAt)

        // Then
        result.shouldBeTrue()
    }

    @Test
    fun `should return false when user revoked all tokens BUT token is new`() {
        // Given
        val revokedAt = Instant.now()
        val issuedAt = Date.from(revokedAt.plusSeconds(30))

        whenever(redisTemplate.hasKey("$tokenPrefix$jti")).thenReturn(false)
        whenever(valueOperations.get("$userPrefix$userId")).thenReturn(revokedAt.toEpochMilli().toString())

        // When
        val result = tokenBlacklistService.isTokenBlacklisted(jti, userId, issuedAt)

        // Then
        result.shouldBeFalse()
    }

    @Test
    fun `should return false (valid) on exact timestamp match`() {
        // Given
        val revokedAt = Instant.now()
        val issuedAt = Date.from(revokedAt)

        whenever(redisTemplate.hasKey("$tokenPrefix$jti")).thenReturn(false)
        whenever(valueOperations.get("$userPrefix$userId")).thenReturn(revokedAt.toEpochMilli().toString())

        // When
        val result = tokenBlacklistService.isTokenBlacklisted(jti, userId, issuedAt)

        // Then
        result.shouldBeFalse()
    }

    @Test
    fun `should handle malformed timestamp in Redis carefully`() {
        // Given
        val issuedAt = Date()

        whenever(redisTemplate.hasKey("$tokenPrefix$jti")).thenReturn(false)
        whenever(valueOperations.get("$userPrefix$userId")).thenReturn("not-a-timestamp")

        // When
        val result = tokenBlacklistService.isTokenBlacklisted(jti, userId, issuedAt)

        // Then
        result.shouldBeFalse()
    }
}
