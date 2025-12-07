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
import io.github.aaravmahajanofficial.auth.jwt.TokenType
import io.github.aaravmahajanofficial.auth.jwt.TokenValidationError
import io.github.aaravmahajanofficial.common.exception.AccountSuspendedException
import io.github.aaravmahajanofficial.common.exception.EmailNotVerifiedException
import io.github.aaravmahajanofficial.common.exception.InvalidTokenException
import io.github.aaravmahajanofficial.config.JwtProperties
import io.github.aaravmahajanofficial.users.UserRepository
import io.github.aaravmahajanofficial.users.UserStatus
import org.springframework.stereotype.Service

@Service
class TokenService(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val jwtProperties: JwtProperties,
    private val refreshTokenManager: RefreshTokenManager,
) {
    fun refreshAccessToken(request: RefreshTokenRequestDto): RefreshTokenResponseDto {
        // 1. Basic JWT Validation (Signature + Expiration)
        val validationResult = jwtService.validateToken(request.refreshToken, TokenType.REFRESH)
        if (!validationResult.isValid || validationResult.jti == null) {
            throw InvalidTokenException("Invalid refresh token", validationResult.error)
        }

        val jti = validationResult.jti

        // 2. Check State
        if (!refreshTokenManager.isTokenValid(jti)) {
            throw InvalidTokenException(
                "Refresh token has been revoked or is invalid",
                TokenValidationError.INVALID_SIGNATURE,
            )
        }

        // 3. Verify User
        val email = validationResult.email ?: throw InvalidTokenException(
            "Missing email claim",
            TokenValidationError.MISSING_CLAIMS,
        )

        val user = userRepository.findByEmail(email) ?: throw InvalidTokenException(
            "User not found",
            TokenValidationError.MISSING_CLAIMS,
        )

        when {
            !user.emailVerified -> throw EmailNotVerifiedException()
            user.status == UserStatus.SUSPENDED -> throw AccountSuspendedException()
        }

        // 4. Revoke old refresh token, create new one
        val newRefreshToken = refreshTokenManager.rotateRefreshToken(jti, user)

        // 5. Create new Access Token
        val tokenRequest = TokenRequest(user.id!!, user.email, user.roles.map { it.name }.toSet())
        val newAccessToken = jwtService.generateAccessToken(tokenRequest)

        return RefreshTokenResponseDto(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            expiresIn = jwtProperties.accessTokenExpiration / 1000,
        )
    }
}
