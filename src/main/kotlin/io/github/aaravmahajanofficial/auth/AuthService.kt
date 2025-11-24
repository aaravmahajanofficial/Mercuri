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
import io.github.aaravmahajanofficial.auth.login.LoginRequestDto
import io.github.aaravmahajanofficial.auth.login.LoginResponseDto
import io.github.aaravmahajanofficial.auth.mappers.toRegisterResponse
import io.github.aaravmahajanofficial.auth.mappers.toUser
import io.github.aaravmahajanofficial.auth.register.RegisterRequestDto
import io.github.aaravmahajanofficial.auth.register.RegisterResponseDto
import io.github.aaravmahajanofficial.common.exception.DefaultRoleNotFoundException
import io.github.aaravmahajanofficial.common.exception.UserAlreadyExistsException
import io.github.aaravmahajanofficial.users.RoleRepository
import io.github.aaravmahajanofficial.users.RoleType
import io.github.aaravmahajanofficial.users.UserRepository
import jakarta.transaction.Transactional
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

    @Transactional
    fun register(requestBody: RegisterRequestDto): RegisterResponseDto {
        if (userRepository.findByEmail(requestBody.email) != null) {
            throw UserAlreadyExistsException()
        }

        val role = roleRepository.findByName(RoleType.CUSTOMER)
            ?: throw DefaultRoleNotFoundException()

        val hashedPassword = passwordEncoder.encode(requestBody.password)!!

        val user = requestBody.toUser(hashedPassword).also { it.addRole(role) }

        // Hibernate delays SQL execution until flush/commit.
        // Fields populated by the database (e.g., timestamps, generated IDs)
        // are not available until the INSERT actually runs.
        // `saveAndFlush()` forces the INSERT immediately so these fields are
        // guaranteed to be non-null within this method.
        val savedUser = userRepository.saveAndFlush(user)

        applicationEventPublisher.publishEvent(UserRegisteredEvent(savedUser))

        return savedUser.toRegisterResponse()
    }

    fun login(requestBody: LoginRequestDto): LoginResponseDto = TODO()
}
