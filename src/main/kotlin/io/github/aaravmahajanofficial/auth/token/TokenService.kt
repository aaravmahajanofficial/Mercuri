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
import io.github.aaravmahajanofficial.auth.jwt.TokenType
import io.github.aaravmahajanofficial.auth.jwt.TokenValidationError
import io.github.aaravmahajanofficial.common.exception.InvalidTokenException
import io.github.aaravmahajanofficial.config.JwtProperties
import io.github.aaravmahajanofficial.users.UserRepository
import org.springframework.stereotype.Service

@Service
class TokenService(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val jwtProperties: JwtProperties,
) {
    fun refreshToken(request: RefreshTokenRequestDto): RefreshTokenResponseDto {
        val validationResult = jwtService.validateToken(request.refreshToken, TokenType.REFRESH)

        if (!validationResult.isValid) {
            throw InvalidTokenException("Invalid refresh token", validationResult.error)
        }

        // Verify if the user actually exists
        val user = userRepository.findByEmail(validationResult.email!!)

        if (user == null || !user.emailVerified) {
            throw InvalidTokenException(
                if (user == null) "User not found" else "Email not verified",
                TokenValidationError.MISSING_CLAIMS,
            )
        }

        val currentUserRoles = user.roles.map { it.name }.toSet()
        val newAccessToken = jwtService.refreshAccessToken(request.refreshToken, currentUserRoles)

        return RefreshTokenResponseDto(
            accessToken = newAccessToken,
            expiresIn = jwtProperties.accessTokenExpiration / 1000,
        )
    }
}
