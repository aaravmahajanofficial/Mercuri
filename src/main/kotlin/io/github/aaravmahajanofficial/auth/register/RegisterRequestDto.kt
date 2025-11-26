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
package io.github.aaravmahajanofficial.auth.register

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegisterRequestDto(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    @field:Size(max = 255)
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 128)
    @field:Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&+=])(?=\\S+$).{8,128}$",
        message = "Password must be 8-128 characters long and include at least one uppercase letter, " +
            "one lowercase letter, one digit, and one special character",
    )
    val password: String,

    @field:NotBlank(message = "First name is required")
    @field:Size(max = 50)
    @field:Pattern(regexp = "^[A-Za-z\\s'-]+$", message = "First name contains invalid characters")
    val firstName: String,

    @field:NotBlank(message = "Last name is required")
    @field:Size(max = 50)
    @field:Pattern(regexp = "^[A-Za-z\\s'-]+$", message = "Last name contains invalid characters")
    val lastName: String,

    @field:NotBlank(message = "Phone number is required")
    @field:Size(max = 20)
    @field:Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Phone number must be in valid international format")
    val phoneNumber: String,
)
