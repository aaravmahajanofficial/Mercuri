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
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    val email: String,

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 32)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    val username: String,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128)
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&+=])(?=\\S+$).{8,128}$",
        message = "Password must be 8-128 characters long and include at least one uppercase letter, " +
            "one lowercase letter, one digit, and one special character.",
    )
    val password: String,

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 50)
    @Pattern(regexp = "^[A-Za-z\\s'-]+$", message = "First name contains invalid characters")
    val firstName: String,

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 50)
    @Pattern(regexp = "^[A-Za-z\\\\s'-]+$", message = "Last name contains invalid characters")
    val lastName: String,

    @NotBlank(message = "Phone number is required")
    @Size(max = 20)
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Phone number must be in valid international format")
    val phoneNumber: String,
)
