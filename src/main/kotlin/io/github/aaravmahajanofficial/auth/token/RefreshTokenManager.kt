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
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(RefreshTokenManager::class.java)

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

        refreshTokenRepository.save(refreshTokenEntity)

        // Cache
        cacheTokenSafely(jti, user.id.toString(), Duration.ofMillis(jwtProperties.refreshTokenExpiration))

        return refreshToken
    }

    @Transactional
    fun rotateRefreshToken(oldTokenJti: UUID, user: User): String {
        val token = refreshTokenRepository.findById(oldTokenJti).orElseThrow {
            InvalidTokenException("Refresh token not found in DB")
        }

        // Revoke old token
        token.revoked = true
        refreshTokenRepository.save(token)

        runCatching { redisTemplate.delete(getRedisKey(oldTokenJti)) }

        // Issue new refresh token
        return createRefreshToken(user)
    }

    fun isTokenValid(jti: UUID): Boolean {
        val redisKey = getRedisKey(jti)

        // FAST PATH: Redis hit
        val redisHit = runCatching { redisTemplate.hasKey(redisKey) }.getOrElse { false }
        if (redisHit == true) return true

        // FALLBACK: Check DB
        val refreshToken = refreshTokenRepository.findById(jti).orElse(null) ?: return false

        // Hard checks: Revoked or expired
        if (refreshToken.revoked || refreshToken.expiresAt.isBefore(Instant.now())) {
            return false
        }

        // SELF-HEAL: Rehydrate Redis
        cacheTokenSafely(jti, refreshToken.user.id.toString(), Duration.between(Instant.now(), refreshToken.expiresAt))

        return true
    }

    @Transactional
    fun revokeRefreshToken(oldTokenJti: UUID) {
        val token = refreshTokenRepository.findById(oldTokenJti).orElseThrow {
            InvalidTokenException("Refresh token not found in DB")
        }

        token.revoked = true
        refreshTokenRepository.save(token)

        runCatching { redisTemplate.delete(getRedisKey(oldTokenJti)) }
        logger.info("Revoked refresh token: $oldTokenJti")
    }

    @Transactional
    fun revokeAllUserTokens(user: User) {
        val activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedFalse(requireNotNull(user.id))
        if (activeTokens.isEmpty()) return

        // Mark all as revoked in DB
        activeTokens.forEach { it.revoked = true }
        refreshTokenRepository.saveAll(activeTokens)

        runCatching {
            val redisKeys = activeTokens.map { getRedisKey(it.jti) }
            redisTemplate.delete(redisKeys)
        }.onFailure {
            logger.error("Failed to clear Redis keys for user ${user.id}", it)
        }

        logger.info("Revoked all tokens for user: ${user.id}")
    }

    private fun cacheTokenSafely(jti: UUID, userId: String, duration: Duration) {
        runCatching {
            redisTemplate.opsForValue().set(getRedisKey(jti), userId, duration)
        }.onFailure {
            logger.warn("Failed to cache refresh token $jti in Redis. System operating in DB-only mode.")
        }
    }

    private fun getRedisKey(jti: UUID?): String = "$REDIS_PREFIX$jti"
}
