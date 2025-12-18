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

import io.github.aaravmahajanofficial.auth.events.UserLoginEvent
import io.github.aaravmahajanofficial.auth.events.UserRegisterEvent
import io.github.aaravmahajanofficial.auth.jwt.JwtService
import io.github.aaravmahajanofficial.auth.jwt.TokenRequest
import io.github.aaravmahajanofficial.auth.jwt.TokenType
import io.github.aaravmahajanofficial.auth.jwt.TokenValidationError
import io.github.aaravmahajanofficial.auth.login.LoginRequestDto
import io.github.aaravmahajanofficial.auth.login.LoginResponseDto
import io.github.aaravmahajanofficial.auth.mappers.toRegisterResponse
import io.github.aaravmahajanofficial.auth.mappers.toUser
import io.github.aaravmahajanofficial.auth.mappers.toUserDto
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.auth.register.RegisterResponseDto
import io.github.aaravmahajanofficial.auth.token.RefreshTokenManager
import io.github.aaravmahajanofficial.auth.token.RefreshTokenRequestDto
import io.github.aaravmahajanofficial.auth.token.RefreshTokenResponseDto
import io.github.aaravmahajanofficial.auth.token.TokenBlacklistService
import io.github.aaravmahajanofficial.common.exception.AccountSuspendedException
import io.github.aaravmahajanofficial.common.exception.DefaultRoleNotFoundException
import io.github.aaravmahajanofficial.common.exception.EmailNotVerifiedException
import io.github.aaravmahajanofficial.common.exception.InvalidTokenException
import io.github.aaravmahajanofficial.common.exception.UserAlreadyExistsException
import io.github.aaravmahajanofficial.config.JwtProperties
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import io.github.aaravmahajanofficial.users.UserStatus
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Suppress("TooGenericExceptionCaught")
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val roleRepository: RoleRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
    private val refreshTokenManager: RefreshTokenManager,
    private val tokenBlacklistService: TokenBlacklistService,
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    @Transactional
    fun register(requestBody: RegisterRequestDto): RegisterResponseDto {
        if (userRepository.findByEmail(requestBody.email) != null) {
            throw UserAlreadyExistsException()
        }

        val role = roleRepository.findByName(RoleType.CUSTOMER)
            ?: throw DefaultRoleNotFoundException()

        val hashedPassword = hashPassword(requestBody)
        val user = requestBody.toUser(hashedPassword).apply { addRole(role) }

        // Hibernate delays SQL execution until flush/commit.
        // Fields populated by the database (e.g., timestamps, generated IDs)
        // are not available until the INSERT actually runs.
        // `saveAndFlush()` forces the INSERT immediately so these fields are
        // guaranteed to be non-null within this method.
        val savedUser = userRepository.saveAndFlush(user)

        applicationEventPublisher.publishEvent(UserRegisterEvent(savedUser))

        return savedUser.toRegisterResponse()
    }

    @Transactional
    fun login(requestBody: LoginRequestDto): LoginResponseDto {
        val rawUser = findUserByEmailOrThrow(requestBody)

        validatePasswordOrThrow(requestBody, rawUser)
        validateUserStatusOrThrow(rawUser)

        val managedUser = updateLoginTimestamps(rawUser)

        applicationEventPublisher.publishEvent(UserLoginEvent(managedUser))

        // Generate JWT Tokens
        val tokenRequest = buildTokenRequest(managedUser)
        val accessToken = jwtService.generateAccessToken(tokenRequest)
        val refreshToken = refreshTokenManager.createRefreshToken(managedUser)

        return LoginResponseDto(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = "Bearer",
            expiresIn = jwtService.accessTokenExpiration(),
            authStatus = AuthStatus.VERIFIED,
            user = managedUser.toUserDto(),
        )
    }

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

    fun logout(accessToken: String, refreshToken: String?) {
        try {
            val accessClaims = jwtService.extractAllClaims(accessToken)
            val jti = accessClaims.id
            val expiresAt = accessClaims.expiration.toInstant()

            if (jti != null) {
                tokenBlacklistService.blacklistToken(jti, expiresAt)
            }
        } catch (e: Exception) {
            logger.warn("Failed to blacklist access token during logout", e)
        }

        if (!refreshToken.isNullOrBlank()) {
            try {
                val refreshClaims = jwtService.extractAllClaims(refreshToken)
                val refreshTokenJti = UUID.fromString(refreshClaims.id)
                refreshTokenManager.revokeRefreshToken(refreshTokenJti)
            } catch (e: Exception) {
                logger.warn("Failed to revoke refresh token during logout", e)
            }
        }
    }

    @Transactional
    fun logoutAll(userId: UUID) {
        val user = userRepository.findByIdOrNull(userId) ?: throw NoSuchElementException("User not found")

        // Block all Access Tokens issued before now
        tokenBlacklistService.revokeAllUserTokens(user.id.toString())

        // Remove all Refresh Tokens
        refreshTokenManager.revokeAllUserTokens(user)
    }

    private fun findUserByEmailOrThrow(requestBody: LoginRequestDto): User = (
        userRepository.findByEmail(requestBody.email)
            ?: throw BadCredentialsException("Invalid credentials")
        )

    private fun updateLoginTimestamps(user: User): User {
        user.lastLoginAt = Instant.now()
        return userRepository.save(user)
    }

    private fun validatePasswordOrThrow(requestBody: LoginRequestDto, user: User) {
        if (!passwordEncoder.matches(requestBody.password, user.passwordHash)) {
            throw BadCredentialsException("Invalid credentials")
        }
    }

    private fun hashPassword(requestBody: RegisterRequestDto): String =
        requireNotNull(passwordEncoder.encode(requestBody.password)) { ("Password encoder returned null hash") }

    private fun validateUserStatusOrThrow(user: User) {
        if (user.status == UserStatus.SUSPENDED) {
            throw AccountSuspendedException()
        }

        if (!user.emailVerified) {
            throw EmailNotVerifiedException()
        }
    }

    private fun buildTokenRequest(updatedUser: User): TokenRequest = TokenRequest(
        userID = requireNotNull(updatedUser.id) { "User ID must be set after persistence" },
        email = updatedUser.email,
        roles = updatedUser.roles.map { it.name }.toSet(),
    )
}
