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
package io.github.aaravmahajanofficial.config

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "jwt")
@Validated
data class JwtProperties(
    @field:NotBlank(message = "JWT secret key must be configured")
    val secretKey: String,

    @field:Min(value = 60000, message = "Access token expiration must be at least 1 minute (60000ms)")
    val accessTokenExpiration: Long = 900_000L,

    @field:Min(value = 3600000, message = "Refresh token expiration must be at least 1 hour (3600000ms)")
    val refreshTokenExpiration: Long = 604_800_000L,
)
