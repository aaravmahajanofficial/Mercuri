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

import io.github.aaravmahajanofficial.config.JwtProperties
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.Date

@Service
class TokenBlacklistService(private val redisTemplate: StringRedisTemplate, private val jwtProperties: JwtProperties) {

    private val logger = LoggerFactory.getLogger(TokenBlacklistService::class.java)

    companion object {
        const val BLACKLIST_TOKEN_PREFIX = "jwt:bl:token:"
        const val BLACKLIST_USER_PREFIX = "jwt:bl:user:"
    }

    // When logging out
    fun blacklistToken(jti: String, expiresAt: Instant) {
        val ttl = calculateTtl(expiresAt)
        if (ttl.isNegative || ttl.isZero) return

        redisTemplate.opsForValue().set("$BLACKLIST_TOKEN_PREFIX$jti", "revoked", ttl)
        logger.debug("Token blacklisted: jti={}", jti)
    }

    // Blacklist all the tokens
    fun revokeAllUserTokens(userId: String) {
        val now = Instant.now().toEpochMilli().toString()
        val ttl = Duration.ofSeconds(jwtProperties.refreshTokenExpiration)

        redisTemplate.opsForValue().set("$BLACKLIST_USER_PREFIX$userId", now, ttl)
        logger.info("Revoked all tokens for user: {} at {}", userId, now)
    }

    fun isTokenBlacklisted(jti: String, userId: String, issuedAt: Date): Boolean {
        // Check for specific token
        if (redisTemplate.hasKey("$BLACKLIST_TOKEN_PREFIX$jti")) {
            return true
        }

        // Check User-wide revocation
        val lastRevocationStr = redisTemplate.opsForValue().get("$BLACKLIST_USER_PREFIX$userId")
        val tokenIssuedTime = issuedAt.toInstant()

        if (lastRevocationStr != null) {
            try {
                val lastRevocationTime = Instant.ofEpochMilli(lastRevocationStr.toLong())
                if (tokenIssuedTime.isBefore(lastRevocationTime)) {
                    return true
                }
            } catch (_: NumberFormatException) {
                logger.error(
                    "Corrupted revocation timestamp for user {}: '{}'. Ignoring revocation check.",
                    userId,
                    lastRevocationStr,
                )
                return false
            }
        }

        return false
    }

    private fun calculateTtl(expiresAt: Instant): Duration = Duration.between(Instant.now(), expiresAt)
}
