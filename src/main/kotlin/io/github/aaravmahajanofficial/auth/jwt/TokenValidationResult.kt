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
package io.github.aaravmahajanofficial.auth.jwt

import io.github.aaravmahajanofficial.users.RoleType
import java.util.Date
import java.util.UUID

data class TokenValidationResult(
    val isValid: Boolean,
    val jti: UUID? = null,
    val userID: UUID? = null,
    val email: String? = null,
    val roles: Set<RoleType>? = null,
    val issuedAt: Date? = null,
    val error: TokenValidationError? = null,
) {
    companion object {
        fun valid(jti: UUID, userID: UUID, email: String, roles: Set<RoleType>, issuedAt: Date) = TokenValidationResult(
            isValid = true,
            jti = jti,
            userID = userID,
            email = email,
            roles = roles,
            issuedAt = issuedAt,
        )

        fun invalid(error: TokenValidationError) = TokenValidationResult(
            isValid = false,
            error = error,
        )
    }
}
