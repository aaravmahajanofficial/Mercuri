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

import io.github.aaravmahajanofficial.auth.events.UserRegisteredEvent
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.auth.register.RegisterResponseDto
import io.github.aaravmahajanofficial.common.exception.DefaultRoleNotFoundException
import io.github.aaravmahajanofficial.common.exception.UserAlreadyExistsException
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.User
import io.github.aaravmahajanofficial.users.UserRepository
import io.github.aaravmahajanofficial.users.UserStatus
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val roleRepository: RoleRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    fun register(requestBody: RegisterRequestDto): RegisterResponseDto {
        if (userRepository.findByEmail(requestBody.email) != null) {
            throw UserAlreadyExistsException("User with provided credentials already exists")
        }
        if (userRepository.findByUsername(requestBody.username) != null) {
            throw UserAlreadyExistsException("User with provided credentials already exists")
        }

        val customerRole = roleRepository.findByName(RoleType.CUSTOMER)
            ?: throw DefaultRoleNotFoundException(
                "Default role ${RoleType.CUSTOMER} not found. System misconfiguration.",
            )

        val hashedPassword = passwordEncoder.encode(requestBody.password)

        val user = User(
            email = requestBody.email,
            username = requestBody.username,
            passwordHash = hashedPassword,
            firstName = requestBody.firstName,
            lastName = requestBody.lastName,
            phoneNumber = requestBody.phoneNumber,
            status = UserStatus.ACTIVE,
        )

        user.addRole(customerRole)

        val savedUser = userRepository.save(user)

        applicationEventPublisher.publishEvent(UserRegisteredEvent(savedUser))

        return RegisterResponseDto(
            id = savedUser.id!!,
            email = savedUser.email,
            username = savedUser.username,
            phoneNumber = savedUser.phoneNumber,
            status = AuthStatus.PENDING_VERIFICATION,
            emailVerified = savedUser.emailVerified,
            createdAt = savedUser.createdAt!!,
            roles = savedUser.roles.map { it.name },
        )
    }
}
