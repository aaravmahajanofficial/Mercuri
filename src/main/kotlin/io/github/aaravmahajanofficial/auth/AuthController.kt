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

import io.github.aaravmahajanofficial.auth.jwt.JwtAuthenticationPrincipal
import io.github.aaravmahajanofficial.auth.login.LoginRequestDto
import io.github.aaravmahajanofficial.auth.login.LoginResponseDto
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.auth.register.RegisterResponseDto
import io.github.aaravmahajanofficial.auth.token.RefreshTokenRequestDto
import io.github.aaravmahajanofficial.auth.token.RefreshTokenResponseDto
import io.github.aaravmahajanofficial.common.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping(
        "/register",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE, APPLICATION_PROBLEM_JSON_VALUE],
    )
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody requestBody: RegisterRequestDto): ApiResponse<RegisterResponseDto> =
        ApiResponse.Success(authService.register(requestBody))

    @PostMapping(
        "/login",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE, APPLICATION_PROBLEM_JSON_VALUE],
    )
    @ResponseStatus(HttpStatus.OK)
    fun login(@Valid @RequestBody requestBody: LoginRequestDto): ApiResponse<LoginResponseDto> =
        ApiResponse.Success(authService.login(requestBody))

    @PostMapping(
        "/refresh",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE, APPLICATION_PROBLEM_JSON_VALUE],
    )
    @ResponseStatus(HttpStatus.OK)
    fun refreshToken(@Valid @RequestBody requestBody: RefreshTokenRequestDto): ApiResponse<RefreshTokenResponseDto> =
        ApiResponse.Success(authService.refreshAccessToken(requestBody))

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @RequestBody(required = false) request: RefreshTokenRequestDto?,
    ) {
        val accessToken = authHeader.substringAfter("Bearer ")
        authService.logout(accessToken, request?.refreshToken)
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logoutAll(@AuthenticationPrincipal principal: JwtAuthenticationPrincipal) =
        authService.logoutAll(principal.userId)
}
