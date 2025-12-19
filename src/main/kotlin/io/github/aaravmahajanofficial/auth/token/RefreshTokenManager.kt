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
package io.github.aaravmahajanofficial.auth.token

import io.github.aaravmahajanofficial.auth.jwt.JwtService
import io.github.aaravmahajanofficial.auth.jwt.TokenRequest
import io.github.aaravmahajanofficial.common.exception.model.InvalidTokenException
import io.github.aaravmahajanofficial.config.JwtProperties
import io.github.aaravmahajanofficial.users.User
import jakarta.transaction.Transactional
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class RefreshTokenManager(
    private val hashService: RefreshTokenHashService,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val redisTemplate: StringRedisTemplate,
) {
    private companion object {
        const val REDIS_PREFIX = "jwt:rt:"
    }

    @Transactional
    fun createRefreshToken(user: User, deviceInfo: String? = null, ipAddress: String? = null): String {
        val jti = UUID.randomUUID()

        // Generate JWT
        val tokenRequest = TokenRequest(requireNotNull(user.id), user.email, user.roles.map { it.name }.toSet())
        val refreshToken = jwtService.generateRefreshToken(tokenRequest, jti)

        // Create entity and Persist
        val refreshTokenEntity = RefreshToken(
            jti = jti,
            user = user,
            tokenHash = hashService.hash(refreshToken),
            expiresAt = Instant.now().plusMillis(jwtProperties.refreshTokenExpiration),
            deviceInfo = deviceInfo,
            ipAddress = ipAddress,
        )

        refreshTokenRepository.saveAndFlush(refreshTokenEntity)

        // Cache
        val redisKey = getRedisKey(jti)
        redisTemplate.opsForValue()
            .set(redisKey, user.id.toString(), Duration.ofMillis(jwtProperties.refreshTokenExpiration))

        return refreshToken
    }

    @Transactional
    fun rotateRefreshToken(oldTokenJti: UUID, user: User): String {
        // Revoke old token
        redisTemplate.delete(getRedisKey(oldTokenJti))

        // Mark revoked in DB
        revoke(oldTokenJti)

        // Issue new token
        return createRefreshToken(user)
    }

    fun isTokenValid(jti: UUID): Boolean {
        val redisKey = getRedisKey(jti)

        // FAST PATH: Redis hit
        if (redisTemplate.hasKey(redisKey)) {
            return true
        }

        // FALLBACK: Check DB
        val refreshToken = refreshTokenRepository.findById(jti).orElse(null) ?: return false

        // Hard checks: Revoked or expired
        if (refreshToken.revoked || refreshToken.expiresAt.isBefore(Instant.now())) {
            return false
        }

        // SELF-HEAL: Rehydrate Redis
        redisTemplate.opsForValue().set(
            redisKey,
            refreshToken.user.id.toString(),
            Duration.between(Instant.now(), refreshToken.expiresAt),
        )

        return true
    }

    @Transactional
    fun revokeRefreshToken(oldTokenJti: UUID) {
        // Remove from Redis
        redisTemplate.delete(getRedisKey(oldTokenJti))

        // Mark revoked in DB
        revoke(oldTokenJti)
    }

    @Transactional
    fun revokeAllUserTokens(user: User) {
        val activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedFalse(requireNotNull(user.id))
        if (activeTokens.isEmpty()) return

        // Remove all from Redis
        val redisKeys = activeTokens.map { getRedisKey(it.jti) }
        redisTemplate.delete(redisKeys)

        activeTokens.forEach { it.revoked = true }

        // Mark all as revoked in DB
        refreshTokenRepository.saveAllAndFlush(activeTokens)
    }

    fun revoke(oldTokenJti: UUID) {
        val token = refreshTokenRepository.findById(oldTokenJti).orElseThrow {
            InvalidTokenException("Refresh token not found in DB")
        }
        token.revoked = true
        refreshTokenRepository.saveAndFlush(token)
    }

    private fun getRedisKey(jti: UUID?): String = "$REDIS_PREFIX$jti"
}
