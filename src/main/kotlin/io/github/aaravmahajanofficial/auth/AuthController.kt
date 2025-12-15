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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping(
        "/register",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE, APPLICATION_PROBLEM_JSON_VALUE],
    )
    fun register(
        @Valid @RequestBody requestBody: RegisterRequestDto,
    ): ResponseEntity<ApiResponse<RegisterResponseDto>> {
        val user = authService.register(requestBody)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.Success(user))
    }

    @PostMapping(
        "/login",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE, APPLICATION_PROBLEM_JSON_VALUE],
    )
    fun login(@Valid @RequestBody requestBody: LoginRequestDto): ResponseEntity<ApiResponse<LoginResponseDto>> {
        val user = authService.login(requestBody)
        return ResponseEntity.ok()
            .body(ApiResponse.Success(user))
    }

    @PostMapping(
        "/refresh",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE, APPLICATION_PROBLEM_JSON_VALUE],
    )
    fun refreshToken(
        @Valid @RequestBody requestBody: RefreshTokenRequestDto,
    ): ResponseEntity<ApiResponse<RefreshTokenResponseDto>> {
        val response = authService.refreshAccessToken(requestBody)
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.Success(response))
    }

    @PostMapping("/logout")
    fun logout(@CurrentUser principal: JwtAuthenticationPrincipal): ResponseEntity<Unit> {
        authService.logout(principal)
        return ResponseEntity.noContent().build()
    }
}
